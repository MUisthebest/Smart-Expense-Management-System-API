package com.example.smartmoney.application.dtos.requests.suggestion;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class RespondToSuggestionRequest {
    /** true → accept (execute the proposed action); false → dismiss. */
    @NotNull(message = "accept is required")
    private Boolean accept;
}
