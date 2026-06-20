package com.example.smartmoney.presentation.controllers;

import com.example.smartmoney.application.dtos.requests.userincome.CreateUserIncomeRequest;
import com.example.smartmoney.application.dtos.requests.userincome.UpdateUserIncomeRequest;
import com.example.smartmoney.application.dtos.responses.CheckResponse;
import com.example.smartmoney.application.dtos.responses.userincome.UserIncomeResponse;
import com.example.smartmoney.application.usecases.userincome.*;
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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserIncomeController")
class UserIncomeControllerTest {

    @Mock private CreateUserIncomeUseCase createUserIncomeUseCase;
    @Mock private GetUserIncomeUseCase getUserIncomeUseCase;
    @Mock private UpdateUserIncomeUseCase updateUserIncomeUseCase;
    @Mock private DeleteUserIncomeUseCase deleteUserIncomeUseCase;

    private UserIncomeController controller;
    private Validator validator;

    private static final String ACTOR_ID = "66666666-6666-6666-6666-666666666666";

    @BeforeEach
    void setUp() {
        controller = new UserIncomeController(
                createUserIncomeUseCase, getUserIncomeUseCase,
                updateUserIncomeUseCase, deleteUserIncomeUseCase);

        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    private Authentication mockAuth() {
        return new UsernamePasswordAuthenticationToken(ACTOR_ID, null, List.of());
    }

    // ========================== CREATE ==========================

    @Nested
    @DisplayName("POST /user-income/me")
    class CreateUserIncome {

        @Test
        @DisplayName("returns CheckResponse with success=true on creation")
        void createIncomeSucceeds() {
            UserIncomeResponse incomeResponse = buildIncomeResponse();
            when(createUserIncomeUseCase.execute(eq(ACTOR_ID), any())).thenReturn(incomeResponse);

            CreateUserIncomeRequest req = validCreateIncomeRequest();
            CheckResponse<UserIncomeResponse> response = controller.create(mockAuth(), req);

            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getMessage()).isEqualTo("Created");
            assertThat(response.getData()).isEqualTo(incomeResponse);
            verify(createUserIncomeUseCase).execute(ACTOR_ID, req);
        }

        @Test
        @DisplayName("propagates exception from use case")
        void createIncomeThrowsWhenUseCaseFails() {
            when(createUserIncomeUseCase.execute(any(), any()))
                    .thenThrow(new IllegalStateException("User income already exists"));

            org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,
                    () -> controller.create(mockAuth(), validCreateIncomeRequest()));
        }

