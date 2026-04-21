package com.example.smartmoney.application.usecases.project;

import com.example.smartmoney.domain.exceptions.ProjectErrorCode;
import com.example.smartmoney.domain.exceptions.ProjectException;
import com.example.smartmoney.domain.models.project.Project;
import com.example.smartmoney.domain.models.project.enums.JoinStatus;
import com.example.smartmoney.domain.models.project.enums.ProjectType;
import com.example.smartmoney.domain.models.project.valueobjects.UserId;

final class ProjectAccessPolicy {

    private ProjectAccessPolicy() {
    }

    static void requireCanView(Project project, UserId actor) {
        if (actor.equals(project.getOwnerId())) {
            return;
        }

        if (project.getType() == ProjectType.GROUP) {
            boolean joined = project.getMembers().stream()
                    .anyMatch(m -> m.getUserId().equals(actor) && m.getJoinStatus() == JoinStatus.JOINED);
            if (joined) {
                return;
            }
        }

        throw new ProjectException("Permission denied", ProjectErrorCode.PROJECT_PERMISSION_DENIED);
    }

    static void requireCanManage(Project project, UserId actor) {
        if (actor.equals(project.getOwnerId())) {
            return;
        }

        if (project.getType() == ProjectType.GROUP) {
            boolean canManage = project.getMembers().stream()
                    .anyMatch(m -> m.getUserId().equals(actor)
                            && m.getJoinStatus() == JoinStatus.JOINED
                            && m.isAdmin());
            if (canManage) {
                return;
            }
        }

        throw new ProjectException("Permission denied", ProjectErrorCode.PROJECT_PERMISSION_DENIED);
    }
}
