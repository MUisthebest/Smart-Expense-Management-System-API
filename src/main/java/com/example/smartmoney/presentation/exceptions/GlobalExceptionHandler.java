package com.example.smartmoney.presentation.exceptions;

import com.example.smartmoney.application.dtos.responses.CheckResponse;
import com.example.smartmoney.domain.exceptions.*;
import io.swagger.v3.oas.annotations.Hidden;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
@Slf4j
@Hidden
public class GlobalExceptionHandler {
    
    @ExceptionHandler(DomainException.class)
    public ResponseEntity<CheckResponse<ErrorDetails>> handleDomainException(
            DomainException ex, WebRequest request) {
        
        log.warn("Domain exception: {}", ex.getMessage());
        
        ErrorDetails errorDetails = ErrorDetails.builder()
            .timestamp(LocalDateTime.now())
            .message(ex.getMessage())
            .errorCode(ex.getErrorCode())
            .path(request.getDescription(false).replace("uri=", ""))
            .build();
        
        CheckResponse<ErrorDetails> response = CheckResponse.<ErrorDetails>builder()
            .success(false)
            .message(ex.getMessage())
            .data(errorDetails)
            .errorCode(ex.getErrorCode())
            .build();
        
        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }
    
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<CheckResponse<ErrorDetails>> handleAuthenticationException(
            AuthenticationException ex, WebRequest request) {
        
        log.warn("Authentication exception: {}", ex.getMessage());
        
        ErrorDetails errorDetails = ErrorDetails.builder()
            .timestamp(LocalDateTime.now())
            .message(ex.getMessage())
            .errorCode(ex.getErrorCode())
            .path(request.getDescription(false).replace("uri=", ""))
            .build();
        
        CheckResponse<ErrorDetails> response = CheckResponse.<ErrorDetails>builder()
            .success(false)
            .message(ex.getMessage())
            .data(errorDetails)
            .errorCode(ex.getErrorCode())
            .build();
        
        return new ResponseEntity<>(response, HttpStatus.UNAUTHORIZED);
    }
    
    @ExceptionHandler(AuthorizationException.class)
    public ResponseEntity<CheckResponse<ErrorDetails>> handleAuthorizationException(
            AuthorizationException ex, WebRequest request) {
        
        log.warn("Authorization exception: {}", ex.getMessage());
        
        ErrorDetails errorDetails = ErrorDetails.builder()
            .timestamp(LocalDateTime.now())
            .message(ex.getMessage())
            .errorCode(ex.getErrorCode())
            .path(request.getDescription(false).replace("uri=", ""))
            .build();
        
        CheckResponse<ErrorDetails> response = CheckResponse.<ErrorDetails>builder()
            .success(false)
            .message(ex.getMessage())
            .data(errorDetails)
            .errorCode(ex.getErrorCode())
            .build();
        
        return new ResponseEntity<>(response, HttpStatus.FORBIDDEN);
    }
    
    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<CheckResponse<ErrorDetails>> handleEntityNotFoundException(
            EntityNotFoundException ex, WebRequest request) {
        
        log.warn("Entity not found: {}", ex.getMessage());
        
        ErrorDetails errorDetails = ErrorDetails.builder()
            .timestamp(LocalDateTime.now())
            .message(ex.getMessage())
            .errorCode(ex.getErrorCode())
            .path(request.getDescription(false).replace("uri=", ""))
            .build();
        
        CheckResponse<ErrorDetails> response = CheckResponse.<ErrorDetails>builder()
            .success(false)
            .message(ex.getMessage())
            .data(errorDetails)
            .errorCode(ex.getErrorCode())
            .build();
        
        return new ResponseEntity<>(response, HttpStatus.NOT_FOUND);
    }
    
    @ExceptionHandler(BusinessRuleValidationException.class)
    public ResponseEntity<CheckResponse<ErrorDetails>> handleBusinessRuleValidationException(
            BusinessRuleValidationException ex, WebRequest request) {
        
        log.warn("Business rule violation: {}", ex.getMessage());
        
        ErrorDetails errorDetails = ErrorDetails.builder()
            .timestamp(LocalDateTime.now())
            .message(ex.getMessage())
            .errorCode(ex.getErrorCode())
            .path(request.getDescription(false).replace("uri=", ""))
            .build();
        
        CheckResponse<ErrorDetails> response = CheckResponse.<ErrorDetails>builder()
            .success(false)
            .message(ex.getMessage())
            .data(errorDetails)
            .errorCode(ex.getErrorCode())
            .build();
        
        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }
    
    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<CheckResponse<Map<String, String>>> handleValidationException(
            ValidationException ex, WebRequest request) {
        
        log.warn("Validation exception: {}", ex.getMessage());
        
        CheckResponse<Map<String, String>> response = CheckResponse.<Map<String, String>>builder()
            .success(false)
            .message(ex.getMessage())
            .data(ex.getErrors())
            .errorCode(ex.getErrorCode())
            .build();
        
        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }
    
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<CheckResponse<Map<String, String>>> handleValidationExceptions(
            MethodArgumentNotValidException ex) {
        
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach((error) -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            errors.put(fieldName, errorMessage);
        });
        
        log.warn("Validation errors: {}", errors);
        
