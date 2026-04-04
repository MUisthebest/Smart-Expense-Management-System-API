package com.example.smartmoney.domain.models.notification;

import lombok.*;
import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PushToken {
    private String id;
    private String userId;
    private String pushToken;
    private String platform;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}