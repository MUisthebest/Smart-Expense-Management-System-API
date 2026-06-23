package com.example.smartmoney.presentation.controllers;

import com.example.smartmoney.application.dtos.requests.userfinancialprofile.CreateUserFinancialProfileRequest;
import com.example.smartmoney.application.dtos.requests.userfinancialprofile.UpdateUserFinancialProfileRequest;
import com.example.smartmoney.application.dtos.responses.CheckResponse;
import com.example.smartmoney.application.dtos.responses.UserFinancialProfileResponse;
import com.example.smartmoney.application.usecases.userprofile.*;
import com.example.smartmoney.domain.models.userprofile.enums.*;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserFinancialProfileController")
class UserFinancialProfileControllerTest {

    @Mock private CreateUserFinancialProfileUseCase createUseCase;
    @Mock private UpdateUserFinancialProfileUseCase updateUseCase;
    @Mock private GetUserFinancialProfileUseCase getUseCase;
    @Mock private DeleteUserFinancialProfileUseCase deleteUseCase;

    private UserFinancialProfileController controller;
    private Validator validator;

    private static final String ACTOR_ID = "77777777-7777-7777-7777-777777777777";
    private static final UUID PROFILE_ID = UUID.fromString("88888888-8888-8888-8888-888888888888");

    @BeforeEach
    void setUp() {
        controller = new UserFinancialProfileController(createUseCase, updateUseCase, getUseCase, deleteUseCase);

        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    private Authentication mockAuth() {
        return new UsernamePasswordAuthenticationToken(ACTOR_ID, null, List.of());
    }

    // ========================== CREATE ==========================

    @Nested
    @DisplayName("POST /user-financial-profile")
    class CreateProfile {

        @Test
        @DisplayName("returns CheckResponse with success=true on creation")
        void createProfileSucceeds() {
            UserFinancialProfileResponse profileResponse = buildProfileResponse();
            when(createUseCase.execute(eq(ACTOR_ID), any())).thenReturn(profileResponse);

            CheckResponse<UserFinancialProfileResponse> response =
                    controller.create(mockAuth(), validCreateRequest());

            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getMessage()).isEqualTo("Created");
            assertThat(response.getData()).isEqualTo(profileResponse);
            verify(createUseCase).execute(eq(ACTOR_ID), any());
        }

        @Test
        @DisplayName("propagates exception when profile already exists (409 Conflict scenario)")
        void createProfileThrowsWhenAlreadyExists() {
            when(createUseCase.execute(any(), any()))
                    .thenThrow(new RuntimeException("User financial profile already exists"));

            org.junit.jupiter.api.Assertions.assertThrows(RuntimeException.class,
                    () -> controller.create(mockAuth(), validCreateRequest()));
        }

        @Test
        @DisplayName("CreateUserFinancialProfileRequest: null role fails validation")
        void nullRoleFailsValidation() {
            CreateUserFinancialProfileRequest req = validCreateRequest();
            req.setRole(null);
            Set<ConstraintViolation<CreateUserFinancialProfileRequest>> violations = validator.validate(req);
            assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("role"));
        }

