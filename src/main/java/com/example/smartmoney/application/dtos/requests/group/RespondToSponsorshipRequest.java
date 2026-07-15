package com.example.smartmoney.application.dtos.requests.group;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class RespondToSponsorshipRequest {
    @NotNull(message = "agreed is required")
    private Boolean agreed;
}
