package com.example.smartmoney.presentation.controllers;

/*
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
    }

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
    }

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
    }

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
*/
