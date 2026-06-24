package com.example.smartmoney.presentation.controllers;

import com.example.smartmoney.application.dtos.requests.transactions.AnalyticsRequest;
import com.example.smartmoney.application.dtos.requests.transactions.CreateTransactionRequest;
import com.example.smartmoney.application.dtos.requests.transactions.UpdateTransactionRequest;
import com.example.smartmoney.application.dtos.responses.CheckResponse;
import com.example.smartmoney.application.dtos.responses.TransactionAnalyticsResponse;
import com.example.smartmoney.application.dtos.responses.TransactionListResponse;
import com.example.smartmoney.application.dtos.responses.TransactionResponse;
import com.example.smartmoney.application.usecases.transaction.*;
import com.example.smartmoney.domain.exceptions.AuthenticationException;
import com.example.smartmoney.domain.models.transaction.enums.Category;
import com.example.smartmoney.domain.models.transaction.enums.TransactionType;
import com.example.smartmoney.infrastructure.security.JwtTokenProvider;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("TransactionController")
class TransactionControllerTest {

    @Mock private CreateTransactionUseCase createTransactionUseCase;
    @Mock private UpdateTransactionUseCase updateTransactionUseCase;
    @Mock private GetTransactionUseCase getTransactionUseCase;
    @Mock private GetUserTransactionsUseCase getUserTransactionsUseCase;
    @Mock private DeleteTransactionUseCase deleteTransactionUseCase;
    @Mock private GetTransactionAnalyticsUseCase getTransactionAnalyticsUseCase;
    @Mock private JwtTokenProvider jwtTokenProvider;

    private TransactionController controller;
    private Validator validator;

    private static final UUID USER_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID TX_ID   = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final String AUTH_HEADER = "Bearer test-token";
    private static final String TOKEN = "test-token";

    @BeforeEach
    void setUp() {
        controller = new TransactionController(
                createTransactionUseCase, updateTransactionUseCase,
                getTransactionUseCase, getUserTransactionsUseCase,
                deleteTransactionUseCase, jwtTokenProvider,
                getTransactionAnalyticsUseCase);

        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    // ========================== CREATE ==========================

    @Nested
    @DisplayName("POST /transactions")
    class CreateTransaction {

        @Test
        @DisplayName("returns 201 with TransactionResponse on success")
        void createTransactionSucceeds() {
            when(jwtTokenProvider.getUserIdFromToken(TOKEN)).thenReturn(USER_ID);
            TransactionResponse txResponse = buildTransactionResponse();
            when(createTransactionUseCase.execute(eq(USER_ID), any())).thenReturn(txResponse);

            CreateTransactionRequest req = validCreateRequest();
            ResponseEntity<CheckResponse<TransactionResponse>> response =
                    controller.createTransaction(AUTH_HEADER, req);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            assertThat(response.getBody().isSuccess()).isTrue();
            assertThat(response.getBody().getData()).isEqualTo(txResponse);
            assertThat(response.getBody().getMessage()).contains("created successfully");
        }

        @Test
        @DisplayName("returns 400 when use case throws exception")
        void createTransactionFailsOnUseCase() {
            when(jwtTokenProvider.getUserIdFromToken(TOKEN)).thenReturn(USER_ID);
            when(createTransactionUseCase.execute(any(), any()))
                    .thenThrow(new IllegalArgumentException("Invalid amount"));

            ResponseEntity<CheckResponse<TransactionResponse>> response =
                    controller.createTransaction(AUTH_HEADER, validCreateRequest());

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(response.getBody().isSuccess()).isFalse();
        }

        @Test
        @DisplayName("returns 400 when auth token is invalid")
        void createTransactionFailsOnInvalidToken() {
            when(jwtTokenProvider.getUserIdFromToken(TOKEN))
                    .thenThrow(new AuthenticationException("Invalid token"));

            ResponseEntity<CheckResponse<TransactionResponse>> response =
                    controller.createTransaction(AUTH_HEADER, validCreateRequest());

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(response.getBody().isSuccess()).isFalse();
        }

        @Test
        @DisplayName("CreateTransactionRequest: null amount fails validation")
        void nullAmountFailsValidation() {
            CreateTransactionRequest req = validCreateRequest();
            req.setAmount(null);
            Set<ConstraintViolation<CreateTransactionRequest>> violations = validator.validate(req);
            assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("amount"));
        }

        @Test
        @DisplayName("CreateTransactionRequest: zero amount fails validation")
        void zeroAmountFailsValidation() {
            CreateTransactionRequest req = validCreateRequest();
            req.setAmount(BigDecimal.ZERO);
            Set<ConstraintViolation<CreateTransactionRequest>> violations = validator.validate(req);
            assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("amount"));
        }

