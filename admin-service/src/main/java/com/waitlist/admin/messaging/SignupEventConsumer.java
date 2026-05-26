package com.waitlist.admin.messaging;

import com.waitlist.admin.domain.WaitlistEntry;
import com.waitlist.admin.repository.WaitlistEntryRepository;
import com.waitlist.events.SignupEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SignupEventConsumer {

    private final WaitlistEntryRepository repository;

    @KafkaListener(topics = "waitlist.signup", groupId = "admin-service")
    public void handleSignup(SignupEvent event) {
        if (repository.findByEmail(event.email()).isPresent()) return;

        var entry = new WaitlistEntry();
        entry.setIngestionId(event.ingestionId());
        entry.setEmail(event.email());
        entry.setName(event.name());
        entry.setCompany(event.company());
        repository.save(entry);
    }
}
