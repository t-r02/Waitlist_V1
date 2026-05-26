package com.waitlist.ingestion.repository;

import com.waitlist.ingestion.domain.ReferralPoints;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface ReferralPointsRepository extends JpaRepository<ReferralPoints, Long> {
    Optional<ReferralPoints> findByEmail(String email);
}
