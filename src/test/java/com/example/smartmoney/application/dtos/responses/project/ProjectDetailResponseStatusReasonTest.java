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

@DisplayName("ProjectDetailResponse.fromDomain — statusReason")
class ProjectDetailResponseStatusReasonTest {

    private Project project(ProjectStatus status, LocalDate deadline, int monthLeft, BigDecimal moneySaved) {
        ProjectId id = ProjectId.newId();
        Instant now = Instant.now();
        ProjectTracking tracking = new ProjectTracking(UUID.randomUUID(), id,
                moneySaved, 1, new BigDecimal("100"), monthLeft, BigDecimal.ZERO, 0, now, now);
        return Project.rehydrate(id, new UserId(UUID.randomUUID()), ProjectType.PERSONAL, status,
                ProjectPriority.MEDIUM, "Trip", null, new Money(new BigDecimal("3000"), "USD"),
                deadline, null, List.of(), tracking, now);
    }

    @Test
    @DisplayName("FROZEN → FROZEN_DEBT")
    void frozen() {
        Project p = project(ProjectStatus.FROZEN, LocalDate.now().plusMonths(3), 3, new BigDecimal("500"));
        assertThat(ProjectDetailResponse.fromDomain(p).getStatusReason())
                .isEqualTo(ProjectStatusReason.FROZEN_DEBT);
    }

    @Test
    @DisplayName("ABANDONED → ABANDONED_BY_USER")
    void abandoned() {
        Project p = project(ProjectStatus.ABANDONED, LocalDate.now().plusMonths(3), 3, new BigDecimal("500"));
        assertThat(ProjectDetailResponse.fromDomain(p).getStatusReason())
                .isEqualTo(ProjectStatusReason.ABANDONED_BY_USER);
    }

    @Test
    @DisplayName("EXPIRED with monthLeft 0 → EXPIRED_DEADLINE")
    void expiredDeadline() {
        Project p = project(ProjectStatus.EXPIRED, LocalDate.now().plusMonths(3), 0, new BigDecimal("500"));
        assertThat(ProjectDetailResponse.fromDomain(p).getStatusReason())
                .isEqualTo(ProjectStatusReason.EXPIRED_DEADLINE);
    }

    @Test
    @DisplayName("EXPIRED with months still left → EXPIRED_FROZEN_TOO_LONG")
    void expiredFrozenTooLong() {
        Project p = project(ProjectStatus.EXPIRED, LocalDate.now().plusMonths(3), 2, new BigDecimal("500"));
        assertThat(ProjectDetailResponse.fromDomain(p).getStatusReason())
                .isEqualTo(ProjectStatusReason.EXPIRED_FROZEN_TOO_LONG);
    }

    @Test
    @DisplayName("ACTIVE tracking to deadline → ON_TRACK")
    void activeOnTrack() {
        Project p = project(ProjectStatus.ACTIVE, LocalDate.now().plusMonths(5), 5, new BigDecimal("500"));
        assertThat(ProjectDetailResponse.fromDomain(p).getStatusReason())
                .isEqualTo(ProjectStatusReason.ON_TRACK);
    }

    @Test
    @DisplayName("ACTIVE with pace-aware months left exceeding calendar → BEHIND_PACE")
    void activeBehindPace() {
        Project p = project(ProjectStatus.ACTIVE, LocalDate.now().plusMonths(2), 6, new BigDecimal("500"));
        assertThat(ProjectDetailResponse.fromDomain(p).getStatusReason())
                .isEqualTo(ProjectStatusReason.BEHIND_PACE);
    }

    @Test
    @DisplayName("paceMonthsLeft comes from tracking; paceStatus ON_TRACK when it equals the calendar countdown")
    void paceFieldsOnTrack() {
        Project p = project(ProjectStatus.ACTIVE, LocalDate.now().plusMonths(5), 5, new BigDecimal("500"));
        ProjectDetailResponse r = ProjectDetailResponse.fromDomain(p);
        assertThat(r.getMonthsLeft()).isEqualTo(5);        // calendar
        assertThat(r.getPaceMonthsLeft()).isEqualTo(5);    // from tracking
        assertThat(r.getPaceStatus()).isEqualTo(PaceStatus.ON_TRACK);
    }

    @Test
    @DisplayName("paceStatus BEHIND when pace-aware months exceed the calendar countdown")
    void paceFieldsBehind() {
        Project p = project(ProjectStatus.ACTIVE, LocalDate.now().plusMonths(2), 6, new BigDecimal("500"));
        ProjectDetailResponse r = ProjectDetailResponse.fromDomain(p);
        assertThat(r.getMonthsLeft()).isEqualTo(2);
        assertThat(r.getPaceMonthsLeft()).isEqualTo(6);
        assertThat(r.getPaceStatus()).isEqualTo(PaceStatus.BEHIND);
    }

    @Test
    @DisplayName("terminal project → paceStatus NOT_APPLICABLE")
    void paceTerminalNotApplicable() {
        Project p = project(ProjectStatus.COMPLETED, LocalDate.now().plusMonths(5), 5, new BigDecimal("500"));
        assertThat(ProjectDetailResponse.fromDomain(p).getPaceStatus())
                .isEqualTo(PaceStatus.NOT_APPLICABLE);
    }
}
