package com.example.smartmoney.application.usecases.project;

import com.example.smartmoney.domain.exceptions.ProjectErrorCode;
import com.example.smartmoney.domain.exceptions.ProjectException;
import com.example.smartmoney.domain.models.project.Project;
import com.example.smartmoney.domain.models.project.valueobjects.UserId;

final class ProjectAccessPolicy {

    private ProjectAccessPolicy() {
    }

    static void requireCanView(Project project, UserId actor) {
        if (!actor.equals(project.getOwnerId())) {
            throw new ProjectException("Permission denied", ProjectErrorCode.PROJECT_PERMISSION_DENIED);
        }
    }

    static void requireCanManage(Project project, UserId actor) {
        if (!actor.equals(project.getOwnerId())) {
            throw new ProjectException("Permission denied", ProjectErrorCode.PROJECT_PERMISSION_DENIED);
        }
    }
}