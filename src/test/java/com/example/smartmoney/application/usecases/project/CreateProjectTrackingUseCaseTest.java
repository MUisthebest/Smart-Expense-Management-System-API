package com.example.smartmoney.application.usecases.project;

import com.example.smartmoney.application.dtos.requests.project.CreateProjectTrackingRequest;
import com.example.smartmoney.application.dtos.responses.project.ProjectTrackingResponse;
import com.example.smartmoney.domain.models.project.Project;
import com.example.smartmoney.domain.models.project.ProjectTracking;
import com.example.smartmoney.domain.models.project.enums.ProjectPriority;
import com.example.smartmoney.domain.models.project.enums.ProjectType;
import com.example.smartmoney.domain.models.project.valueobjects.Money;
import com.example.smartmoney.domain.models.project.valueobjects.ProjectId;
import com.example.smartmoney.domain.models.project.valueobjects.UserId;
import com.example.smartmoney.domain.repositories.ProjectRepository;
import com.example.smartmoney.domain.repositories.ProjectTrackingRepository;
import com.example.smartmoney.domain.repositories.UserIncomeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("CreateProjectTrackingUseCase")
class CreateProjectTrackingUseCaseTest {

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private ProjectTrackingRepository projectTrackingRepository;

    @Mock
    private UserIncomeRepository userIncomeRepository;

    private CreateProjectTrackingUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new CreateProjectTrackingUseCase(projectRepository, projectTrackingRepository, userIncomeRepository);
    }

    @Test
    @DisplayName("Calculates monthlySaving from targetAmount and months to deadline")
    void calculatesMonthlySavingFromProjectDeadline() {
        String actorId = "f5bbeb40-0a56-4a6b-a9cf-5a547ce38a7b";
        String projectId = "1e04139c-8dfc-4e03-a127-a8684f7f7ab1";

        LocalDate deadline = LocalDate.now().plusMonths(2);
        Project project = Project.createNew(
                UserId.of(actorId),
                ProjectType.PERSONAL,
                ProjectPriority.MEDIUM,
                "Laptop",
                null,
                new Money(new BigDecimal("1000.00"), "VND"),
                deadline
        );

        CreateProjectTrackingRequest req = new CreateProjectTrackingRequest();
        req.setMoneySaved(new BigDecimal("100.00"));
        req.setCurrentMonth(1);
        req.setMonthLeft(2);
        req.setMoneyOwed(new BigDecimal("900.00"));

        when(projectRepository.findById(ProjectId.of(projectId))).thenReturn(Optional.of(project));
        when(projectTrackingRepository.findByProjectId(ProjectId.of(projectId))).thenReturn(Optional.empty());
        when(projectTrackingRepository.save(any(ProjectTracking.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ProjectTrackingResponse response = useCase.execute(actorId, projectId, req);

        // LocalDate.now() + 2 months => 3-month divisor under the current formula (+1 inclusive month)
        assertEquals(new BigDecimal("333.34"), response.getMonthlySaving());
    }
}