        @Test
        @DisplayName("CreateUserFinancialProfileRequest: null livingStatus fails validation")
        void nullLivingStatusFailsValidation() {
            CreateUserFinancialProfileRequest req = validCreateRequest();
            req.setLivingStatus(null);
            Set<ConstraintViolation<CreateUserFinancialProfileRequest>> violations = validator.validate(req);
            assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("livingStatus"));
        }

        @Test
        @DisplayName("CreateUserFinancialProfileRequest: null incomeLevel fails validation")
        void nullIncomeLevelFailsValidation() {
            CreateUserFinancialProfileRequest req = validCreateRequest();
            req.setIncomeLevel(null);
            Set<ConstraintViolation<CreateUserFinancialProfileRequest>> violations = validator.validate(req);
            assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("incomeLevel"));
        }

        @Test
        @DisplayName("CreateUserFinancialProfileRequest: null transportMode fails validation")
        void nullTransportModeFailsValidation() {
            CreateUserFinancialProfileRequest req = validCreateRequest();
            req.setTransportMode(null);
            Set<ConstraintViolation<CreateUserFinancialProfileRequest>> violations = validator.validate(req);
            assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("transportMode"));
        }

        @Test
        @DisplayName("CreateUserFinancialProfileRequest: null spendingStyle fails validation")
        void nullSpendingStyleFailsValidation() {
            CreateUserFinancialProfileRequest req = validCreateRequest();
            req.setSpendingStyle(null);
            Set<ConstraintViolation<CreateUserFinancialProfileRequest>> violations = validator.validate(req);
            assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("spendingStyle"));
        }

        @Test
        @DisplayName("CreateUserFinancialProfileRequest: null workStyle fails validation")
        void nullWorkStyleFailsValidation() {
            CreateUserFinancialProfileRequest req = validCreateRequest();
            req.setWorkStyle(null);
            Set<ConstraintViolation<CreateUserFinancialProfileRequest>> violations = validator.validate(req);
            assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("workStyle"));
        }

        @Test
        @DisplayName("CreateUserFinancialProfileRequest: null familyStatus fails validation")
        void nullFamilyStatusFailsValidation() {
            CreateUserFinancialProfileRequest req = validCreateRequest();
            req.setFamilyStatus(null);
            Set<ConstraintViolation<CreateUserFinancialProfileRequest>> violations = validator.validate(req);
            assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("familyStatus"));
        }

        @Test
        @DisplayName("CreateUserFinancialProfileRequest: null studyIntensity fails validation")
        void nullStudyIntensityFailsValidation() {
            CreateUserFinancialProfileRequest req = validCreateRequest();
            req.setStudyIntensity(null);
            Set<ConstraintViolation<CreateUserFinancialProfileRequest>> violations = validator.validate(req);
            assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("studyIntensity"));
        }

        @Test
        @DisplayName("CreateUserFinancialProfileRequest: null healthNeed fails validation")
        void nullHealthNeedFailsValidation() {
            CreateUserFinancialProfileRequest req = validCreateRequest();
            req.setHealthNeed(null);
            Set<ConstraintViolation<CreateUserFinancialProfileRequest>> violations = validator.validate(req);
            assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("healthNeed"));
        }

        @Test
        @DisplayName("CreateUserFinancialProfileRequest: fully valid request passes all validations")
        void validRequestPassesAllValidations() {
            CreateUserFinancialProfileRequest req = validCreateRequest();
            Set<ConstraintViolation<CreateUserFinancialProfileRequest>> violations = validator.validate(req);
            assertThat(violations).isEmpty();
        }
    }

    // ========================== GET ==========================

    @Nested
    @DisplayName("GET /user-financial-profile")
    class GetProfile {

        @Test
        @DisplayName("returns success=true with profile data when profile exists")
        void getProfileSucceeds() {
            UserFinancialProfileResponse profileResponse = buildProfileResponse();
            when(getUseCase.execute(ACTOR_ID)).thenReturn(profileResponse);

            CheckResponse<UserFinancialProfileResponse> response = controller.get(mockAuth());

            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getMessage()).isEqualTo("Success");
            assertThat(response.getData()).isEqualTo(profileResponse);
        }

        @Test
        @DisplayName("returns success=false when profile does not exist (use case returns null)")
        void getProfileReturnsFalseWhenNotFound() {
            when(getUseCase.execute(ACTOR_ID)).thenReturn(null);

            CheckResponse<UserFinancialProfileResponse> response = controller.get(mockAuth());

            assertThat(response.isSuccess()).isFalse();
            assertThat(response.getMessage()).isEqualTo("Failed");
            assertThat(response.getData()).isNull();
        }
    }

    // ========================== UPDATE ==========================

    @Nested
    @DisplayName("PUT /user-financial-profile")
    class UpdateProfile {

        @Test
        @DisplayName("returns CheckResponse with success=true on update")
        void updateProfileSucceeds() {
            UserFinancialProfileResponse profileResponse = buildProfileResponse();
            when(updateUseCase.execute(eq(ACTOR_ID), any())).thenReturn(profileResponse);

            UpdateUserFinancialProfileRequest req = validUpdateRequest();
            CheckResponse<UserFinancialProfileResponse> response = controller.update(mockAuth(), req);

            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getMessage()).isEqualTo("Updated");
            assertThat(response.getData()).isEqualTo(profileResponse);
        }

        @Test
        @DisplayName("propagates exception when profile not found during update")
        void updateProfileThrowsWhenNotFound() {
            when(updateUseCase.execute(any(), any()))
                    .thenThrow(new RuntimeException("Profile not found"));

            org.junit.jupiter.api.Assertions.assertThrows(RuntimeException.class,
                    () -> controller.update(mockAuth(), validUpdateRequest()));
        }
    }

    // ========================== DELETE ==========================

    @Nested
    @DisplayName("DELETE /user-financial-profile")
    class DeleteProfile {

        @Test
        @DisplayName("returns CheckResponse with success=true on deletion")
        void deleteProfileSucceeds() {
            doNothing().when(deleteUseCase).execute(ACTOR_ID);

            CheckResponse<Void> response = controller.delete(mockAuth());

            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getMessage()).isEqualTo("Deleted");
            verify(deleteUseCase).execute(ACTOR_ID);
        }

        @Test
        @DisplayName("propagates exception when profile not found during delete")
        void deleteProfileThrowsWhenNotFound() {
            doThrow(new RuntimeException("Profile not found"))
                    .when(deleteUseCase).execute(any());

            org.junit.jupiter.api.Assertions.assertThrows(RuntimeException.class,
                    () -> controller.delete(mockAuth()));
        }
    }

    // ========================== HELPERS ==========================

    private CreateUserFinancialProfileRequest validCreateRequest() {
        CreateUserFinancialProfileRequest req = new CreateUserFinancialProfileRequest();
        req.setRole(UserRole.STUDENT);
        req.setLivingStatus(LivingStatus.DORM);
        req.setIncomeLevel(IncomeLevel.LOW);
        req.setTransportMode(TransportMode.BUS);
        req.setSpendingStyle(SpendingStyle.FRUGAL);
        req.setWorkStyle(WorkStyle.REMOTE);
        req.setFamilyStatus(FamilyStatus.SINGLE);
        req.setStudyIntensity(StudyIntensity.COURSE_HEAVY);
        req.setHealthNeed(HealthNeed.LOW);
        return req;
    }

    private UpdateUserFinancialProfileRequest validUpdateRequest() {
        UpdateUserFinancialProfileRequest req = new UpdateUserFinancialProfileRequest();
        req.setRole(UserRole.OFFICE_WORKER);
        req.setLivingStatus(LivingStatus.RENT_ROOM);
        req.setIncomeLevel(IncomeLevel.MEDIUM);
        req.setTransportMode(TransportMode.MOTORBIKE);
        req.setSpendingStyle(SpendingStyle.BALANCED);
        req.setWorkStyle(WorkStyle.ONSITE);
        req.setFamilyStatus(FamilyStatus.SINGLE);
        req.setStudyIntensity(StudyIntensity.NORMAL);
        req.setHealthNeed(HealthNeed.NORMAL);
        return req;
    }

    private UserFinancialProfileResponse buildProfileResponse() {
        return UserFinancialProfileResponse.builder()
                .id(PROFILE_ID)
                .userId(UUID.fromString(ACTOR_ID))
                .role(UserRole.STUDENT.name())
                .livingStatus(LivingStatus.DORM.name())
                .incomeLevel(IncomeLevel.LOW.name())
                .transportMode(TransportMode.BUS.name())
                .spendingStyle(SpendingStyle.FRUGAL.name())
                .workStyle(WorkStyle.REMOTE.name())
                .familyStatus(FamilyStatus.SINGLE.name())
                .studyIntensity(StudyIntensity.COURSE_HEAVY.name())
                .healthNeed(HealthNeed.LOW.name())
                .createdAt(Instant.now())
                .build();
    }
}
