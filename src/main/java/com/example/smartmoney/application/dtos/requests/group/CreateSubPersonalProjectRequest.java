package com.example.smartmoney.application.dtos.requests.group;

import com.example.smartmoney.domain.models.project.enums.ProjectPriority;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CreateSubPersonalProjectRequest {
    @NotNull(message = "Priority is required")
    private ProjectPriority priority;
}
