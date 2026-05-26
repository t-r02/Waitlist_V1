package com.waitlist.ingestion.dto;

import lombok.Data;

@Data
public class SignupRequest {
    private String email;
    private String name;
    private String company;
    private String referralCode;
}
