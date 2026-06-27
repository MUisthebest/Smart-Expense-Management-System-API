package com.example.smartmoney.presentation.controllers;

/*
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
    }

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
    }

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
    }

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
*/
