package com.example.smartmoney.application.usecases.project;

import com.example.smartmoney.application.dtos.requests.project.ProjectBudgetAdvisorRequest;
import com.example.smartmoney.application.dtos.responses.project.ProjectBudgetAdvisorResponse;
import com.example.smartmoney.domain.models.user.User;
import com.example.smartmoney.domain.models.user.enums.Saving_Pace;
import com.example.smartmoney.domain.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * Suggests a monthly-saving plan for a project: how much to set aside and over how many
 * months to reach the target by the deadline.
 *
 * <p><b>Pivot D + redesign §5.5:</b> the pace is no longer a per-request {@code Mode}
 * (URGENT/RELAXED) — it reads the user's {@code savingPace} trait (goalPush), so one default
 * drives every project. <b>Advisory, never blocking:</b> the old 20%-usable-income limit threw
 * {@code PROJECT_USABLE_INCOME_LIMIT_EXCEEDED}; that affordability gate is gone (net-income /
 * learned-disposable affordability is Pivot A/C). This use case only computes and suggests.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProjectBudgetAdvisorUseCase {

    private final UserRepository userRepository;

    public ProjectBudgetAdvisorResponse execute(String actorId, ProjectBudgetAdvisorRequest req) {
        Saving_Pace pace = resolvePace(actorId);

        int numberOfMonths = monthsForPace(monthsUntil(req.getDeadline()), pace);
        BigDecimal monthlySaving = req.getTargetAmount()
                .divide(BigDecimal.valueOf(numberOfMonths), 2, RoundingMode.HALF_UP);

        return ProjectBudgetAdvisorResponse.builder()
                .monthlySaving(monthlySaving)
                .numberOfMonths(numberOfMonths)
                .build();
    }

    /** The user's saving-pace trait, defaulting to BALANCED when unset (cold start). */
    private Saving_Pace resolvePace(String actorId) {
        Saving_Pace pace = userRepository.findById(UUID.fromString(actorId))
                .map(User::getSavingPace)
                .orElse(null);
        return pace != null ? pace : Saving_Pace.BALANCED;
    }

    /** Whole calendar months from today to the deadline, rounding up a partial month; min 1. */
    private int monthsUntil(LocalDate deadline) {
        LocalDate today = LocalDate.now();
        if (!deadline.isAfter(today)) return 1;
        long months = ChronoUnit.MONTHS.between(today, deadline);
        if (today.plusMonths(months).isBefore(deadline)) months += 1;
        return (int) Math.max(1, months);
    }

    /**
     * Adjusts the horizon by pace: AGGRESIVE finishes sooner (higher monthly saving), RELAXED
     * stretches one month past the deadline (lower monthly saving), BALANCED tracks the deadline.
     */
    private int monthsForPace(int deadlineMonths, Saving_Pace pace) {
        return switch (pace) {
            case AGGRESIVE -> Math.max(1, (int) Math.ceil(deadlineMonths * 0.75));
            case RELAXED -> deadlineMonths + 1;
            case BALANCED -> deadlineMonths;
        };
    }
}
