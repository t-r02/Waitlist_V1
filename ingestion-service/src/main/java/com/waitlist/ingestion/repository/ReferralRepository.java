package com.waitlist.ingestion.repository;

import com.waitlist.ingestion.domain.Referral;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ReferralRepository extends JpaRepository<Referral, Long> {
    List<Referral> findByReferrerEmail(String email);
}
