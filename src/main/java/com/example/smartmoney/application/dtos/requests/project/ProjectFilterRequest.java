package com.example.smartmoney.application.dtos.requests.project;

import com.example.smartmoney.domain.models.project.enums.ProjectPriority;
import com.example.smartmoney.domain.models.project.enums.ProjectStatus;
import com.example.smartmoney.domain.models.project.enums.ProjectType;

public record ProjectFilterRequest(
        String search,
        ProjectType type,
        ProjectStatus status,
        ProjectPriority priority
) {
}