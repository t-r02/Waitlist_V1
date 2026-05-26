package com.waitlist.ingestion.service;

import com.waitlist.ingestion.domain.Referral;
import com.waitlist.ingestion.domain.ReferralFingerprint;
import com.waitlist.ingestion.domain.ReferralPoints;
import com.waitlist.ingestion.repository.ReferralFingerprintRepository;
import com.waitlist.ingestion.repository.ReferralPointsRepository;
import com.waitlist.ingestion.repository.ReferralRepository;
import com.waitlist.ingestion.repository.WaitlistEntryRepository;
import com.waitlist.ingestion.web.RateLimitInterceptor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReferralService {

    private static final int MAX_REFERRALS_PER_IP_PER_24H = 5;

    private final ReferralRepository referralRepo;
    private final ReferralPointsRepository pointsRepo;
    private final WaitlistEntryRepository entryRepo;
    private final ReferralFingerprintRepository fingerprintRepo;

    /**
     * REQUIRES_NEW: this transaction is independent of the caller's signup transaction.
     * If a duplicate referee constraint fires, this transaction rolls back and
     * DataIntegrityViolationException propagates to the caller, which catches and ignores it.
     * Self-referrals are rejected before any DB write.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void trackReferral(String referralCode, String refereeEmail) {
        if (entryRepo.findByEmail(refereeEmail).isEmpty()) return;

        var referrerOpt = entryRepo.findByReferralCode(referralCode);
        if (referrerOpt.isEmpty()) return;

        var referrer = referrerOpt.get();

        if (referrer.getEmail().equalsIgnoreCase(refereeEmail)) {
            log.warn("Self-referral rejected [email={}]", refereeEmail);
            return;
        }

        var ref = new Referral();
        ref.setReferrerEmail(referrer.getEmail());
        ref.setRefereeEmail(refereeEmail);
        // saveAndFlush forces the flush inside this REQUIRES_NEW transaction so
        // DataIntegrityViolationException surfaces here, not at commit time
        referralRepo.saveAndFlush(ref);

        // Fingerprint fraud check — runs after a successful referral insert
        String ipHash = currentIpHash();
        updateFingerprint(referrer.getEmail(), ipHash);

        awardPoints(referrer.getEmail(), 10);
    }

    @Transactional
    public void awardPoints(String email, int points) {
        var rp = pointsRepo.findByEmail(email).orElseGet(() -> {
            var newRp = new ReferralPoints();
            newRp.setEmail(email);
            return newRp;
        });
        rp.addPoints(points);
        pointsRepo.save(rp);
    }

    public List<ReferralPoints> getLeaderboard() {
        return pointsRepo.findLeaderboard(PageRequest.of(0, 10));
    }

    // ── Fingerprint helpers ──────────────────────────────────────────────────

    private void updateFingerprint(String referrerEmail, String ipHash) {
        var existing = fingerprintRepo.findByReferrerEmailAndIpHash(referrerEmail, ipHash);

        ReferralFingerprint fp;
        if (existing.isPresent()) {
            fp = existing.get();
            if (fp.getWindowStart().isBefore(OffsetDateTime.now().minusHours(24))) {
                fp.setCount(1);
                fp.setWindowStart(OffsetDateTime.now());
            } else {
                fp.setCount(fp.getCount() + 1);
            }
        } else {
            fp = new ReferralFingerprint();
            fp.setReferrerEmail(referrerEmail);
            fp.setIpHash(ipHash);
            // count and windowStart default to 1 / now() in the entity
        }
        fingerprintRepo.save(fp);

        if (fp.getCount() > MAX_REFERRALS_PER_IP_PER_24H) {
            flagReferrer(referrerEmail, ipHash);
        }
    }

    private void flagReferrer(String referrerEmail, String ipHash) {
        var rp = pointsRepo.findByEmail(referrerEmail).orElseGet(() -> {
            var newRp = new ReferralPoints();
            newRp.setEmail(referrerEmail);
            return newRp;
        });
        if (!rp.isFlagged()) {
            rp.setFlagged(true);
            pointsRepo.save(rp);
            log.warn("Referrer flagged for suspicious activity [referrerEmail={}, ipHash={}]",
                    referrerEmail, ipHash);
        }
    }

    /**
     * Reads the client IP from the current servlet request and returns a
     * truncated SHA-256 hex digest. Returns "unknown" outside a web context
     * (async workers, tests) so the fingerprint still functions but buckets
     * everything under one key.
     */
    static String currentIpHash() {
        try {
            var attrs = (ServletRequestAttributes) RequestContextHolder.currentRequestAttributes();
            String ip = RateLimitInterceptor.extractClientIp(attrs.getRequest());
            return hashIp(ip);
        } catch (IllegalStateException e) {
            return "unknown";
        }
    }

    static String hashIp(String ip) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(ip.getBytes(StandardCharsets.UTF_8));
            var sb = new StringBuilder(16);
            for (int i = 0; i < 8; i++) sb.append(String.format("%02x", digest[i]));
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }
}
