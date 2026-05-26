package com.waitlist.ingestion.controller;

import com.waitlist.ingestion.dto.SignupRequest;
import com.waitlist.ingestion.dto.SignupResponse;
import com.waitlist.ingestion.service.ReferralService;
import com.waitlist.ingestion.service.SignupService;
import io.github.bucket4j.Bucket;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/public")
@RequiredArgsConstructor
public class SignupController {

    private final SignupService signupService;
    private final ReferralService referralService;
    private final Bucket bucket;

    @PostMapping("/signup")
    public ResponseEntity<SignupResponse> signup(@Valid @RequestBody SignupRequest req) {
        if (!bucket.tryConsume(1)) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).build();
        }
        return ResponseEntity.ok(signupService.signup(req));
    }

    @GetMapping("/leaderboard")
    public ResponseEntity<?> leaderboard() {
        return ResponseEntity.ok(referralService.getLeaderboard());
    }
}
