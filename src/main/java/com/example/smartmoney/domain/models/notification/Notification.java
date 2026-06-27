package com.example.smartmoney.domain.models.notification;

import java.time.LocalDateTime;
import java.util.UUID;

public class Notification {
    private UUID id;
    private UUID userId;
    private String content;
    private String deepLink;
    private LocalDateTime createdAt;
    private boolean read = false; 

    public Notification() {}

    public Notification(UUID userId, String content, LocalDateTime createdAt) {
        this.userId = userId;
        this.content = content;
        this.createdAt = createdAt;
    }

    public Notification(UUID userId, String content, String deepLink, LocalDateTime createdAt) {
        this.userId = userId;
        this.content = content;
        this.deepLink = deepLink;
        this.createdAt = createdAt;
    }

    public Notification(UUID id, UUID userId, String content, String deepLink, LocalDateTime createdAt, boolean read) {
        this.id = id;
        this.userId = userId;
        this.content = content;
        this.deepLink = deepLink;
        this.createdAt = createdAt;
        this.read = read;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public String getDeepLink() { return deepLink; }
    public void setDeepLink(String deepLink) { this.deepLink = deepLink; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public boolean getRead() { return read; }
    public void setRead(boolean read) { this.read = read; }
}