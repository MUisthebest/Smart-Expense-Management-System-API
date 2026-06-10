package com.example.smartmoney.application.dtos.requests.project;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class InviteProjectMemberRequest {
    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    private String email;

    private boolean admin;

    /**
     * The monthly amount the invitee will pledge to contribute. Required for
     * GROUP projects; the invitee's safe-spending check is performed against
     * this value at the moment they accept the invite.
     */
    @NotNull(message = "monthlyCommitmentAmount is required")
    @DecimalMin(value = "0.01", message = "monthlyCommitmentAmount must be > 0")
    private BigDecimal monthlyCommitmentAmount;
}
