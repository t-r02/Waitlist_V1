package com.waitlist.ingestion.domain;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Data
@Table(name = "referral_points")
public class ReferralPoints {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String email;

    @Column(nullable = false)
    private int points = 0;

    private String badge;

    public void addPoints(int p) {
        points += p;
        updateBadge();
    }

    private void updateBadge() {
        if (points >= 50) badge = "GOLD";
        else if (points >= 20) badge = "SILVER";
        else if (points >= 5) badge = "BRONZE";
    }
}
