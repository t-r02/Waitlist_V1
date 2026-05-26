package com.waitlist.ingestion.controller;

import com.waitlist.ingestion.dto.SignupRequest;
import com.waitlist.ingestion.dto.SignupResponse;
import com.waitlist.ingestion.service.ReferralService;
import com.waitlist.ingestion.service.SignupService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/public")
@RequiredArgsConstructor
public class SignupController {

    private final SignupService signupService;
    private final ReferralService referralService;

    @PostMapping("/signup")
    public ResponseEntity<SignupResponse> signup(@Valid @RequestBody SignupRequest req) {
        return ResponseEntity.ok(signupService.signup(req));
    }

    @GetMapping("/leaderboard")
    public ResponseEntity<?> leaderboard() {
        return ResponseEntity.ok(referralService.getLeaderboard());
    }
}
