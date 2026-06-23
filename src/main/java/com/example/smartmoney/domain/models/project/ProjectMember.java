package com.example.smartmoney.domain.models.project;

import com.example.smartmoney.domain.models.project.enums.JoinStatus;
import com.example.smartmoney.domain.models.project.valueobjects.UserId;

import java.util.Objects;

public class ProjectMember {
    private final UserId userId;
    private JoinStatus joinStatus;
    private boolean admin;

    public ProjectMember(UserId userId, JoinStatus joinStatus, boolean admin) {
        this.userId = Objects.requireNonNull(userId);
        this.joinStatus = Objects.requireNonNull(joinStatus);
        this.admin = admin;
    }

    public UserId getUserId() {
        return userId;
    }

    public JoinStatus getJoinStatus() {
        return joinStatus;
    }

    public boolean isAdmin() {
        return admin;
    }

    public void invite() {
        this.joinStatus = JoinStatus.INVITED;
    }

    public void accept() {
        this.joinStatus = JoinStatus.JOINED;
    }

    public void leave() {
        this.joinStatus = JoinStatus.LEFT;
    }

    public void setAdmin(boolean admin) {
        this.admin = admin;
    }
}
