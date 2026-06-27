package com.example.smartmoney.application.dtos.requests.notification;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
public class MarkNotificationsReadRequest {
    private List<UUID> notificationIds;
}