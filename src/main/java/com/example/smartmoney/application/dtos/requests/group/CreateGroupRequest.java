package com.example.smartmoney.application.dtos.requests.group;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateGroupRequest {
    @NotBlank(message = "Group name is required")
    @Size(max = 255)
    private String name;

    private String description;
}