        @Test
        @DisplayName("CreateTransactionRequest: negative amount fails validation")
        void negativeAmountFailsValidation() {
            CreateTransactionRequest req = validCreateRequest();
            req.setAmount(new BigDecimal("-1.00"));
            Set<ConstraintViolation<CreateTransactionRequest>> violations = validator.validate(req);
            assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("amount"));
        }

        @Test
        @DisplayName("CreateTransactionRequest: null type fails validation")
        void nullTypeFailsValidation() {
            CreateTransactionRequest req = validCreateRequest();
            req.setType(null);
            Set<ConstraintViolation<CreateTransactionRequest>> violations = validator.validate(req);
            assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("type"));
        }

        @Test
        @DisplayName("CreateTransactionRequest: null category fails validation")
        void nullCategoryFailsValidation() {
            CreateTransactionRequest req = validCreateRequest();
            req.setCategory(null);
            Set<ConstraintViolation<CreateTransactionRequest>> violations = validator.validate(req);
            assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("category"));
        }

        @Test
        @DisplayName("CreateTransactionRequest: wrong date format fails validation")
        void wrongDateFormatFailsValidation() {
            CreateTransactionRequest req = validCreateRequest();
            req.setDate("2024-01-15 10:30");  // wrong format
            Set<ConstraintViolation<CreateTransactionRequest>> violations = validator.validate(req);
            assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("date"));
        }

        @Test
        @DisplayName("CreateTransactionRequest: description exceeding 500 chars fails validation")
        void descriptionTooLongFailsValidation() {
            CreateTransactionRequest req = validCreateRequest();
            req.setDescription("a".repeat(501));
            Set<ConstraintViolation<CreateTransactionRequest>> violations = validator.validate(req);
            assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("description"));
        }

        @Test
        @DisplayName("CreateTransactionRequest: valid request passes all validations")
        void validRequestPassesAllValidations() {
            CreateTransactionRequest req = validCreateRequest();
            Set<ConstraintViolation<CreateTransactionRequest>> violations = validator.validate(req);
            assertThat(violations).isEmpty();
        }
    }

    // ========================== GET LIST ==========================

    @Nested
    @DisplayName("GET /transactions")
    class GetUserTransactions {

        @Test
        @DisplayName("returns 200 with paginated list of transactions")
        void getTransactionsWithNoFilters() {
            when(jwtTokenProvider.getUserIdFromToken(TOKEN)).thenReturn(USER_ID);
            TransactionListResponse listResponse = buildTransactionListResponse();
            when(getUserTransactionsUseCase.execute(eq(USER_ID), any())).thenReturn(listResponse);

            ResponseEntity<CheckResponse<TransactionListResponse>> response =
                    controller.getUserTransactions(AUTH_HEADER, null, null, null, null,
                            null, null, "date", "DESC", 0, 10);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().isSuccess()).isTrue();
            assertThat(response.getBody().getData()).isEqualTo(listResponse);
        }

        @Test
        @DisplayName("returns 200 with filtered transactions by category and type")
        void getTransactionsWithCategoryFilter() {
            when(jwtTokenProvider.getUserIdFromToken(TOKEN)).thenReturn(USER_ID);
            TransactionListResponse listResponse = buildTransactionListResponse();
            when(getUserTransactionsUseCase.execute(eq(USER_ID), any())).thenReturn(listResponse);

            ResponseEntity<CheckResponse<TransactionListResponse>> response =
                    controller.getUserTransactions(AUTH_HEADER,
                            null, null, null, null,
                            Category.FOOD, TransactionType.EXPENSE,
                            "amount", "ASC", 0, 5);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().isSuccess()).isTrue();
        }

        @Test
        @DisplayName("returns 200 with amount range filter")
        void getTransactionsWithAmountFilter() {
            when(jwtTokenProvider.getUserIdFromToken(TOKEN)).thenReturn(USER_ID);
            TransactionListResponse listResponse = buildTransactionListResponse();
            when(getUserTransactionsUseCase.execute(eq(USER_ID), any())).thenReturn(listResponse);

            ResponseEntity<CheckResponse<TransactionListResponse>> response =
                    controller.getUserTransactions(AUTH_HEADER,
                            new BigDecimal("100"), new BigDecimal("500"),
                            null, null, null, null,
                            "date", "DESC", 0, 10);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @Test
        @DisplayName("returns error when date format is invalid")
        void getTransactionsWithInvalidDateReturnsError() {
            when(jwtTokenProvider.getUserIdFromToken(TOKEN)).thenReturn(USER_ID);
            when(getUserTransactionsUseCase.execute(any(), any()))
                    .thenThrow(new IllegalArgumentException("Invalid date format"));

            ResponseEntity<CheckResponse<TransactionListResponse>> response =
                    controller.getUserTransactions(AUTH_HEADER,
                            null, null, "invalid-date", null,
                            null, null, "date", "DESC", 0, 10);

            assertThat(response.getBody().isSuccess()).isFalse();
        }
    }

    // ========================== GET BY ID ==========================

    @Nested
    @DisplayName("GET /transactions/{id}")
    class GetTransactionById {

        @Test
        @DisplayName("returns 200 with TransactionResponse when found")
        void getTransactionSucceeds() {
            when(jwtTokenProvider.getUserIdFromToken(TOKEN)).thenReturn(USER_ID);
            TransactionResponse txResponse = buildTransactionResponse();
            when(getTransactionUseCase.execute(USER_ID, TX_ID)).thenReturn(txResponse);

            ResponseEntity<CheckResponse<TransactionResponse>> response =
                    controller.getTransaction(AUTH_HEADER, TX_ID);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().isSuccess()).isTrue();
            assertThat(response.getBody().getData().getId()).isEqualTo(TX_ID);
        }

        @Test
        @DisplayName("returns 404 when transaction is not found")
        void getTransactionReturns404WhenNotFound() {
            when(jwtTokenProvider.getUserIdFromToken(TOKEN)).thenReturn(USER_ID);
            when(getTransactionUseCase.execute(USER_ID, TX_ID))
                    .thenThrow(new RuntimeException("Transaction not found"));

            ResponseEntity<CheckResponse<TransactionResponse>> response =
                    controller.getTransaction(AUTH_HEADER, TX_ID);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
            assertThat(response.getBody().isSuccess()).isFalse();
        }

        @Test
        @DisplayName("returns 403 when transaction belongs to another user")
        void getTransactionReturns403WhenForbidden() {
            when(jwtTokenProvider.getUserIdFromToken(TOKEN)).thenReturn(USER_ID);
            when(getTransactionUseCase.execute(USER_ID, TX_ID))
                    .thenThrow(new RuntimeException("Unauthorized: transaction does not belong to user"));

            ResponseEntity<CheckResponse<TransactionResponse>> response =
                    controller.getTransaction(AUTH_HEADER, TX_ID);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        }
    }

    // ========================== UPDATE ==========================

    @Nested
    @DisplayName("PUT /transactions/{id}")
    class UpdateTransaction {

        @Test
        @DisplayName("returns 200 with updated TransactionResponse on success")
        void updateTransactionSucceeds() {
            when(jwtTokenProvider.getUserIdFromToken(TOKEN)).thenReturn(USER_ID);
            TransactionResponse txResponse = buildTransactionResponse();
            txResponse.setAmount(new BigDecimal("999.00"));
            when(updateTransactionUseCase.execute(eq(USER_ID), eq(TX_ID), any()))
                    .thenReturn(txResponse);

            UpdateTransactionRequest req = new UpdateTransactionRequest();
            req.setAmount(new BigDecimal("999.00"));

            ResponseEntity<CheckResponse<TransactionResponse>> response =
                    controller.updateTransaction(AUTH_HEADER, TX_ID, req);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().isSuccess()).isTrue();
            assertThat(response.getBody().getData().getAmount()).isEqualByComparingTo("999.00");
        }

        @Test
        @DisplayName("returns 404 when transaction not found during update")
        void updateTransactionReturns404WhenNotFound() {
            when(jwtTokenProvider.getUserIdFromToken(TOKEN)).thenReturn(USER_ID);
            when(updateTransactionUseCase.execute(any(), any(), any()))
                    .thenThrow(new RuntimeException("Transaction not found"));

            ResponseEntity<CheckResponse<TransactionResponse>> response =
                    controller.updateTransaction(AUTH_HEADER, TX_ID, new UpdateTransactionRequest());

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @Test
        @DisplayName("returns 403 when updating another user's transaction")
        void updateTransactionReturns403WhenForbidden() {
            when(jwtTokenProvider.getUserIdFromToken(TOKEN)).thenReturn(USER_ID);
            when(updateTransactionUseCase.execute(any(), any(), any()))
                    .thenThrow(new RuntimeException("Unauthorized access to transaction"));

            ResponseEntity<CheckResponse<TransactionResponse>> response =
                    controller.updateTransaction(AUTH_HEADER, TX_ID, new UpdateTransactionRequest());

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        }
    }

    // ========================== DELETE ==========================

    @Nested
    @DisplayName("DELETE /transactions/{id}")
    class DeleteTransaction {

        @Test
        @DisplayName("returns 200 with success=true on successful deletion")
        void deleteTransactionSucceeds() {
            when(jwtTokenProvider.getUserIdFromToken(TOKEN)).thenReturn(USER_ID);
            doNothing().when(deleteTransactionUseCase).execute(USER_ID, TX_ID);

            ResponseEntity<CheckResponse<Void>> response = controller.deleteTransaction(AUTH_HEADER, TX_ID);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().isSuccess()).isTrue();
            assertThat(response.getBody().getMessage()).contains("deleted successfully");
            verify(deleteTransactionUseCase).execute(USER_ID, TX_ID);
        }

        @Test
        @DisplayName("returns 404 when transaction not found during delete")
        void deleteTransactionReturns404WhenNotFound() {
            when(jwtTokenProvider.getUserIdFromToken(TOKEN)).thenReturn(USER_ID);
            doThrow(new RuntimeException("Transaction not found"))
                    .when(deleteTransactionUseCase).execute(any(), any());

            ResponseEntity<CheckResponse<Void>> response = controller.deleteTransaction(AUTH_HEADER, TX_ID);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @Test
        @DisplayName("returns 403 when deleting another user's transaction")
        void deleteTransactionReturns403WhenForbidden() {
            when(jwtTokenProvider.getUserIdFromToken(TOKEN)).thenReturn(USER_ID);
            doThrow(new RuntimeException("Unauthorized to delete this transaction"))
                    .when(deleteTransactionUseCase).execute(any(), any());

            ResponseEntity<CheckResponse<Void>> response = controller.deleteTransaction(AUTH_HEADER, TX_ID);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        }
    }

    // ========================== ANALYTICS ==========================

    @Nested
    @DisplayName("POST /transactions/analytics")
    class Analytics {

        @Test
        @DisplayName("returns 200 with analytics data for given year and month")
        void getAnalyticsSucceeds() {
            when(jwtTokenProvider.getUserIdFromToken(TOKEN)).thenReturn(USER_ID);
            TransactionAnalyticsResponse analyticsResponse = TransactionAnalyticsResponse.builder()
                    .monthlyStats(List.of())
                    .categoryProportions(List.of())
                    .monthlyTotalTransactions(0L)
                    .build();
            
            AnalyticsRequest req = new AnalyticsRequest();
            req.setYear(2024);
            req.setMonth(6);
            
            when(getTransactionAnalyticsUseCase.execute(eq(USER_ID), any(AnalyticsRequest.class)))
                    .thenReturn(analyticsResponse);

            ResponseEntity<CheckResponse<TransactionAnalyticsResponse>> response =
                    controller.getAnalytics(AUTH_HEADER, req);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().isSuccess()).isTrue();
            verify(getTransactionAnalyticsUseCase).execute(eq(USER_ID), eq(req));
        }

        @Test
        @DisplayName("returns 500 on analytics computation failure")
        void getAnalyticsReturns500OnError() {
            when(jwtTokenProvider.getUserIdFromToken(TOKEN)).thenReturn(USER_ID);
            
            AnalyticsRequest req = new AnalyticsRequest();
            req.setYear(2024);
            req.setMonth(6);
            
            when(getTransactionAnalyticsUseCase.execute(any(UUID.class), any(AnalyticsRequest.class)))
                    .thenThrow(new RuntimeException("Database error"));

            ResponseEntity<CheckResponse<TransactionAnalyticsResponse>> response =
                    controller.getAnalytics(AUTH_HEADER, req);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
            assertThat(response.getBody().isSuccess()).isFalse();
        }
    }

    // ========================== HELPERS ==========================

    private CreateTransactionRequest validCreateRequest() {
        CreateTransactionRequest req = new CreateTransactionRequest();
        req.setAmount(new BigDecimal("150.00"));
        req.setType(TransactionType.EXPENSE);
        req.setCategory(Category.FOOD);
        req.setDescription("Lunch");
        req.setDate("15/06/2024 12:30");
        return req;
    }

    private TransactionResponse buildTransactionResponse() {
        return TransactionResponse.builder()
                .id(TX_ID)
                .userId(USER_ID)
                .amount(new BigDecimal("150.00"))
                .type(TransactionType.EXPENSE)
                .category(Category.FOOD)
                .description("Lunch")
                .date("15/06/2024 12:30")
                .build();
    }

    private TransactionListResponse buildTransactionListResponse() {
        return TransactionListResponse.builder()
                .transactions(List.of(buildTransactionResponse()))
                .count(1)
                .page(0)
                .size(10)
                .totalElements(1L)
                .totalPages(1)
                .build();
    }
}
