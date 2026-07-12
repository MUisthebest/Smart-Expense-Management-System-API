package com.example.smartmoney.application.services;

import com.example.smartmoney.domain.models.project.Project;
import com.example.smartmoney.domain.models.project.ProjectTracking;
import com.example.smartmoney.domain.models.project.enums.ProjectPriority;
import com.example.smartmoney.domain.models.project.enums.ProjectStatus;
import com.example.smartmoney.domain.models.project.enums.ProjectType;
import com.example.smartmoney.domain.models.project.valueobjects.Money;
import com.example.smartmoney.domain.models.project.valueobjects.ProjectId;
import com.example.smartmoney.domain.models.project.valueobjects.UserId;
import com.example.smartmoney.domain.repositories.BudgetRepository;
import com.example.smartmoney.domain.repositories.ProjectRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("WindfallAllocationService — active-project eligibility")
class WindfallAllocationServiceTest {

    private final BudgetRepository budgetRepository = mock(BudgetRepository.class);
    private final ProjectRepository projectRepository = mock(ProjectRepository.class);
    private final WindfallAllocationService service =
            new WindfallAllocationService(budgetRepository, projectRepository);

    private Project project(String name, ProjectStatus status, ProjectPriority priority) {
        return project(name, status, priority, BigDecimal.ZERO);
    }

    private Project project(String name, ProjectStatus status, ProjectPriority priority, BigDecimal moneySaved) {
        ProjectId projectId = ProjectId.newId();
        ProjectTracking tracking = new ProjectTracking(
                UUID.randomUUID(), projectId, moneySaved, 1,
                BigDecimal.valueOf(500_000), 12, BigDecimal.ZERO, 0,
                Instant.now(), Instant.now());
        return Project.rehydrate(
                projectId, UserId.of(UUID.randomUUID().toString()), ProjectType.PERSONAL,
                status, priority, name, "desc",
                new Money(BigDecimal.valueOf(20_000_000), "VND"),
                LocalDate.now().plusMonths(12), null, List.of(), tracking, Instant.now());
    }

    @Test
    @DisplayName("suggestSavingsBoost skips a COMPLETED project even though its deadline is still in the future")
    void skipsCompletedProject() {
        when(projectRepository.findAllByUser(any())).thenReturn(
                List.of(project("Mua nhà", ProjectStatus.COMPLETED, ProjectPriority.HIGH)));

        var result = service.suggestSavingsBoost(UUID.randomUUID(), 7, 2026, "trúng thưởng 5 triệu");

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("suggestSavingsBoost skips an ABANDONED project and falls through to an eligible one")
    void skipsAbandonedProjectFallsThroughToActive() {
        when(projectRepository.findAllByUser(any())).thenReturn(List.of(
                project("Du lịch", ProjectStatus.ABANDONED, ProjectPriority.HIGH),
                project("Mua xe", ProjectStatus.ACTIVE, ProjectPriority.MEDIUM)));

        var result = service.suggestSavingsBoost(UUID.randomUUID(), 7, 2026, "trúng thưởng 5 triệu");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getProjectName()).isEqualTo("Mua xe");
    }

    @Test
    @DisplayName("suggestSavingsBoost prefers HIGH priority over MEDIUM/LOW among active projects")
    void prioritizesHighOverMediumAndLow() {
        when(projectRepository.findAllByUser(any())).thenReturn(List.of(
                project("Thấp", ProjectStatus.ACTIVE, ProjectPriority.LOW),
                project("Trung bình", ProjectStatus.ACTIVE, ProjectPriority.MEDIUM),
                project("Cao", ProjectStatus.ACTIVE, ProjectPriority.HIGH)));

        var result = service.suggestSavingsBoost(UUID.randomUUID(), 7, 2026, "trúng thưởng 500 nghìn");

        assertThat(result).isNotEmpty();
        assertThat(result.get(0).getProjectName()).isEqualTo("Cao");
    }

    @Test
    @DisplayName("suggestAbandonedProjectFunds surfaces idle moneySaved from an ABANDONED project")
    void suggestsAbandonedProjectIdleFunds() {
        when(projectRepository.findAllByUser(any())).thenReturn(List.of(
                project("Du lịch", ProjectStatus.ABANDONED, ProjectPriority.MEDIUM, BigDecimal.valueOf(3_000_000)),
                project("Mua xe", ProjectStatus.ACTIVE, ProjectPriority.HIGH)));

        var result = service.suggestAbandonedProjectFunds(UUID.randomUUID(), "tổ chức tiệc sinh nhật 5 triệu");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getProjectName()).isEqualTo("Du lịch");
        assertThat(result.get(0).getSuggestedAddAmount()).isEqualTo(-3_000_000L);
        assertThat(result.get(0).getNewMoneySaved()).isEqualTo(0L);
    }

    @Test
    @DisplayName("suggestAbandonedProjectFunds ignores an ABANDONED project with nothing saved and any non-ABANDONED project")
    void ignoresAbandonedProjectWithNoSavedMoneyOrWrongStatus() {
        when(projectRepository.findAllByUser(any())).thenReturn(List.of(
                project("Trống", ProjectStatus.ABANDONED, ProjectPriority.LOW, BigDecimal.ZERO),
                project("Hoàn tất", ProjectStatus.COMPLETED, ProjectPriority.LOW, BigDecimal.valueOf(1_000_000)),
                project("Đang chạy", ProjectStatus.ACTIVE, ProjectPriority.LOW, BigDecimal.valueOf(1_000_000))));

        var result = service.suggestAbandonedProjectFunds(UUID.randomUUID(), "mua laptop mới");

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("suggestProjectDeferral skips deferring an ACTIVE project once extraCoverage already covers the expense")
    void extraCoverageAvoidsUnnecessaryDeferral() {
        when(projectRepository.findAllByUser(any())).thenReturn(
                List.of(project("Mua xe", ProjectStatus.ACTIVE, ProjectPriority.HIGH)));
        when(budgetRepository.findByUserIdMonthYear(any(), anyInt(), anyInt())).thenReturn(List.of());

        var withoutExtra = service.suggestProjectDeferral(UUID.randomUUID(), 7, 2026, "chi phí đột xuất 3 triệu");
        assertThat(withoutExtra).isNotEmpty();

        var withExtra = service.suggestProjectDeferral(
                UUID.randomUUID(), 7, 2026, "chi phí đột xuất 3 triệu", BigDecimal.valueOf(3_000_000));
        assertThat(withExtra).isEmpty();
    }
}
