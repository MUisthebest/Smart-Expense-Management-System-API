package com.example.smartmoney.presentation.exceptions;

import com.example.smartmoney.application.dtos.responses.CheckResponse;
import com.example.smartmoney.domain.exceptions.*;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import io.swagger.v3.oas.annotations.Hidden;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
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

    /**
     * Thrown by Spring's message converter BEFORE the controller method runs whenever the
     * {@code @RequestBody} JSON can't be deserialized into the target DTO — a field holding the
     * wrong JSON type (e.g. {@code "amountLimit": "aa"} into a {@code BigDecimal}) or an unknown
     * enum literal (e.g. an invalid {@code category}). Since it happens during argument
     * resolution, it never reaches the controller's own try/catch, and without a dedicated
     * handler here it falls through to {@link #handleGlobalException} as a 500 — this is what a
     * malformed request body actually is: a 400, not a server fault.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<CheckResponse<ErrorDetails>> handleMessageNotReadable(
            HttpMessageNotReadableException ex, WebRequest request) {

        String message = describeMalformedBody(ex.getCause());
        log.warn("Malformed request body: {}", message);

        ErrorDetails errorDetails = ErrorDetails.builder()
                .timestamp(LocalDateTime.now())
                .message(message)
                .errorCode("INVALID_REQUEST_BODY")
                .path(request.getDescription(false).replace("uri=", ""))
                .build();

        CheckResponse<ErrorDetails> response = CheckResponse.<ErrorDetails>builder()
                .success(false)
                .message(message)
                .errorCode("INVALID_REQUEST_BODY")
                .data(errorDetails)
                .build();

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    /** Both a wrong JSON type (string into a number) and an unknown enum literal surface as
     *  {@link InvalidFormatException} — the field path + rejected value make a precise message
     *  possible for either case. Any other unreadable-body cause (truncated/invalid JSON syntax,
     *  a missing required structural field, ...) falls back to a generic but still-400 message. */
    private String describeMalformedBody(Throwable cause) {
        if (cause instanceof InvalidFormatException ife) {
            String targetType = ife.getTargetType() != null ? ife.getTargetType().getSimpleName() : "the expected type";
            return String.format("Invalid value '%s' for field '%s': expected %s",
                    ife.getValue(), fieldPath(ife.getPath()), targetType);
        }
        if (cause instanceof MismatchedInputException mie) {
            return String.format("Invalid or missing value for field '%s'", fieldPath(mie.getPath()));
        }
        return "Malformed request body";
    }

    private String fieldPath(List<JsonMappingException.Reference> path) {
        if (path == null || path.isEmpty()) return "request body";
        StringBuilder sb = new StringBuilder();
        for (JsonMappingException.Reference ref : path) {
            if (ref.getFieldName() != null) {
                if (!sb.isEmpty()) sb.append('.');
                sb.append(ref.getFieldName());
            } else {
                sb.append('[').append(ref.getIndex()).append(']');
            }
        }
        return !sb.isEmpty() ? sb.toString() : "request body";
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