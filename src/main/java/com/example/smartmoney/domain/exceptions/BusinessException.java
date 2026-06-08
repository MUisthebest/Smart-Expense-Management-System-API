package com.example.smartmoney.domain.exceptions;

public class BusinessException extends RuntimeException {

    private final String errorCode;

    public BusinessException(String message, CommonErrorCode errorCode) {
        super(message);
        this.errorCode = errorCode.name();
    }

    public BusinessException(String message, String errorCode) {
        super(message);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
