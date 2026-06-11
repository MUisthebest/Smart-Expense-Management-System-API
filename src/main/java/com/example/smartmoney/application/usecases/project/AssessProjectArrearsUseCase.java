package com.example.smartmoney.application.usecases.project;

import com.example.smartmoney.domain.models.project.Project;
import com.example.smartmoney.domain.models.project.ProjectContribution;
import com.example.smartmoney.domain.models.project.ProjectTracking;
import com.example.smartmoney.domain.models.project.enums.ProjectStatus;
import com.example.smartmoney.domain.models.project.valueobjects.UserId;
import com.example.smartmoney.domain.repositories.ProjectRepository;
import com.example.smartmoney.domain.repositories.ProjectTrackingRepository;
import com.example.smartmoney.domain.services.SafeSpendingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Monthly arrears assessment for PERSONAL projects (including sub-personal projects
 * linked to a group project). All projects in the {@code projects} table are PERSONAL type;
 * settlement logic is identical regardless of whether a project has a group_project_id.
 *
 * <p>Runs on the 1st of each month at 00:30. Assesses contributions made during the
 * previous calendar month, accrues shortfalls into {@code tracking.money_owed}, and
 * freezes the project if arrears reach 3× the monthly saving.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AssessProjectArrearsUseCase {

    private final ProjectRepository projectRepository;
    private final ProjectTrackingRepository projectTrackingRepository;
    private final SafeSpendingService safeSpendingService;

    @Transactional
    public void execute() {
        Instant from = startOfLastMonth();
        Instant to = startOfThisMonth();

        List<Project> active = projectRepository.findAllByStatus(ProjectStatus.ACTIVE);
        log.info("Arrears assessment starting for {} active projects (window {} → {})",
                active.size(), from, to);

        Set<UUID> affectedUsers = new HashSet<>();

        for (Project p : active) {
            try {
                processPersonal(p, from, to, affectedUsers);
                projectRepository.save(p);
            } catch (Exception ex) {
                log.error("Arrears assessment failed for project {}: {}",
                        p.getId(), ex.getMessage(), ex);
            }
        }

        for (UUID uid : affectedUsers) {
            try {
                safeSpendingService.recalculateAndSaveSafeSpending(new UserId(uid));
            } catch (Exception ex) {
                log.warn("Failed to recalc safe spending for user {}: {}", uid, ex.getMessage());
            }
        }
        log.info("Arrears assessment complete. Affected users: {}", affectedUsers.size());
    }

    private void processPersonal(Project p, Instant from, Instant to, Set<UUID> affectedUsers) {
        ProjectTracking tracking = projectTrackingRepository
                .findByProjectId(p.getId())
                .orElse(null);
        if (tracking == null) return;

        BigDecimal monthly = tracking.getMonthlySaving();
        if (monthly == null || monthly.compareTo(BigDecimal.ZERO) <= 0) return;

        UserId ownerId = p.getOwnerId();
        BigDecimal contributed = sumOwnerContributions(p, ownerId, from, to);
        BigDecimal shortfall = monthly.subtract(contributed);

        BigDecimal currentOwed = tracking.getMoneyOwed() != null
                ? tracking.getMoneyOwed() : BigDecimal.ZERO;
        BigDecimal newOwed = currentOwed;
        if (shortfall.compareTo(BigDecimal.ZERO) > 0) {
            newOwed = currentOwed.add(shortfall);
        }
        tracking.update(null, null, null, newOwed);
        advanceMonth(tracking);
        projectTrackingRepository.save(tracking);

        affectedUsers.add(ownerId.value());

        BigDecimal threshold = monthly.multiply(BigDecimal.valueOf(3));
        if (newOwed.compareTo(threshold) >= 0) {
            log.warn("Project {} owner exceeded 3-month default ({} >= {}) — freezing",
                    p.getId(), newOwed, threshold);
            p.freeze(null);
        }
    }

    private BigDecimal sumOwnerContributions(Project p, UserId ownerId, Instant from, Instant to) {
        BigDecimal total = BigDecimal.ZERO;
        for (ProjectContribution c : p.getContributions()) {
            if (!ownerId.equals(c.getUserId())) continue;
            Instant when = c.getCreatedAt();
            if (when == null || when.isBefore(from) || !when.isBefore(to)) continue;
            total = total.add(c.getMoney().amount());
        }
        return total;
    }

    private void advanceMonth(ProjectTracking tracking) {
        int nextMonth = tracking.getCurrentMonth() + 1;
        int nextLeft = Math.max(0, tracking.getMonthLeft() - 1);
        tracking.update(null, nextMonth, nextLeft, null);
    }

    private Instant startOfThisMonth() {
        return LocalDate.now().withDayOfMonth(1)
                .atStartOfDay(ZoneId.systemDefault()).toInstant();
    }

    private Instant startOfLastMonth() {
        return LocalDate.now().withDayOfMonth(1).minusMonths(1)
                .atStartOfDay(ZoneId.systemDefault()).toInstant();
    }
}
