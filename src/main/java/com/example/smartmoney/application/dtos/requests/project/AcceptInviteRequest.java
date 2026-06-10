package com.example.smartmoney.application.dtos.requests.project;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * Request body for the token-based invite acceptance endpoint.
 * The invitee submits the token they received via push / email,
 * plus an optional custom monthly commitment amount.
 */
@Data
public class AcceptInviteRequest {

    @NotBlank(message = "Invite token is required")
    private String token;

    /** Optional custom monthly commitment amount (VND / project currency). */
    private java.math.BigDecimal commitmentAmount;
}
