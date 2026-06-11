package com.example.smartmoney.application.dtos.responses.group;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class GroupInviteResponse {
    private String token;
    private String deepLinkUrl;
    private String message;
}
