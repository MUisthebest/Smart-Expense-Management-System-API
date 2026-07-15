package com.example.smartmoney.application.dtos.requests.group;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.math.BigDecimal;

@Data
public class UpdateAutoSponsorshipRequest {
    @NotNull(message = "enabled is required")
    private Boolean enabled;
    private BigDecimal limit;
}