        @Test
        @DisplayName("CreateUserIncomeRequest: null netIncome fails validation")
        void nullNetIncomeFailsValidation() {
            CreateUserIncomeRequest req = validCreateIncomeRequest();
            req.setNetIncome(null);
            Set<ConstraintViolation<CreateUserIncomeRequest>> violations = validator.validate(req);
            assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("netIncome"));
        }

        @Test
        @DisplayName("CreateUserIncomeRequest: negative netIncome fails validation")
        void negativeNetIncomeFailsValidation() {
            CreateUserIncomeRequest req = validCreateIncomeRequest();
            req.setNetIncome(-1);
            Set<ConstraintViolation<CreateUserIncomeRequest>> violations = validator.validate(req);
            assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("netIncome"));
        }

        @Test
        @DisplayName("CreateUserIncomeRequest: null usableIncome fails validation")
        void nullUsableIncomeFailsValidation() {
            CreateUserIncomeRequest req = validCreateIncomeRequest();
            req.setUsableIncome(null);
            Set<ConstraintViolation<CreateUserIncomeRequest>> violations = validator.validate(req);
            assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("usableIncome"));
        }

        @Test
        @DisplayName("CreateUserIncomeRequest: negative usableIncome fails validation")
        void negativeUsableIncomeFailsValidation() {
            CreateUserIncomeRequest req = validCreateIncomeRequest();
            req.setUsableIncome(-100);
            Set<ConstraintViolation<CreateUserIncomeRequest>> violations = validator.validate(req);
            assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("usableIncome"));
        }

        @Test
        @DisplayName("CreateUserIncomeRequest: currency exceeding 10 chars fails validation")
        void currencyTooLongFailsValidation() {
            CreateUserIncomeRequest req = validCreateIncomeRequest();
            req.setCurrency("TOOLONGCURRENCYCODE");
            Set<ConstraintViolation<CreateUserIncomeRequest>> violations = validator.validate(req);
            assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("currency"));
        }

        @Test
        @DisplayName("CreateUserIncomeRequest: calculationNote exceeding 255 chars fails validation")
        void calculationNoteTooLongFailsValidation() {
            CreateUserIncomeRequest req = validCreateIncomeRequest();
            req.setCalculationNote("a".repeat(256));
            Set<ConstraintViolation<CreateUserIncomeRequest>> violations = validator.validate(req);
            assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("calculationNote"));
        }

        @Test
        @DisplayName("CreateUserIncomeRequest: zero values are allowed (inclusive)")
        void zeroValuesAreValid() {
            CreateUserIncomeRequest req = new CreateUserIncomeRequest();
            req.setNetIncome(0);
            req.setUsableIncome(0);
            Set<ConstraintViolation<CreateUserIncomeRequest>> violations = validator.validate(req);
            assertThat(violations).isEmpty();
        }

        @Test
        @DisplayName("CreateUserIncomeRequest: valid request passes all validations")
        void validRequestPassesAllValidations() {
            CreateUserIncomeRequest req = validCreateIncomeRequest();
            Set<ConstraintViolation<CreateUserIncomeRequest>> violations = validator.validate(req);
            assertThat(violations).isEmpty();
        }
    }

    // ========================== GET ==========================

    @Nested
    @DisplayName("GET /user-income/me")
    class GetUserIncome {

        @Test
        @DisplayName("returns CheckResponse with income data on success")
        void getIncomeSucceeds() {
            UserIncomeResponse incomeResponse = buildIncomeResponse();
            when(getUserIncomeUseCase.execute(ACTOR_ID)).thenReturn(incomeResponse);

            CheckResponse<UserIncomeResponse> response = controller.get(mockAuth());

            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getData()).isEqualTo(incomeResponse);
            assertThat(response.getMessage()).isEqualTo("OK");
        }

        @Test
        @DisplayName("propagates exception when income not found")
        void getIncomeThrowsWhenNotFound() {
            when(getUserIncomeUseCase.execute(any()))
                    .thenThrow(new RuntimeException("User income not found"));

            org.junit.jupiter.api.Assertions.assertThrows(RuntimeException.class,
                    () -> controller.get(mockAuth()));
        }
    }

    // ========================== UPDATE ==========================

    @Nested
    @DisplayName("PUT /user-income/me")
    class UpdateUserIncome {

        @Test
        @DisplayName("returns CheckResponse with updated income on success")
        void updateIncomeSucceeds() {
            UserIncomeResponse incomeResponse = buildIncomeResponse();
            incomeResponse.setNetIncome(5000);
            when(updateUserIncomeUseCase.execute(eq(ACTOR_ID), any())).thenReturn(incomeResponse);

            UpdateUserIncomeRequest req = new UpdateUserIncomeRequest();
            req.setNetIncome(5000);
            CheckResponse<UserIncomeResponse> response = controller.update(mockAuth(), req);

            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getMessage()).isEqualTo("Updated");
            assertThat(response.getData().getNetIncome()).isEqualTo(5000);
        }

        @Test
        @DisplayName("propagates exception from use case on update failure")
        void updateIncomeThrowsOnFailure() {
            when(updateUserIncomeUseCase.execute(any(), any()))
                    .thenThrow(new RuntimeException("User income not found"));

            org.junit.jupiter.api.Assertions.assertThrows(RuntimeException.class,
                    () -> controller.update(mockAuth(), new UpdateUserIncomeRequest()));
        }
    }

    // ========================== DELETE ==========================

    @Nested
    @DisplayName("DELETE /user-income/me")
    class DeleteUserIncome {

        @Test
        @DisplayName("returns CheckResponse with success=true on deletion")
        void deleteIncomeSucceeds() {
            doNothing().when(deleteUserIncomeUseCase).execute(ACTOR_ID);

            CheckResponse<Void> response = controller.delete(mockAuth());

            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getMessage()).isEqualTo("Deleted");
            verify(deleteUserIncomeUseCase).execute(ACTOR_ID);
        }

        @Test
        @DisplayName("propagates exception from use case when income not found")
        void deleteIncomeThrowsWhenNotFound() {
            doThrow(new RuntimeException("User income not found"))
                    .when(deleteUserIncomeUseCase).execute(any());

            org.junit.jupiter.api.Assertions.assertThrows(RuntimeException.class,
                    () -> controller.delete(mockAuth()));
        }
    }

    // ========================== HELPERS ==========================

    private CreateUserIncomeRequest validCreateIncomeRequest() {
        CreateUserIncomeRequest req = new CreateUserIncomeRequest();
        req.setNetIncome(3000);
        req.setUsableIncome(2000);
        req.setCurrency("VND");
        req.setCalculationNote("Monthly salary");
        req.setAutoInvestSurplus(false);
        return req;
    }

    private UserIncomeResponse buildIncomeResponse() {
        return UserIncomeResponse.builder()
                .userId(ACTOR_ID)
                .netIncome(3000)
                .usableIncome(2000)
                .safeSpending(1500)
                .currency("VND")
                .calculationNote("Monthly salary")
                .autoInvestSurplus(false)
                .createdAt(Instant.now())
                .build();
    }
}
