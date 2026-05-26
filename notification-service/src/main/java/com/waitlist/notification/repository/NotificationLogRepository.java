package com.waitlist.notification.repository;

import com.waitlist.notification.domain.NotificationLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface NotificationLogRepository extends JpaRepository<NotificationLog, Long> {
    boolean existsByEventId(UUID eventId);
}
