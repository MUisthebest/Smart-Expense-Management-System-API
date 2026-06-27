package com.example.smartmoney.presentation.controllers;

import com.example.smartmoney.application.dtos.requests.budgets.CreateBudgetRequest;
import com.example.smartmoney.application.dtos.requests.budgets.CreateBulkBudgetsRequest;
import com.example.smartmoney.application.dtos.requests.budgets.UpdateBudgetRequest;
import com.example.smartmoney.application.dtos.responses.*;
import com.example.smartmoney.application.usecases.budget.*;
import com.example.smartmoney.domain.exceptions.AuthenticationException;
import com.example.smartmoney.domain.models.budget.Budget;
import com.example.smartmoney.domain.models.transaction.enums.Category;
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
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("BudgetController")
class BudgetControllerTest {

    @Mock private CreateBudgetUseCase createBudgetUseCase;
    @Mock private CreateBulkBudgetsUseCase createBulkBudgetsUseCase;
    @Mock private GetBudgetListUseCase getBudgetListUseCase;
    @Mock private GetBudgetDetailUseCase getBudgetDetailUseCase;
    @Mock private UpdateBudgetUseCase updateBudgetUseCase;
    @Mock private DeleteBudgetUseCase deleteBudgetUseCase;
    @Mock private JwtTokenProvider jwtTokenProvider;
    @Mock private UpdateBulkBudgetsUseCase updateBulkBudgetsUseCase;
    @Mock private UpsertBulkBudgetsUseCase upsertBulkBudgetsUseCase;
    private BudgetController controller;
    private Validator validator;

    private static final UUID USER_ID   = UUID.fromString("44444444-4444-4444-4444-444444444444");
    private static final UUID BUDGET_ID = UUID.fromString("55555555-5555-5555-5555-555555555555");
    private static final String AUTH_HEADER = "Bearer budget-token";
    private static final String TOKEN = "budget-token";

    @BeforeEach
    void setUp() {
        controller = new BudgetController(
                createBudgetUseCase,
                createBulkBudgetsUseCase,
                updateBulkBudgetsUseCase,   // ✅ thêm
                upsertBulkBudgetsUseCase,   // ✅ thêm
                getBudgetListUseCase,
                getBudgetDetailUseCase,
                updateBudgetUseCase,
                deleteBudgetUseCase,
                jwtTokenProvider
        );
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    // ========================== CREATE ==========================

    @Nested
    @DisplayName("POST /budgets")
    class CreateBudget {

        @Test
        @DisplayName("returns 201 with BudgetItemResponse on success")
        void createBudgetSucceeds() {
            when(jwtTokenProvider.getUserIdFromToken(TOKEN)).thenReturn(USER_ID);
            BudgetItemResponse budgetResponse = buildBudgetItemResponse();
            when(createBudgetUseCase.execute(eq(USER_ID), any())).thenReturn(budgetResponse);

            ResponseEntity<CheckResponse<BudgetItemResponse>> response =
                    controller.createBudget(AUTH_HEADER, validCreateBudgetRequest());

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            assertThat(response.getBody().isSuccess()).isTrue();
            assertThat(response.getBody().getData().getBudgetId()).isEqualTo(BUDGET_ID);
            assertThat(response.getBody().getMessage()).contains("Budget created successfully");
            verify(createBudgetUseCase).execute(eq(USER_ID), any());
        }

        @Test
        @DisplayName("returns 400 when budget already exists for category/month/year")
        void createBudgetFailsOnDuplicate() {
            when(jwtTokenProvider.getUserIdFromToken(TOKEN)).thenReturn(USER_ID);
            when(createBudgetUseCase.execute(any(), any()))
                    .thenThrow(new IllegalArgumentException("Budget already exists for FOOD in 6/2024"));

            ResponseEntity<CheckResponse<BudgetItemResponse>> response =
                    controller.createBudget(AUTH_HEADER, validCreateBudgetRequest());

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(response.getBody().isSuccess()).isFalse();
            assertThat(response.getBody().getMessage()).contains("Failed to create budget");
        }

        @Test
        @DisplayName("returns 400 on invalid auth token")
        void createBudgetFailsOnBadToken() {
            when(jwtTokenProvider.getUserIdFromToken(TOKEN))
                    .thenThrow(new AuthenticationException("Invalid token"));

            ResponseEntity<CheckResponse<BudgetItemResponse>> response =
                    controller.createBudget(AUTH_HEADER, validCreateBudgetRequest());

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(response.getBody().isSuccess()).isFalse();
        }

        @Test
        @DisplayName("CreateBudgetRequest: null category fails validation")
        void nullCategoryFailsValidation() {
            CreateBudgetRequest req = validCreateBudgetRequest();
            req.setCategory(null);
            Set<ConstraintViolation<CreateBudgetRequest>> violations = validator.validate(req);
            assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("category"));
        }

        @Test
        @DisplayName("CreateBudgetRequest: null amountLimit fails validation")
        void nullAmountLimitFailsValidation() {
            CreateBudgetRequest req = validCreateBudgetRequest();
            req.setAmountLimit(null);
            Set<ConstraintViolation<CreateBudgetRequest>> violations = validator.validate(req);
            assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("amountLimit"));
        }

