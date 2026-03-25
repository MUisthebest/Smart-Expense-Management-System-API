package com.example.smartmoney.application.ports.notification;
import com.example.smartmoney.domain.models.notification.Notification;;

public interface NotificationQueuePort {
    void enqueue(Notification notification);
}