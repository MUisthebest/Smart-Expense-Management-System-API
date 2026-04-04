package com.example.smartmoney.domain.exceptions;

public class ProjectException extends DomainException {
    public ProjectException(String message, ProjectErrorCode errorCode) {

        super(message, errorCode.name());
    }

    public ProjectException(String message, String errorCode) {
        super(message, errorCode);
    }
}
