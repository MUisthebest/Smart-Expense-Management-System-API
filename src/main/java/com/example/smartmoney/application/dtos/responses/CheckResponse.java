package com.example.smartmoney.application.dtos.responses;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CheckResponse<T> {
    private boolean success;
    private String message;
    private T data;
    private String errorCode;
    
    public static <T> CheckResponse<T> success(T data) {
        return CheckResponse.<T>builder()
            .success(true)
            .message("Success")
            .data(data)
            .build();
    }
    
    public static <T> CheckResponse<T> success(String message, T data) {
        return CheckResponse.<T>builder()
            .success(true)
            .message(message)
            .data(data)
            .build();
    }
    
    public static <T> CheckResponse<T> error(String message) {
        return CheckResponse.<T>builder()
            .success(false)
            .message(message)
            .build();
    }
    
    public static <T> CheckResponse<T> error(String message, String errorCode) {
        return CheckResponse.<T>builder()
            .success(false)
            .message(message)
            .errorCode(errorCode)
            .build();
    }
    
    public static <T> CheckResponse<T> error(String message, String errorCode, T data) {
        return CheckResponse.<T>builder()
            .success(false)
            .message(message)
            .errorCode(errorCode)
            .data(data)
            .build();
    }
}