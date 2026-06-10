package com.example.smartmoney.application.usecases.project;

import com.example.smartmoney.domain.models.project.Project;
import com.example.smartmoney.domain.models.project.ProjectContribution;
import com.example.smartmoney.domain.models.project.ProjectMember;
import com.example.smartmoney.domain.models.project.ProjectTracking;
import com.example.smartmoney.domain.models.project.enums.JoinStatus;
import com.example.smartmoney.domain.models.project.enums.ProjectStatus;
import com.example.smartmoney.domain.models.project.enums.ProjectType;
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
 * Monthly arrears assessment for both GROUP and PERSONAL projects.
 *
 * <h3>Group projects</h3>
 * <ul>
 *   <li>Per JOINED member with a positive {@code monthlyCommitmentAmount},
 *       compute the sum of contributions made by that member during the
 *       previous calendar month.</li>
 *   <li>Any shortfall is added to that member's {@code arrearsBalance}.</li>
 *   <li>If a non-owner member's arrears reach 3× their commitment, the member
 *       is kicked (DEFAULTED, arrears wiped) and the admin must rebalance the
 *       project goal.</li>
 *   <li>If the OWNER (the implicit admin) reaches the 3× threshold, the whole
 *       project is FROZEN — no further contributions accepted until the owner
 *       catches up and unfreezes.</li>
 *   <li>The aggregate {@code project_tracking.money_owed} is refreshed to
 *       {@code SUM(arrearsBalance)} across all non-LEFT/non-DEFAULTED members.</li>
 * </ul>
 *
 * <h3>Personal projects</h3>
 * <ul>
 *   <li>Same shortfall computation against {@code tracking.monthlySaving} and
 *       the owner's own contributions in the previous month.</li>
 *   <li>Shortfalls accumulate into {@code tracking.money_owed} directly (there
 *       is no per-member row).</li>
 *   <li>If {@code money_owed} reaches 3× the monthly saving, the project is
 *       FROZEN. The owner must lower the saving, abandon, or catch up.</li>
 * </ul>
 *
 * <h3>Cycle definition</h3>
 * "Previous calendar month" in the server's default timezone. The worker runs
 * on the 1st of each month at 00:30, so when it fires on (say) Feb 1, it
 * assesses contributions made during January.
 *
 * <h3>currentMonth advancement</h3>
 * Advanced by +1 inside this use case after the shortfall calc, so that
 * downstream displays reflect the new cycle.
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
                if (p.getType() == ProjectType.GROUP) {
                    processGroup(p, from, to, affectedUsers);
                } else {
                    processPersonal(p, from, to, affectedUsers);
                }
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

    // ───────────────────────────── GROUP ─────────────────────────────

    private void processGroup(Project p, Instant from, Instant to, Set<UUID> affectedUsers) {
        UserId ownerId = p.getOwnerId();

        for (ProjectMember m : p.getMembers()) {
            // Skip non-active and zero-commitment members
            if (m.getJoinStatus() != JoinStatus.JOINED) continue;
            BigDecimal commitment = m.getMonthlyCommitmentAmount();
            if (commitment == null || commitment.compareTo(BigDecimal.ZERO) <= 0) continue;

            BigDecimal contributed = sumMemberContributions(p, m.getUserId(), from, to);
            BigDecimal shortfall = commitment.subtract(contributed);
            if (shortfall.compareTo(BigDecimal.ZERO) > 0) {
                m.accrueArrears(shortfall);
            }
            affectedUsers.add(m.getUserId().value());
        }

        // Default checks — iterate snapshot to avoid concurrent modification
        // since kicking changes joinStatus mid-iteration.
        for (ProjectMember m : List.copyOf(p.getMembers())) {
            if (!m.isOver3MonthDefault()) continue;
            if (m.getUserId().equals(ownerId)) {
                // Owner default → freeze the entire project. Non-owner admins
                // get kicked like regular members; only owner triggers freeze.
                log.warn("Owner {} defaulted on project {} — freezing", ownerId, p.getId());
                p.freeze(null /* system */);
                affectedUsers.add(ownerId.value());
                break; // no point processing further members on a frozen project
            } else {
                log.warn("Member {} defaulted on project {} — kicking", m.getUserId(), p.getId());
                p.kickForDefault(m.getUserId());
                affectedUsers.add(m.getUserId().value());
            }
        }

        // Refresh aggregate money_owed in tracking
        projectTrackingRepository.findByProjectId(p.getId()).ifPresent(tracking -> {
            BigDecimal aggregate = p.sumActiveArrears();
            tracking.update(null, null, null, aggregate);
            advanceMonth(tracking);
            projectTrackingRepository.save(tracking);
        });
    }

    // ──────────────────────────── PERSONAL ───────────────────────────

    private void processPersonal(Project p, Instant from, Instant to, Set<UUID> affectedUsers) {
        ProjectTracking tracking = projectTrackingRepository
                .findByProjectId(p.getId())
                .orElse(null);
        if (tracking == null) return;

        BigDecimal monthly = tracking.getMonthlySaving();
        if (monthly == null || monthly.compareTo(BigDecimal.ZERO) <= 0) return;

        UserId ownerId = p.getOwnerId();
        BigDecimal contributed = sumMemberContributions(p, ownerId, from, to);
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

        // 3-month default → freeze
        BigDecimal threshold = monthly.multiply(BigDecimal.valueOf(3));
        if (newOwed.compareTo(threshold) >= 0) {
            log.warn("Personal project {} owner exceeded 3-month default ({} >= {}) — freezing",
                    p.getId(), newOwed, threshold);
            p.freeze(null /* system */);
        }
    }

    // ─────────────────────────── helpers ─────────────────────────────

    private BigDecimal sumMemberContributions(Project p, UserId memberId,
                                              Instant from, Instant to) {
        BigDecimal total = BigDecimal.ZERO;
        for (ProjectContribution c : p.getContributions()) {
            if (!memberId.equals(c.getUserId())) continue;
            Instant when = c.getCreatedAt();
            if (when == null) continue;
            if (when.isBefore(from)) continue;
            if (!when.isBefore(to)) continue;
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
        LocalDate today = LocalDate.now();
        return today.withDayOfMonth(1).atStartOfDay(ZoneId.systemDefault()).toInstant();
    }

    private Instant startOfLastMonth() {
        LocalDate today = LocalDate.now();
        return today.withDayOfMonth(1).minusMonths(1)
                .atStartOfDay(ZoneId.systemDefault()).toInstant();
    }
}