        CheckResponse<Map<String, String>> response = CheckResponse.<Map<String, String>>builder()
            .success(false)
            .message("Validation failed")
            .data(errors)
            .errorCode("VALIDATION_ERROR")
            .build();
        
        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }
    
    @ExceptionHandler(Exception.class)
    public ResponseEntity<CheckResponse<ErrorDetails>> handleGlobalException(
            Exception ex, WebRequest request) {
        
        log.error("Internal server error", ex);
        
        ErrorDetails errorDetails = ErrorDetails.builder()
            .timestamp(LocalDateTime.now())
            .message("An unexpected error occurred")
            .errorCode("INTERNAL_SERVER_ERROR")
            .path(request.getDescription(false).replace("uri=", ""))
            .build();
        
        CheckResponse<ErrorDetails> response = CheckResponse.<ErrorDetails>builder()
            .success(false)
            .message("Internal server error")
            .data(errorDetails)
            .errorCode("INTERNAL_SERVER_ERROR")
            .build();
        
        return new ResponseEntity<>(response, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @ExceptionHandler(ProjectException.class)
    public ResponseEntity<CheckResponse<ErrorDetails>> handleProjectException(
            ProjectException ex, WebRequest request) {

        HttpStatus status = mapProjectStatus(ex.getErrorCode());

        ErrorDetails errorDetails = ErrorDetails.builder()
                .timestamp(LocalDateTime.now())
                .message(ex.getMessage())
                .errorCode(ex.getErrorCode())
                .path(request.getDescription(false).replace("uri=", ""))
                .build();

        CheckResponse<ErrorDetails> response = CheckResponse.<ErrorDetails>builder()
                .success(false)
                .message(ex.getMessage())
                .data(errorDetails)
                .errorCode(ex.getErrorCode())
                .build();

        return new ResponseEntity<>(response, status);
    }

    private HttpStatus mapProjectStatus(String errorCode) {
        if (ProjectErrorCode.PROJECT_NOT_FOUND.name().equals(errorCode)) return HttpStatus.NOT_FOUND;
        if (ProjectErrorCode.PROJECT_TRACKING_NOT_FOUND.name().equals(errorCode)) return HttpStatus.NOT_FOUND;
        if (ProjectErrorCode.PROJECT_PERMISSION_DENIED.name().equals(errorCode)) return HttpStatus.FORBIDDEN;
        return HttpStatus.BAD_REQUEST;
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<CheckResponse<ErrorDetails>> handleTypeMismatch(
            MethodArgumentTypeMismatchException ex,
            WebRequest request
    ) {
        String message = "Invalid request parameter: " + ex.getName()
                + " = " + ex.getValue();

        ErrorDetails errorDetails = ErrorDetails.builder()
                .timestamp(LocalDateTime.now())
                .message(message)
                .errorCode("INVALID_REQUEST_PARAM")
                .path(request.getDescription(false).replace("uri=", ""))
                .build();

        CheckResponse<ErrorDetails> response = CheckResponse.<ErrorDetails>builder()
                .success(false)
                .message(message)
                .errorCode("INVALID_REQUEST_PARAM")
                .data(errorDetails)
                .build();

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(response);
    }

    @ExceptionHandler(InsufficientSafeSpendingException.class)
    public ResponseEntity<CheckResponse<Map<String, Object>>> handleInsufficientSafeSpending(
            InsufficientSafeSpendingException ex,
            WebRequest request
    ) {
        log.warn("Insufficient safe spending: requested={}, available={}",
                ex.getRequested(), ex.getAvailable());

        Map<String, Object> payload = new HashMap<>();
        payload.put("requested", ex.getRequested());
        payload.put("available", ex.getAvailable());
        payload.put("timestamp", LocalDateTime.now());
        payload.put("path", request.getDescription(false).replace("uri=", ""));

        CheckResponse<Map<String, Object>> response = CheckResponse.<Map<String, Object>>builder()
                .success(false)
                .message(ex.getMessage())
                .data(payload)
                .errorCode(ex.getErrorCode())
                .build();

        // 422 Unprocessable Entity — the request is syntactically valid but
        // violates the safe-spending business constraint.
        return new ResponseEntity<>(response, HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<CheckResponse<ErrorDetails>> handleBusinessException(
            BusinessException ex,
            WebRequest request
    ) {
        HttpStatus status = mapBusinessStatus(ex.getErrorCode());

        ErrorDetails errorDetails = ErrorDetails.builder()
                .timestamp(LocalDateTime.now())
                .message(ex.getMessage())
                .errorCode(ex.getErrorCode())
                .path(request.getDescription(false).replace("uri=", ""))
                .build();

        CheckResponse<ErrorDetails> response = CheckResponse.<ErrorDetails>builder()
                .success(false)
                .message(ex.getMessage())
                .data(errorDetails)
                .errorCode(ex.getErrorCode())
                .build();

        return new ResponseEntity<>(response, status);
    }

    private HttpStatus mapBusinessStatus(String errorCode) {
        if (CommonErrorCode.RESOURCE_NOT_FOUND.name().equals(errorCode)) {
            return HttpStatus.NOT_FOUND;
        }

        if (CommonErrorCode.RESOURCE_ALREADY_EXISTS.name().equals(errorCode)) {
            return HttpStatus.CONFLICT;
        }

        if (CommonErrorCode.PERMISSION_DENIED.name().equals(errorCode)) {
            return HttpStatus.FORBIDDEN;
        }

        if (CommonErrorCode.INVALID_REQUEST.name().equals(errorCode)) {
            return HttpStatus.BAD_REQUEST;
        }

        if (CommonErrorCode.BUSINESS_RULE_VIOLATION.name().equals(errorCode)) {
            return HttpStatus.BAD_REQUEST;
        }

        return HttpStatus.BAD_REQUEST;
    }
}