package com.example.smartmoney.application.dtos.responses.project;

import com.example.smartmoney.domain.models.user.User;
import lombok.Builder;
import lombok.Data;

/**
 * A member taking part in a group project, as shown on the project list — just enough for the
 * client to render the participant strip (avatar + display name).
 */
@Data
@Builder
public class ProjectParticipantResponse {
    private String userId;
    private String username;
    private String fullName;
    private String avatarUrl;

    public static ProjectParticipantResponse fromDomain(User u) {
        return ProjectParticipantResponse.builder()
                .userId(u.getId().toString())
                .username(u.getUsername())
                .fullName(u.getFullName())
                .avatarUrl(u.getAvatar() != null ? u.getAvatar().getUrl() : null)
                .build();
    }
}
