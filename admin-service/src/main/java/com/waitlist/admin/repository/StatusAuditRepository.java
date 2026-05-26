package com.waitlist.admin.repository;

import com.waitlist.admin.domain.StatusAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StatusAuditRepository extends JpaRepository<StatusAuditLog, Long> {
}
