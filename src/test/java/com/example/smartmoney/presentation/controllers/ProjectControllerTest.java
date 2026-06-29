package com.example.smartmoney.presentation.controllers;

import com.example.smartmoney.application.dtos.requests.project.*;
import com.example.smartmoney.application.dtos.responses.CheckResponse;
import com.example.smartmoney.application.dtos.responses.project.*;
import com.example.smartmoney.application.usecases.project.*;
import com.example.smartmoney.domain.models.project.enums.*;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ProjectController")
class ProjectControllerTest {

    @Mock private CreateProjectUseCase createProjectUseCase;
    @Mock private UpdateProjectUseCase updateProjectUseCase;
    @Mock private GetProjectDetailUseCase getProjectDetailUseCase;
    @Mock private ListMyProjectsUseCase listMyProjectsUseCase;
    @Mock private DeleteProjectUseCase deleteProjectUseCase;
    @Mock private CreateProjectTrackingUseCase createProjectTrackingUseCase;
    @Mock private GetProjectTrackingUseCase getProjectTrackingUseCase;
    @Mock private DeleteProjectTrackingUseCase deleteProjectTrackingUseCase;
    @Mock private GetProjectHistoryUseCase getProjectHistoryUseCase;
    @Mock private ProjectBudgetAdvisorUseCase projectBudgetAdvisorUseCase;

    @InjectMocks
    private ProjectController controller;

    private Validator validator;

    private static final String ACTOR_ID  = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa";
    private static final String PROJECT_ID = "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb";
    private static final String MEMBER_ID  = "cccccccc-cccc-cccc-cccc-cccccccccccc";

    @BeforeEach
    void setUp() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    private Authentication mockAuth() {
        return new UsernamePasswordAuthenticationToken(ACTOR_ID, null, List.of());
    }

    // ========================== CREATE ==========================

    @Nested
    @DisplayName("POST /projects")
    class CreateProject {

        @Test
        @DisplayName("returns CheckResponse with success=true and ProjectDetailResponse on creation")
        void createProjectSucceeds() {
            ProjectDetailResponse detail = buildProjectDetailResponse();
            when(createProjectUseCase.execute(eq(ACTOR_ID), any())).thenReturn(detail);

            CheckResponse<ProjectDetailResponse> response =
                    controller.create(mockAuth(), validCreateProjectRequest());

            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getMessage()).isEqualTo("Created");
            assertThat(response.getData()).isEqualTo(detail);
            verify(createProjectUseCase).execute(eq(ACTOR_ID), any());
        }

        @Test
        @DisplayName("propagates exception when user income is missing")
        void createProjectThrowsWhenIncomeMissing() {
            when(createProjectUseCase.execute(any(), any()))
                    .thenThrow(new RuntimeException("User income profile is required"));

            assertThrows(RuntimeException.class,
                    () -> controller.create(mockAuth(), validCreateProjectRequest()));
        }

        @Test
        @DisplayName("propagates exception when max active projects reached")
        void createProjectThrowsWhenMaxActiveReached() {
            when(createProjectUseCase.execute(any(), any()))
                    .thenThrow(new RuntimeException("Maximum active projects limit reached"));

            assertThrows(RuntimeException.class,
                    () -> controller.create(mockAuth(), validCreateProjectRequest()));
        }