        @Test
        @DisplayName("CreateBudgetRequest: zero amountLimit fails validation")
        void zeroAmountLimitFailsValidation() {
            CreateBudgetRequest req = validCreateBudgetRequest();
            req.setAmountLimit(BigDecimal.ZERO);
            Set<ConstraintViolation<CreateBudgetRequest>> violations = validator.validate(req);
            assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("amountLimit"));
        }

        @Test
        @DisplayName("CreateBudgetRequest: month below 1 fails validation")
        void monthBelowRangeFailsValidation() {
            CreateBudgetRequest req = validCreateBudgetRequest();
            req.setMonth(0);
            Set<ConstraintViolation<CreateBudgetRequest>> violations = validator.validate(req);
            assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("month"));
        }

        @Test
        @DisplayName("CreateBudgetRequest: month above 12 fails validation")
        void monthAboveRangeFailsValidation() {
            CreateBudgetRequest req = validCreateBudgetRequest();
            req.setMonth(13);
            Set<ConstraintViolation<CreateBudgetRequest>> violations = validator.validate(req);
            assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("month"));
        }

        @Test
        @DisplayName("CreateBudgetRequest: year below 1900 fails validation")
        void yearBelowMinFailsValidation() {
            CreateBudgetRequest req = validCreateBudgetRequest();
            req.setYear(1899);
            Set<ConstraintViolation<CreateBudgetRequest>> violations = validator.validate(req);
            assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("year"));
        }

        @Test
        @DisplayName("CreateBudgetRequest: valid request passes all validations")
        void validRequestPassesAllValidations() {
            CreateBudgetRequest req = validCreateBudgetRequest();
            Set<ConstraintViolation<CreateBudgetRequest>> violations = validator.validate(req);
            assertThat(violations).isEmpty();
        }
    }

    // ========================== CREATE BULK ==========================

    @Nested
    @DisplayName("POST /budgets/bulk")
    class CreateBulkBudgets {

        @Test
        @DisplayName("returns 201 with BulkBudgetsResponse on success")
        void createBulkBudgetsSucceeds() {
            when(jwtTokenProvider.getUserIdFromToken(TOKEN)).thenReturn(USER_ID);
            BulkBudgetsResponse bulkResponse = BulkBudgetsResponse.builder()
                    .totalCreated(2)
                    .failedItems(List.of())
                    .build();
            when(createBulkBudgetsUseCase.execute(eq(USER_ID), any())).thenReturn(bulkResponse);

            CreateBulkBudgetsRequest req = buildBulkBudgetsRequest();
            ResponseEntity<CheckResponse<BulkBudgetsResponse>> response =
                    controller.createBulkBudgets(AUTH_HEADER, req);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            assertThat(response.getBody().isSuccess()).isTrue();
            assertThat(response.getBody().getData().getTotalCreated()).isEqualTo(2);
        }

        @Test
        @DisplayName("returns 400 when bulk creation fails")
        void createBulkBudgetsFailsOnError() {
            when(jwtTokenProvider.getUserIdFromToken(TOKEN)).thenReturn(USER_ID);
            when(createBulkBudgetsUseCase.execute(any(), any()))
                    .thenThrow(new IllegalArgumentException("Duplicate categories in bulk request"));

            ResponseEntity<CheckResponse<BulkBudgetsResponse>> response =
                    controller.createBulkBudgets(AUTH_HEADER, buildBulkBudgetsRequest());

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(response.getBody().isSuccess()).isFalse();
        }
    }

    // ========================== GET LIST ==========================

    @Nested
    @DisplayName("GET /budgets")
    class GetBudgetList {

        @Test
        @DisplayName("returns 200 with BudgetListResponse for given month and year")
        void getBudgetListSucceeds() {
            when(jwtTokenProvider.getUserIdFromToken(TOKEN)).thenReturn(USER_ID);
            BudgetListResponse listResponse = BudgetListResponse.builder()
                    .month(6)
                    .year(2024)
                    .items(List.of(buildBudgetItemResponse()))
                    .build();
            when(getBudgetListUseCase.execute(eq(USER_ID), eq(6), eq(2024))).thenReturn(listResponse);

            ResponseEntity<CheckResponse<BudgetListResponse>> response =
                    controller.getBudgetList(AUTH_HEADER, 6, 2024);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().isSuccess()).isTrue();
            assertThat(response.getBody().getData().getItems()).hasSize(1);
        }

        @Test
        @DisplayName("returns 400 when month or year parameters are invalid")
        void getBudgetListFailsOnInvalidParams() {
            when(jwtTokenProvider.getUserIdFromToken(TOKEN)).thenReturn(USER_ID);
            when(getBudgetListUseCase.execute(any(), any(), any()))
                    .thenThrow(new IllegalArgumentException("Invalid month: 0"));

            ResponseEntity<CheckResponse<BudgetListResponse>> response =
                    controller.getBudgetList(AUTH_HEADER, 0, 2024);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(response.getBody().isSuccess()).isFalse();
        }
    }

    // ========================== GET DETAIL ==========================

    @Nested
    @DisplayName("GET /budgets/{id}")
    class GetBudgetDetail {

        @Test
        @DisplayName("returns 200 with BudgetItemResponse when found")
        void getBudgetDetailSucceeds() {
            when(jwtTokenProvider.getUserIdFromToken(TOKEN)).thenReturn(USER_ID);
            BudgetItemResponse budgetResponse = buildBudgetItemResponse();
            when(getBudgetDetailUseCase.execute(BUDGET_ID, USER_ID)).thenReturn(budgetResponse);

            ResponseEntity<CheckResponse<BudgetItemResponse>> response =
                    controller.getBudgetDetail(AUTH_HEADER, BUDGET_ID);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().isSuccess()).isTrue();
            assertThat(response.getBody().getData().getBudgetId()).isEqualTo(BUDGET_ID);
        }

        @Test
        @DisplayName("returns 404 when budget not found")
        void getBudgetDetailReturns404WhenNotFound() {
            when(jwtTokenProvider.getUserIdFromToken(TOKEN)).thenReturn(USER_ID);
            when(getBudgetDetailUseCase.execute(any(), any()))
                    .thenThrow(new RuntimeException("Budget not found"));

            ResponseEntity<CheckResponse<BudgetItemResponse>> response =
                    controller.getBudgetDetail(AUTH_HEADER, BUDGET_ID);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
            assertThat(response.getBody().isSuccess()).isFalse();
        }

        @Test
        @DisplayName("returns 403 when budget does not belong to user")
        void getBudgetDetailReturns403WhenForbidden() {
            when(jwtTokenProvider.getUserIdFromToken(TOKEN)).thenReturn(USER_ID);
            when(getBudgetDetailUseCase.execute(any(), any()))
                    .thenThrow(new RuntimeException("Budget does not belong to current user"));

            ResponseEntity<CheckResponse<BudgetItemResponse>> response =
                    controller.getBudgetDetail(AUTH_HEADER, BUDGET_ID);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        }
    }

    // ========================== UPDATE ==========================

    @Nested
    @DisplayName("PUT /budgets/{id}")
    class UpdateBudget {

        @Test
        @DisplayName("returns 200 with updated BudgetItemResponse on success")
        void updateBudgetSucceeds() {
            when(jwtTokenProvider.getUserIdFromToken(TOKEN)).thenReturn(USER_ID);
            BudgetItemResponse budgetResponse = buildBudgetItemResponse();
            budgetResponse.setAmountLimit(new BigDecimal("2000.00"));
            when(updateBudgetUseCase.execute(eq(BUDGET_ID), eq(USER_ID), any()))
                    .thenReturn(budgetResponse);

            UpdateBudgetRequest req = new UpdateBudgetRequest();
            req.setAmountLimit(new BigDecimal("2000.00"));

            ResponseEntity<CheckResponse<BudgetItemResponse>> response =
                    controller.updateBudget(AUTH_HEADER, BUDGET_ID, req);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().isSuccess()).isTrue();
            assertThat(response.getBody().getData().getAmountLimit()).isEqualByComparingTo("2000.00");
        }

        @Test
        @DisplayName("returns 404 when budget not found during update")
        void updateBudgetReturns404WhenNotFound() {
            when(jwtTokenProvider.getUserIdFromToken(TOKEN)).thenReturn(USER_ID);
            when(updateBudgetUseCase.execute(any(), any(), any()))
                    .thenThrow(new RuntimeException("Budget not found"));

            ResponseEntity<CheckResponse<BudgetItemResponse>> response =
                    controller.updateBudget(AUTH_HEADER, BUDGET_ID, new UpdateBudgetRequest());

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @Test
        @DisplayName("returns 403 when updating another user's budget")
        void updateBudgetReturns403WhenForbidden() {
            when(jwtTokenProvider.getUserIdFromToken(TOKEN)).thenReturn(USER_ID);
            when(updateBudgetUseCase.execute(any(), any(), any()))
                    .thenThrow(new RuntimeException("Budget does not belong to user"));

            ResponseEntity<CheckResponse<BudgetItemResponse>> response =
                    controller.updateBudget(AUTH_HEADER, BUDGET_ID, new UpdateBudgetRequest());

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        }
    }

    // ========================== DELETE ==========================

    @Nested
    @DisplayName("DELETE /budgets/{id}")
    class DeleteBudget {

        @Test
        @DisplayName("returns 200 with success=true on successful deletion")
        void deleteBudgetSucceeds() {
            when(jwtTokenProvider.getUserIdFromToken(TOKEN)).thenReturn(USER_ID);
            doNothing().when(deleteBudgetUseCase).execute(BUDGET_ID, USER_ID);

            ResponseEntity<CheckResponse<Void>> response = controller.deleteBudget(AUTH_HEADER, BUDGET_ID);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().isSuccess()).isTrue();
            assertThat(response.getBody().getMessage()).contains("Budget deleted successfully");
            verify(deleteBudgetUseCase).execute(BUDGET_ID, USER_ID);
        }

        @Test
        @DisplayName("returns 404 when budget not found during delete")
        void deleteBudgetReturns404WhenNotFound() {
            when(jwtTokenProvider.getUserIdFromToken(TOKEN)).thenReturn(USER_ID);
            doThrow(new RuntimeException("Budget not found"))
                    .when(deleteBudgetUseCase).execute(any(), any());

            ResponseEntity<CheckResponse<Void>> response = controller.deleteBudget(AUTH_HEADER, BUDGET_ID);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
            assertThat(response.getBody().isSuccess()).isFalse();
        }

        @Test
        @DisplayName("returns 403 when deleting another user's budget")
        void deleteBudgetReturns403WhenForbidden() {
            when(jwtTokenProvider.getUserIdFromToken(TOKEN)).thenReturn(USER_ID);
            doThrow(new RuntimeException("Budget does not belong to user"))
                    .when(deleteBudgetUseCase).execute(any(), any());

            ResponseEntity<CheckResponse<Void>> response = controller.deleteBudget(AUTH_HEADER, BUDGET_ID);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        }
    }

    // ========================== HELPERS ==========================

    private CreateBudgetRequest validCreateBudgetRequest() {
        CreateBudgetRequest req = new CreateBudgetRequest();
        req.setCategory(Category.FOOD);
        req.setAmountLimit(new BigDecimal("500.00"));
        req.setMonth(6);
        req.setYear(2024);
        return req;
    }

    private CreateBulkBudgetsRequest buildBulkBudgetsRequest() {
        CreateBulkBudgetsRequest.BudgetItem item1 = new CreateBulkBudgetsRequest.BudgetItem();
        item1.setCategory(Category.FOOD);
        item1.setAmountLimit(new BigDecimal("500.00"));

        CreateBulkBudgetsRequest.BudgetItem item2 = new CreateBulkBudgetsRequest.BudgetItem();
        item2.setCategory(Category.TRANSPORTATION);
        item2.setAmountLimit(new BigDecimal("300.00"));

        CreateBulkBudgetsRequest req = new CreateBulkBudgetsRequest();
        req.setBudgets(List.of(item1, item2));
        req.setMonth(6);
        req.setYear(2024);
        return req;
    }

    private BudgetItemResponse buildBudgetItemResponse() {
        return BudgetItemResponse.builder()
                .budgetId(BUDGET_ID)
                .userId(USER_ID)
                .category(Category.FOOD)
                .amountLimit(new BigDecimal("500.00"))
                .month(6)
                .year(2024)
                .spent(new BigDecimal("150.00"))
                .remaining(new BigDecimal("350.00"))
                .progressPercent(30.0)
                .alertLevel(Budget.AlertLevel.SAFE)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }
}
