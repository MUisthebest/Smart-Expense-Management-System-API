package com.example.smartmoney.application.dtos.responses.project;

import com.example.smartmoney.domain.models.project.Project;
import com.example.smartmoney.domain.models.project.ProjectTracking;
import com.example.smartmoney.domain.models.project.enums.ProjectPriority;
import com.example.smartmoney.domain.models.project.enums.ProjectStatus;
import com.example.smartmoney.domain.models.project.enums.ProjectType;
import com.example.smartmoney.domain.models.project.valueobjects.Money;
import com.example.smartmoney.domain.models.project.valueobjects.ProjectId;
import com.example.smartmoney.domain.models.project.valueobjects.UserId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ProjectTrackingResponse.fromDomain — UI enrichment")
class ProjectTrackingResponseTest {

    private static final int MAX_FROZEN = 3;

    private Project project(ProjectStatus status, LocalDate deadline) {
        ProjectId id = ProjectId.newId();
        return Project.rehydrate(id, new UserId(UUID.randomUUID()), ProjectType.PERSONAL, status,
                ProjectPriority.MEDIUM, "Trip", null, new Money(new BigDecimal("3000"), "USD"),
                deadline, null, List.of(), null, Instant.now());
    }

    private ProjectTracking tracking(int monthLeft, BigDecimal moneyOwed, BigDecimal monthlySaving) {
        Instant now = Instant.now();
        return new ProjectTracking(UUID.randomUUID(), ProjectId.newId(),
                new BigDecimal("1000"), 1, monthlySaving, monthLeft, moneyOwed, 0, now, now);
    }

    @Test
    @DisplayName("monthsToDeadline reflects calendar months; ON_TRACK when monthLeft == it")
    void onTrack() {
        Project p = project(ProjectStatus.ACTIVE, LocalDate.now().plusMonths(5));
        ProjectTrackingResponse r = ProjectTrackingResponse.fromDomain(
                tracking(5, BigDecimal.ZERO, new BigDecimal("100")), p, MAX_FROZEN);

        assertThat(r.getMonthsToDeadline()).isEqualTo(5);
        assertThat(r.getPaceStatus()).isEqualTo(PaceStatus.ON_TRACK);
        assertThat(r.getMaxFrozenMonths()).isEqualTo(MAX_FROZEN);
    }

    @Test
    @DisplayName("monthLeft below calendar months → AHEAD")
    void ahead() {
        Project p = project(ProjectStatus.ACTIVE, LocalDate.now().plusMonths(5));
        ProjectTrackingResponse r = ProjectTrackingResponse.fromDomain(
                tracking(3, BigDecimal.ZERO, new BigDecimal("100")), p, MAX_FROZEN);
        assertThat(r.getPaceStatus()).isEqualTo(PaceStatus.AHEAD);
    }

    @Test
    @DisplayName("monthLeft above calendar months → BEHIND")
    void behind() {
        Project p = project(ProjectStatus.ACTIVE, LocalDate.now().plusMonths(5));
        ProjectTrackingResponse r = ProjectTrackingResponse.fromDomain(
                tracking(8, BigDecimal.ZERO, new BigDecimal("100")), p, MAX_FROZEN);
        assertThat(r.getPaceStatus()).isEqualTo(PaceStatus.BEHIND);
    }

    @Test
    @DisplayName("terminal project → NOT_APPLICABLE")
    void terminalNotApplicable() {
        Project p = project(ProjectStatus.COMPLETED, LocalDate.now().plusMonths(5));
        ProjectTrackingResponse r = ProjectTrackingResponse.fromDomain(
                tracking(5, BigDecimal.ZERO, new BigDecimal("100")), p, MAX_FROZEN);
        assertThat(r.getPaceStatus()).isEqualTo(PaceStatus.NOT_APPLICABLE);
    }

    @Test
    @DisplayName("debt clear estimate = ceil(owed / monthlySaving)")
    void debtClearEstimate() {
        Project p = project(ProjectStatus.FROZEN, LocalDate.now().plusMonths(5));
        ProjectTrackingResponse r = ProjectTrackingResponse.fromDomain(
                tracking(5, new BigDecimal("250"), new BigDecimal("100")), p, MAX_FROZEN);
        assertThat(r.getDebtClearEstimateMonths()).isEqualTo(3); // ceil(2.5)
    }

    @Test
    @DisplayName("no debt → null estimate")
    void noDebtNullEstimate() {
        Project p = project(ProjectStatus.ACTIVE, LocalDate.now().plusMonths(5));
        ProjectTrackingResponse r = ProjectTrackingResponse.fromDomain(
                tracking(5, BigDecimal.ZERO, new BigDecimal("100")), p, MAX_FROZEN);
        assertThat(r.getDebtClearEstimateMonths()).isNull();
    }
}