        @Test
        @DisplayName("CreateProjectRequest: blank name fails validation")
        void blankNameFailsValidation() {
            CreateProjectRequest req = validCreateProjectRequest();
            req.setName("");
            Set<ConstraintViolation<CreateProjectRequest>> violations = validator.validate(req);
            assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("name"));
        }

        @Test
        @DisplayName("CreateProjectRequest: name exceeding 120 chars fails validation")
        void nameTooLongFailsValidation() {
            CreateProjectRequest req = validCreateProjectRequest();
            req.setName("a".repeat(121));
            Set<ConstraintViolation<CreateProjectRequest>> violations = validator.validate(req);
            assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("name"));
        }

        @Test
        @DisplayName("CreateProjectRequest: null targetAmount fails validation")
        void nullTargetAmountFailsValidation() {
            CreateProjectRequest req = validCreateProjectRequest();
            req.setTargetAmount(null);
            Set<ConstraintViolation<CreateProjectRequest>> violations = validator.validate(req);
            assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("targetAmount"));
        }

        @Test
        @DisplayName("CreateProjectRequest: zero targetAmount fails validation")
        void zeroTargetAmountFailsValidation() {
            CreateProjectRequest req = validCreateProjectRequest();
            req.setTargetAmount(BigDecimal.ZERO);
            Set<ConstraintViolation<CreateProjectRequest>> violations = validator.validate(req);
            assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("targetAmount"));
        }

        @Test
        @DisplayName("CreateProjectRequest: blank currency fails validation")
        void blankCurrencyFailsValidation() {
            CreateProjectRequest req = validCreateProjectRequest();
            req.setCurrency("");
            Set<ConstraintViolation<CreateProjectRequest>> violations = validator.validate(req);
            assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("currency"));
        }

        @Test
        @DisplayName("CreateProjectRequest: null deadline fails validation")
        void nullDeadlineFailsValidation() {
            CreateProjectRequest req = validCreateProjectRequest();
            req.setDeadline(null);
            Set<ConstraintViolation<CreateProjectRequest>> violations = validator.validate(req);
            assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("deadline"));
        }

        @Test
        @DisplayName("CreateProjectRequest: description exceeding 500 chars fails validation")
        void descriptionTooLongFailsValidation() {
            CreateProjectRequest req = validCreateProjectRequest();
            req.setDescription("a".repeat(501));
            Set<ConstraintViolation<CreateProjectRequest>> violations = validator.validate(req);
            assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("description"));
        }

        @Test
        @DisplayName("CreateProjectRequest: valid request passes all validations")
        void validRequestPassesAllValidations() {
            CreateProjectRequest req = validCreateProjectRequest();
            Set<ConstraintViolation<CreateProjectRequest>> violations = validator.validate(req);
            assertThat(violations).isEmpty();
        }
    }

    // ========================== LIST ==========================

    @Nested
    @DisplayName("GET /projects")
    class ListProjects {

        @Test
        @DisplayName("returns CheckResponse with list of projects for current user")
        void listProjectsSucceeds() {
            List<ProjectListItemResponse> items = List.of(
                    buildProjectListItemResponse(),
                    buildProjectListItemResponse()
            );
            when(listMyProjectsUseCase.execute(eq(ACTOR_ID), any())).thenReturn(items);

            CheckResponse<List<ProjectListItemResponse>> response =
                    controller.listMine(mockAuth(), null, null, null, null);

            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getData()).hasSize(2);
        }

        @Test
        @DisplayName("returns filtered list when search, type, status, priority provided")
        void listProjectsWithFilters() {
            when(listMyProjectsUseCase.execute(eq(ACTOR_ID), any())).thenReturn(List.of());

            CheckResponse<List<ProjectListItemResponse>> response =
                    controller.listMine(mockAuth(), "Vacation", ProjectType.PERSONAL,
                            ProjectStatus.ACTIVE, ProjectPriority.HIGH);

            assertThat(response.isSuccess()).isTrue();
        }

        @Test
        @DisplayName("returns empty list when user has no projects")
        void listProjectsReturnsEmpty() {
            when(listMyProjectsUseCase.execute(eq(ACTOR_ID), any())).thenReturn(List.of());

            CheckResponse<List<ProjectListItemResponse>> response =
                    controller.listMine(mockAuth(), null, null, null, null);

            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getData()).isEmpty();
        }
    }

    // ========================== DETAIL ==========================

    @Nested
    @DisplayName("GET /projects/{projectId}")
    class GetProjectDetail {

        @Test
        @DisplayName("returns CheckResponse with project detail on success")
        void getProjectDetailSucceeds() {
            ProjectDetailResponse detail = buildProjectDetailResponse();
            when(getProjectDetailUseCase.execute(ACTOR_ID, PROJECT_ID)).thenReturn(detail);

            CheckResponse<ProjectDetailResponse> response = controller.detail(mockAuth(), PROJECT_ID);

            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getData().getProjectId()).isEqualTo(PROJECT_ID);
        }

        @Test
        @DisplayName("propagates exception when project not found")
        void getProjectDetailThrowsWhenNotFound() {
            when(getProjectDetailUseCase.execute(any(), any()))
                    .thenThrow(new RuntimeException("Project not found"));

            assertThrows(RuntimeException.class,
                    () -> controller.detail(mockAuth(), PROJECT_ID));
        }

        @Test
        @DisplayName("propagates exception when user is not a member")
        void getProjectDetailThrowsWhenNotMember() {
            when(getProjectDetailUseCase.execute(any(), any()))
                    .thenThrow(new RuntimeException("Access denied: not a project member"));

            assertThrows(RuntimeException.class,
                    () -> controller.detail(mockAuth(), PROJECT_ID));
        }
    }

    // ========================== UPDATE ==========================

    @Nested
    @DisplayName("PUT /projects/{projectId}")
    class UpdateProject {

        @Test
        @DisplayName("returns CheckResponse with updated project on success")
        void updateProjectSucceeds() {
            ProjectDetailResponse detail = buildProjectDetailResponse();
            when(updateProjectUseCase.execute(eq(ACTOR_ID), eq(PROJECT_ID), any()))
                    .thenReturn(detail);

            UpdateProjectRequest req = new UpdateProjectRequest();
            req.setName("Updated Name");

            CheckResponse<ProjectDetailResponse> response =
                    controller.update(mockAuth(), PROJECT_ID, req);

            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getMessage()).isEqualTo("Updated");
        }

        @Test
        @DisplayName("propagates exception when user is not the owner")
        void updateProjectThrowsWhenNotOwner() {
            when(updateProjectUseCase.execute(any(), any(), any()))
                    .thenThrow(new RuntimeException("Only the project owner can update the project"));

            assertThrows(RuntimeException.class,
                    () -> controller.update(mockAuth(), PROJECT_ID, new UpdateProjectRequest()));
        }
    }

    // ========================== DELETE ==========================

    @Nested
    @DisplayName("DELETE /projects/{projectId}")
    class DeleteProject {

        @Test
        @DisplayName("returns CheckResponse with success=true on deletion")
        void deleteProjectSucceeds() {
            doNothing().when(deleteProjectUseCase).execute(ACTOR_ID, PROJECT_ID);

            CheckResponse<Void> response = controller.delete(mockAuth(), PROJECT_ID);

            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getMessage()).isEqualTo("Deleted");
            verify(deleteProjectUseCase).execute(ACTOR_ID, PROJECT_ID);
        }

        @Test
        @DisplayName("propagates exception when not the owner")
        void deleteProjectThrowsWhenNotOwner() {
            doThrow(new RuntimeException("Only the owner can delete this project"))
                    .when(deleteProjectUseCase).execute(any(), any());

            assertThrows(RuntimeException.class,
                    () -> controller.delete(mockAuth(), PROJECT_ID));
        }
    }

    // ========================== TRACKING ==========================

    @Nested
    @DisplayName("POST /projects/{projectId}/tracking")
    class CreateTracking {

        @Test
        @DisplayName("returns CheckResponse with tracking data on success")
        void createTrackingSucceeds() {
            ProjectTrackingResponse trackingResponse = ProjectTrackingResponse.builder()
                    .id("t1").projectId(PROJECT_ID).currentMonth(3).monthLeft(9).build();
            when(createProjectTrackingUseCase.execute(eq(ACTOR_ID), eq(PROJECT_ID), any()))
                    .thenReturn(trackingResponse);

            CreateProjectTrackingRequest req = validTrackingRequest();
            CheckResponse<ProjectTrackingResponse> response =
                    controller.createTracking(mockAuth(), PROJECT_ID, req);

            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getMessage()).isEqualTo("Created");
        }

        @Test
        @DisplayName("CreateProjectTrackingRequest: null moneySaved fails validation")
        void nullMoneySavedFailsValidation() {
            CreateProjectTrackingRequest req = validTrackingRequest();
            req.setMoneySaved(null);
            Set<ConstraintViolation<CreateProjectTrackingRequest>> violations = validator.validate(req);
            assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("moneySaved"));
        }

        @Test
        @DisplayName("CreateProjectTrackingRequest: null currentMonth fails validation")
        void nullCurrentMonthFailsValidation() {
            CreateProjectTrackingRequest req = validTrackingRequest();
            req.setCurrentMonth(null);
            Set<ConstraintViolation<CreateProjectTrackingRequest>> violations = validator.validate(req);
            assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("currentMonth"));
        }

        @Test
        @DisplayName("CreateProjectTrackingRequest: currentMonth below 1 fails validation")
        void currentMonthBelowMinFailsValidation() {
            CreateProjectTrackingRequest req = validTrackingRequest();
            req.setCurrentMonth(0);
            Set<ConstraintViolation<CreateProjectTrackingRequest>> violations = validator.validate(req);
            assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("currentMonth"));
        }

        @Test
        @DisplayName("CreateProjectTrackingRequest: valid request passes all validations")
        void validRequestPassesAllValidations() {
            Set<ConstraintViolation<CreateProjectTrackingRequest>> violations =
                    validator.validate(validTrackingRequest());
            assertThat(violations).isEmpty();
        }
    }

    @Nested
    @DisplayName("GET /projects/{projectId}/tracking")
    class GetTracking {

        @Test
        @DisplayName("returns tracking data on success")
        void getTrackingSucceeds() {
            ProjectTrackingResponse trackingResponse = ProjectTrackingResponse.builder()
                    .id("t1").projectId(PROJECT_ID).build();
            when(getProjectTrackingUseCase.execute(ACTOR_ID, PROJECT_ID)).thenReturn(trackingResponse);

            CheckResponse<ProjectTrackingResponse> response =
                    controller.getTracking(mockAuth(), PROJECT_ID);

            assertThat(response.isSuccess()).isTrue();
        }

        @Test
        @DisplayName("propagates exception when tracking not found")
        void getTrackingThrowsWhenNotFound() {
            when(getProjectTrackingUseCase.execute(any(), any()))
                    .thenThrow(new RuntimeException("Project tracking not found"));

            assertThrows(RuntimeException.class,
                    () -> controller.getTracking(mockAuth(), PROJECT_ID));
        }
    }

    @Nested
    @DisplayName("DELETE /projects/{projectId}/tracking")
    class DeleteTracking {

        @Test
        @DisplayName("returns success=true on deletion")
        void deleteTrackingSucceeds() {
            doNothing().when(deleteProjectTrackingUseCase).execute(ACTOR_ID, PROJECT_ID);

            CheckResponse<Void> response = controller.deleteTracking(mockAuth(), PROJECT_ID);

            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getMessage()).isEqualTo("Deleted");
            verify(deleteProjectTrackingUseCase).execute(ACTOR_ID, PROJECT_ID);
        }
    }

    // ========================== HISTORY ==========================

    @Nested
    @DisplayName("GET /projects/{projectId}/history")
    class ProjectHistory {

        @Test
        @DisplayName("returns list of history snapshots on success")
        void getHistorySucceeds() {
            List<ProjectHistoryResponse> historyList = List.of(
                    ProjectHistoryResponse.builder().year(2024).month(6).build());
            when(getProjectHistoryUseCase.execute(ACTOR_ID, PROJECT_ID)).thenReturn(historyList);

            CheckResponse<List<ProjectHistoryResponse>> response =
                    controller.getHistory(mockAuth(), PROJECT_ID);

            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getData()).hasSize(1);
        }
    }

    // ========================== AI BUDGET ADVISOR ==========================

    @Nested
    @DisplayName("POST /projects/project-advisor")
    class ProjectAdvisor {

        @Test
        @DisplayName("returns advisor response with monthly saving plan")
        void projectAdvisorSucceeds() {
            ProjectBudgetAdvisorResponse advisorResponse = ProjectBudgetAdvisorResponse.builder()
                    .monthlySaving(new BigDecimal("833.33"))
                    .numberOfMonths(12)
                    .build();
            when(projectBudgetAdvisorUseCase.execute(eq(ACTOR_ID), any()))
                    .thenReturn(advisorResponse);

            ProjectBudgetAdvisorRequest req = validAdvisorRequest();

            CheckResponse<ProjectBudgetAdvisorResponse> response =
                    controller.projectAdvisor(mockAuth(), req);

            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getData().getMonthlySaving())
                    .isEqualByComparingTo("833.33");
        }

        @Test
        @DisplayName("propagates exception when user income not set up")
        void projectAdvisorThrowsWhenNoIncome() {
            when(projectBudgetAdvisorUseCase.execute(any(), any()))
                    .thenThrow(new RuntimeException("User income profile is required for budget advice"));

            assertThrows(RuntimeException.class,
                    () -> controller.projectAdvisor(mockAuth(), validAdvisorRequest()));
        }
    }

    // ========================== HELPERS ==========================

    private CreateProjectRequest validCreateProjectRequest() {
        CreateProjectRequest req = new CreateProjectRequest();
        req.setName("Vacation Fund");
        req.setDescription("Save for annual vacation");
        req.setPriority(ProjectPriority.MEDIUM);
        req.setTargetAmount(new BigDecimal("5000.00"));
        req.setCurrency("VND");
        req.setDeadline(LocalDate.now().plusMonths(6));
        return req;
    }

    private ProjectDetailResponse buildProjectDetailResponse() {
        return ProjectDetailResponse.builder()
                .projectId(PROJECT_ID)
                .ownerId(ACTOR_ID)
                .type(ProjectType.PERSONAL)
                .name("Vacation Fund")
                .targetAmount(new BigDecimal("5000.00"))
                .currency("VND")
                .totalContributed(BigDecimal.ZERO)
                .remaining(new BigDecimal("5000.00"))
                .progressPercent(BigDecimal.ZERO)
                .deadline(LocalDate.now().plusMonths(6))
                .status("ONGOING")
                .monthsLeft(6)
                .priority(ProjectPriority.MEDIUM)
                .createdAt(Instant.now())
                .build();
    }

    private ProjectListItemResponse buildProjectListItemResponse() {
        return ProjectListItemResponse.builder()
                .projectId(PROJECT_ID)
                .name("Vacation Fund")
                .type(ProjectType.PERSONAL)
                .priority(ProjectPriority.MEDIUM)
                .status(ProjectStatus.ACTIVE)
                .targetAmount(new BigDecimal("5000.00"))
                .currency("VND")
                .totalContributed(BigDecimal.ZERO)
                .remaining(new BigDecimal("5000.00"))
                .progressPercent(BigDecimal.ZERO)
                .deadline(LocalDate.now().plusMonths(6))
                .monthsLeft(6)
                .build();
    }

    private ProjectBudgetAdvisorRequest validAdvisorRequest() {
        ProjectBudgetAdvisorRequest req = new ProjectBudgetAdvisorRequest();
        req.setName("Vacation Fund");
        req.setType(ProjectType.PERSONAL);
        req.setPriority(ProjectPriority.MEDIUM);
        req.setTargetAmount(new BigDecimal("10000.00"));
        req.setCurrency("VND");
        req.setDeadline(LocalDate.now().plusMonths(12));
        return req;
    }

    private CreateProjectTrackingRequest validTrackingRequest() {
        CreateProjectTrackingRequest req = new CreateProjectTrackingRequest();
        req.setMoneySaved(new BigDecimal("1000.00"));
        req.setCurrentMonth(3);
        req.setMonthLeft(9);
        req.setMoneyOwed(BigDecimal.ZERO);
        return req;
    }
}