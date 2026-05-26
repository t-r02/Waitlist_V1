package com.waitlist.ingestion.repository;

import com.waitlist.ingestion.domain.ReferralPoints;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface ReferralPointsRepository extends JpaRepository<ReferralPoints, Long> {

    Optional<ReferralPoints> findByEmail(String email);

    @Query("SELECT r FROM ReferralPoints r WHERE r.flagged = false ORDER BY r.points DESC")
    List<ReferralPoints> findLeaderboard(Pageable pageable);
}
