package com.example.smartmoney.application.dtos.responses;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SignatureResponse {
    private long timestamp;
    private String signature;
    private String apiKey;
    private String cloudName;
}