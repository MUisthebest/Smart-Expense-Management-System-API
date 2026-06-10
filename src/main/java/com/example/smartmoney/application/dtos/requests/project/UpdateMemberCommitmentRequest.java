package com.example.smartmoney.application.dtos.requests.project;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class UpdateMemberCommitmentRequest {
    @NotNull(message = "amount is required")
    @DecimalMin(value = "0.01", message = "amount must be > 0")
    private BigDecimal amount;
}
