package com.waitlist.ingestion;

import com.waitlist.ingestion.entity.ReferralFingerprint;
import com.waitlist.ingestion.entity.ReferralPoints;
import com.waitlist.ingestion.entity.WaitlistEntry;
import com.waitlist.ingestion.repository.ReferralFingerprintRepository;
import com.waitlist.ingestion.repository.ReferralPointsRepository;
import com.waitlist.ingestion.repository.ReferralRepository;
import com.waitlist.ingestion.repository.WaitlistEntryRepository;
import com.waitlist.ingestion.service.LeaderboardService;
import com.waitlist.ingestion.service.ReferralService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReferralServiceTest {

    @Mock WaitlistEntryRepository entryRepo;
    @Mock ReferralRepository referralRepo;
    @Mock ReferralPointsRepository pointsRepo;
    @Mock ReferralFingerprintRepository fingerprintRepo;
    @Mock LeaderboardService leaderboardService;

    ReferralService service;

    @BeforeEach
    void setUp() {
        service = new ReferralService(referralRepo, pointsRepo, entryRepo, fingerprintRepo,
                leaderboardService);
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private WaitlistEntry entry(String email, String code) {
        var e = new WaitlistEntry();
        e.setEmail(email);
        e.setReferralCode(code);
        return e;
    }

    private void stubReferrer(WaitlistEntry referrer) {
        when(entryRepo.findByReferralCode(referrer.getReferralCode()))
                .thenReturn(Optional.of(referrer));
    }

    private void stubReferee(WaitlistEntry referee) {
        when(entryRepo.findByEmail(referee.getEmail()))
                .thenReturn(Optional.of(referee));
    }

    // ── self-referral ─────────────────────────────────────────────────────────

    @Test
    void selfReferral_isRejectedAndNothingIsPersisted() {
        var person = entry("alice@example.com", "aliccode");
        stubReferrer(person);
        stubReferee(person);

        service.trackReferral("aliccode", "alice@example.com");

        verify(referralRepo, never()).saveAndFlush(any());
        verify(pointsRepo, never()).save(any());
    }

    @Test
    void selfReferral_caseInsensitive_isRejected() {
        var person = entry("alice@example.com", "aliccode");
        stubReferrer(person);
        // refereeEmail arrives upper-cased — stub must match the exact string passed to findByEmail
        when(entryRepo.findByEmail("ALICE@EXAMPLE.COM")).thenReturn(Optional.of(person));

        service.trackReferral("aliccode", "ALICE@EXAMPLE.COM");

        verify(referralRepo, never()).saveAndFlush(any());
    }

    // ── duplicate referee ─────────────────────────────────────────────────────

    @Test
    void duplicateReferee_throwsDataIntegrityViolation_andPointsAreNotAwarded() {
        var referrer = entry("bob@example.com", "bobscode");
        var referee  = entry("carol@example.com", "xxxxxxxx");
        stubReferrer(referrer);
        stubReferee(referee);

        when(referralRepo.saveAndFlush(any()))
                .thenThrow(new DataIntegrityViolationException("duplicate key value violates unique constraint"));

        // DataIntegrityViolationException propagates — the caller (SignupPersistenceService) catches it
        assertThatThrownBy(() -> service.trackReferral("bobscode", "carol@example.com"))
                .isInstanceOf(DataIntegrityViolationException.class);

        verify(pointsRepo, never()).save(any());
        verify(fingerprintRepo, never()).save(any());
    }

    // ── legitimate referral ───────────────────────────────────────────────────

    @Test
    void legitimateReferral_createsReferralRowAndDoesNotAwardPoints() {
        // Points are awarded by StatusChangedConsumer on APPROVED, not here.
        var referrer = entry("dave@example.com", "davecode");
        var referee  = entry("eve@example.com", "eveccode");
        stubReferrer(referrer);
        stubReferee(referee);

        // No existing fingerprint (count will be 1 — below the flag threshold of 5)
        when(fingerprintRepo.findByReferrerEmailAndIpHash(eq("dave@example.com"), any()))
                .thenReturn(Optional.empty());

        service.trackReferral("davecode", "eve@example.com");

        // Referral row created with correct emails
        verify(referralRepo).saveAndFlush(argThat(r ->
                "dave@example.com".equals(r.getReferrerEmail()) &&
                "eve@example.com".equals(r.getRefereeEmail())));

        // Points repo must NOT be touched — points come later via APPROVED event
        verify(pointsRepo, never()).save(any());
        verify(pointsRepo, never()).findByEmail(any());
    }

    @Test
    void legitimateReferral_fingerprintRowIsCreated() {
        var referrer = entry("dave@example.com", "davecode");
        var referee  = entry("eve@example.com", "eveccode");
        stubReferrer(referrer);
        stubReferee(referee);

        when(fingerprintRepo.findByReferrerEmailAndIpHash(eq("dave@example.com"), any()))
                .thenReturn(Optional.empty());

        service.trackReferral("davecode", "eve@example.com");

        // A new fingerprint row is saved
        verify(fingerprintRepo).save(argThat(fp ->
                "dave@example.com".equals(fp.getReferrerEmail()) && fp.getCount() == 1));
    }

    // ── fingerprint / flagging ────────────────────────────────────────────────

    @Test
    void referrer_flaggedWhenFingerprintExceedsThreshold() {
        var referrer = entry("spammer@example.com", "spamcode");
        var referee  = entry("victim@example.com", "victcode");
        stubReferrer(referrer);
        stubReferee(referee);

        var fp = new ReferralFingerprint();
        fp.setReferrerEmail("spammer@example.com");
        fp.setIpHash("unknown"); // test context — no real request
        fp.setCount(5);         // already at the limit; next referral tips it over
        fp.setWindowStart(java.time.OffsetDateTime.now().minusMinutes(10));

        when(fingerprintRepo.findByReferrerEmailAndIpHash("spammer@example.com", "unknown"))
                .thenReturn(Optional.of(fp));
        when(pointsRepo.findByEmail("spammer@example.com")).thenReturn(Optional.empty());

        service.trackReferral("spamcode", "victim@example.com");

        // Fingerprint count incremented to 6 and saved
        verify(fingerprintRepo).save(argThat(f -> f.getCount() == 6));

        // Referrer flagged
        verify(pointsRepo).save(argThat(rp ->
                "spammer@example.com".equals(rp.getEmail()) && rp.isFlagged()));
    }

    // ── hash helper ───────────────────────────────────────────────────────────

    @Test
    void hashIp_returnsDeterministic16CharHex() {
        String h1 = ReferralService.hashIp("192.168.1.1");
        String h2 = ReferralService.hashIp("192.168.1.1");
        String h3 = ReferralService.hashIp("10.0.0.1");

        assertThat(h1).hasSize(16).matches("[0-9a-f]+");
        assertThat(h1).isEqualTo(h2);
        assertThat(h1).isNotEqualTo(h3);
    }
}
