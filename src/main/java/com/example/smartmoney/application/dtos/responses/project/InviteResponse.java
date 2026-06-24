package com.example.smartmoney.application.dtos.responses.project;

import lombok.Builder;
import lombok.Getter;

/**
 * Returned after an admin successfully invites a member.
 * Contains the deep-link URL that must be sent to the invitee.
 */
@Getter
@Builder
public class InviteResponse {
    private final String token;
    private final String deepLinkUrl;
    private final String message;
}
