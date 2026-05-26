package com.waitlist.admin.repository;

import com.waitlist.admin.domain.WaitlistEntry;
import com.waitlist.admin.domain.Status;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface WaitlistEntryRepository extends JpaRepository<WaitlistEntry, Long> {
    List<WaitlistEntry> findByStatus(Status status);
    Optional<WaitlistEntry> findByEmail(String email);
}
