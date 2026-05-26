package com.waitlist.ingestion.service;

import com.waitlist.ingestion.domain.Referral;
import com.waitlist.ingestion.domain.ReferralPoints;
import com.waitlist.ingestion.repository.ReferralPointsRepository;
import com.waitlist.ingestion.repository.ReferralRepository;
import com.waitlist.ingestion.repository.WaitlistEntryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ReferralService {
    private final ReferralRepository referralRepo;
    private final ReferralPointsRepository pointsRepo;
    private final WaitlistEntryRepository entryRepo;
    
    @Transactional
    public void trackReferral(String referralCode, String refereeEmail) {
        entryRepo.findByEmail(refereeEmail).ifPresent(referee -> {
            var referrerOpt = entryRepo.findAll().stream()
                .filter(e -> referralCode.equals(e.getReferralCode()))
                .findFirst();
            
            referrerOpt.ifPresent(referrer -> {
                var ref = new Referral();
                ref.setReferrerEmail(referrer.getEmail());
                ref.setRefereeEmail(refereeEmail);
                referralRepo.save(ref);
                
                awardPoints(referrer.getEmail(), 10);
            });
        });
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
        return pointsRepo.findAll(PageRequest.of(0, 10)).getContent();
    }
}
