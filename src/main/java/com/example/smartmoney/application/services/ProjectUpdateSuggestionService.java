package com.example.smartmoney.application.services;

import com.example.smartmoney.application.dtos.responses.ChatResponse;
import com.example.smartmoney.domain.models.project.Project;
import com.example.smartmoney.domain.models.project.ProjectTracking;
import com.example.smartmoney.domain.models.project.enums.ProjectStatus;
import com.example.smartmoney.domain.models.project.valueobjects.UserId;
import com.example.smartmoney.domain.repositories.ProjectRepository;
import com.example.smartmoney.domain.repositories.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * PROJECT_UPDATE — the user directly asks to adjust an existing project's tracked
 * income (extra monthly/one-off contribution → {@code moneySaved}) or expense
 * (debt/money owed → {@code moneyOwed}), as opposed to INCOME_WINDFALL/LARGE_EXPENSE
 * which are triggered by a sudden external event.
 *
 * This service is PURELY READ-ONLY — it never writes any project record. The frontend
 * shows a confirm card and calls {@code PUT /api/v1/projects/{projectId}/tracking}
 * only after the user explicitly confirms.
 */
@Service
@RequiredArgsConstructor
public class ProjectUpdateSuggestionService {

    private static final Pattern AMOUNT_PATTERN = Pattern.compile(
            "([\\d][\\d,.]*)\\s*(tỷ|ty|billion|triệu|tr|M|m|K|k|nghìn|ngàn)?",
            Pattern.UNICODE_CASE);

    private static final BigDecimal DEFAULT_AMOUNT = BigDecimal.valueOf(1_000_000);

    // Feasibility check: a project's monthlySaving crossing this fraction of monthly income
    // is flagged as unsustainable; the suggested new monthlySaving targets SUSTAINABLE_RATIO instead.
    private static final BigDecimal INFEASIBLE_RATIO = BigDecimal.valueOf(0.5);
    private static final BigDecimal SUSTAINABLE_RATIO = BigDecimal.valueOf(0.3);

    // Month-end leftover nudge: only fires with few days left and a meaningful amount unspent.
    private static final int LEFTOVER_DAYS_THRESHOLD = 5;
    private static final BigDecimal LEFTOVER_MIN_AMOUNT = BigDecimal.valueOf(500_000);

    // Surplus-trend nudge: 3 consecutive PAST calendar months each clearing this much surplus.
    private static final int SURPLUS_TREND_MONTHS = 3;
    private static final BigDecimal SURPLUS_THRESHOLD = BigDecimal.valueOf(2_000_000);

    // Debt/expense direction keywords — checked before the (more common) contribution keywords.
    // Bounded gap ([\s\S]{0,20}) tolerates the amount sitting between the two keywords,
    // e.g. "trả bớt 1 triệu nợ" — the number falls right where a rigid adjacency match would break.
    private static final Pattern DEBT_INCREASE = Pattern.compile(
            "nợ[\\s\\S]{0,20}(thêm|tăng)|(thêm|tăng)[\\s\\S]{0,20}nợ|" +
                    "phát sinh[\\s\\S]{0,20}(nợ|chi phí)|vay thêm|phải trả thêm",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern DEBT_DECREASE = Pattern.compile(
            "trả[\\s\\S]{0,20}(bớt|hết|xong)[\\s\\S]{0,20}nợ|giảm[\\s\\S]{0,20}nợ|" +
                    "thanh toán[\\s\\S]{0,20}nợ|đã trả nợ|pay off debt|reduce debt",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    private final ProjectRepository projectRepository;
    private final TransactionRepository transactionRepository;

    /**
     * Computes a single suggestion for the project the user is referring to.
     *
     * @param userId     authenticated user
     * @param messageLow user message, lower-cased, used for project-name matching
     *                   and contribution-vs-debt direction detection
     * @return zero-or-one-element list (empty when the user has no projects)
     */
    /**
     * When a suggestion computation comes back empty because the user named a specific project
     * that exists but isn't ACTIVE (completed/abandoned/frozen/expired), this finds it so the
     * caller can tell the user clearly why nothing was suggested — instead of silently no-op'ing,
     * which reads as the request being ignored.
     *
     * @return the named project, only if its status isn't ACTIVE; empty if no name in the
     *         message matches any of the user's projects, or the match is itself ACTIVE
     */
    public Optional<Project> findNamedInactiveProject(UUID userId, String messageLow) {
        return projectRepository.findAllByUser(UserId.of(userId.toString())).stream()
                .filter(p -> p.getStatus() != ProjectStatus.ACTIVE)
                .filter(p -> messageLow.contains(p.getName().toLowerCase()))
                .findFirst();
    }

    /** Short human-readable phrase for a non-ACTIVE project status, VI/EN. */
    public static String statusPhrase(ProjectStatus status, boolean english) {
        return switch (status) {
            case COMPLETED -> english ? "already completed" : "đã hoàn tất";
            case ABANDONED, CANCELLED -> english ? "already cancelled" : "đã bị hủy";
            case FROZEN -> english ? "currently frozen" : "đang tạm dừng";
            case EXPIRED -> english ? "already expired" : "đã hết hạn";
            default -> english ? "not active" : "không còn hoạt động";
        };
    }

    public List<ChatResponse.SavingsPlanSuggestion> suggest(UUID userId, String messageLow) {
        // Only ACTIVE projects are eligible targets — a COMPLETED/ABANDONED/FROZEN/EXPIRED project
        // shouldn't receive a new contribution or debt suggestion even if the user names it.
        List<Project> projects = activeProjects(userId);
        if (projects.isEmpty()) return Collections.emptyList();

        Project target = pickTargetProject(projects, messageLow);
        if (target == null || target.getTracking() == null) return Collections.emptyList();

        BigDecimal amount = parseAmount(messageLow);
        if (amount.compareTo(BigDecimal.ZERO) == 0) amount = DEFAULT_AMOUNT;

        ProjectTracking tracking = target.getTracking();
        boolean english = MessageLanguageDetector.isEnglish(messageLow);

        if (DEBT_DECREASE.matcher(messageLow).find()) {
            return List.of(buildDebtSuggestion(target, tracking, amount.negate(), english));
        }
        if (DEBT_INCREASE.matcher(messageLow).find()) {
            return List.of(buildDebtSuggestion(target, tracking, amount, english));
        }
        // Default: treat as an extra contribution to moneySaved (the "project income" case).
        return List.of(buildContributionSuggestion(target, amount, english));
    }

    /**
     * Proactive safety-net check: flags an active project whose {@code monthlySaving} exceeds
     * {@link #INFEASIBLE_RATIO} of the user's monthly income as unsustainable, and suggests
     * lowering it to {@link #SUSTAINABLE_RATIO} of income — expressed as an extended
     * {@code suggestedMonthLeft} (monthlySaving is derived as remaining/monthsLeft, so extending
     * the deadline is how this DTO shape expresses "lower the monthly saving").
     *
     * @return empty when income is unset/zero, or no active project is over the ratio
     */
    public Optional<ChatResponse.SavingsPlanSuggestion> checkFeasibility(
            UUID userId, BigDecimal monthlyIncome, boolean english) {
        if (monthlyIncome == null || monthlyIncome.compareTo(BigDecimal.ZERO) <= 0) return Optional.empty();

        List<Project> projects = activeProjects(userId);
        BigDecimal infeasibleFloor = monthlyIncome.multiply(INFEASIBLE_RATIO);

        Optional<Project> worst = projects.stream()
                .filter(p -> p.getTracking() != null && p.getTracking().getMonthlySaving() != null)
                .filter(p -> p.getTracking().getMonthlySaving().compareTo(infeasibleFloor) > 0)
                .max(Comparator.comparing(p -> p.getTracking().getMonthlySaving()));

        if (worst.isEmpty()) return Optional.empty();

        Project project = worst.get();
        ProjectTracking tracking = project.getTracking();
        BigDecimal newMonthlySaving = monthlyIncome.multiply(SUSTAINABLE_RATIO).setScale(0, RoundingMode.HALF_UP);
        if (newMonthlySaving.compareTo(BigDecimal.ZERO) <= 0) return Optional.empty();

        BigDecimal remaining = project.remaining().amount();
        int newMonthLeft = remaining.divide(newMonthlySaving, 0, RoundingMode.CEILING).intValue();
        if (newMonthLeft <= tracking.getMonthLeft()) return Optional.empty();

        return Optional.of(ChatResponse.SavingsPlanSuggestion.builder()
                .projectId(project.getId().toString())
                .projectName(project.getName())
                .currentMoneySaved(tracking.getMoneySaved() != null ? tracking.getMoneySaved().longValue() : 0L)
                .suggestedMonthLeft(newMonthLeft)
                .reason(english
                        ? String.format(
                        "'%s' needs %,.0f VND/month — too high vs your income. Lower it to " +
                        "%,.0f VND/month by extending the deadline to %d month(s)?",
                        project.getName(), tracking.getMonthlySaving(), newMonthlySaving, newMonthLeft)
                        : String.format(
                        "Dự án '%s' đang cần %,.0f VND/tháng — quá cao so với thu nhập của bạn. " +
                        "Giảm xuống %,.0f VND/tháng bằng cách kéo dài thời hạn thành %d tháng?",
                        project.getName(), tracking.getMonthlySaving(), newMonthlySaving, newMonthLeft))
                .build());
    }

    /**
     * Proactive safety-net check: near month-end, with meaningful safe-spending left unused,
     * suggests topping up the highest-priority active project with the leftover.
     *
     * @return empty when there are more than {@link #LEFTOVER_DAYS_THRESHOLD} days left, the
     *         leftover is below {@link #LEFTOVER_MIN_AMOUNT}, or there's no active project
     */
    public Optional<ChatResponse.SavingsPlanSuggestion> checkMonthEndLeftover(
            UUID userId, int daysLeft, BigDecimal safeSpending, boolean english) {
        if (daysLeft > LEFTOVER_DAYS_THRESHOLD) return Optional.empty();
        if (safeSpending == null) return Optional.empty();

        // safeSpending is the PLANNED envelope (income minus project commitments) — it does NOT
        // subtract actual spending already recorded this month. Treating the whole planned figure
        // as "unused leftover" is wrong once real expenses have eaten into it (or blown past it
        // entirely, e.g. a deficit month with no income confirmed yet): the amount offered to a
        // project must be what's ACTUALLY still unspent, not the theoretical envelope.
        LocalDate monthStart = LocalDate.now().withDayOfMonth(1);
        LocalDate monthEnd = monthStart.withDayOfMonth(monthStart.lengthOfMonth());
        BigDecimal actualExpense = transactionRepository.getTotalExpenseBetween(userId, monthStart, monthEnd);
        BigDecimal realRemaining = safeSpending.subtract(actualExpense != null ? actualExpense : BigDecimal.ZERO);
        if (realRemaining.compareTo(LEFTOVER_MIN_AMOUNT) < 0) return Optional.empty();

        List<Project> projects = activeProjects(userId);
        Project target = projects.stream().findFirst().orElse(null);
        if (target == null || target.getTracking() == null) return Optional.empty();

        return Optional.of(buildLeftoverSuggestion(target, realRemaining, daysLeft, english));
    }

    private ChatResponse.SavingsPlanSuggestion buildLeftoverSuggestion(
            Project project, BigDecimal amount, int daysLeft, boolean english) {
        // Same contributions-based basis as buildContributionSuggestion — an explicit "yes" to
        // this nudge is a new contribution turn, confirmed via AddProjectContributionUseCase.
        BigDecimal current = project.totalContributed().amount();
        BigDecimal updated = current.add(amount);

        return ChatResponse.SavingsPlanSuggestion.builder()
                .projectId(project.getId().toString())
                .projectName(project.getName())
                .currentMoneySaved(current.longValue())
                .suggestedAddAmount(amount.longValue())
                .newMoneySaved(updated.longValue())
                .reason(english
                        ? String.format(
                        "Only %d day(s) left this month and you still have %,.0f VND of safe " +
                        "spending unused — add it to '%s'?",
                        daysLeft, amount, project.getName())
                        : String.format(
                        "Còn %d ngày nữa là hết tháng và bạn vẫn còn dư %,.0f VND chi tiêu an toàn — " +
                        "góp thêm vào '%s' không?",
                        daysLeft, amount, project.getName()))
                .build();
    }

    /**
     * Proactive safety-net check: when the user has cleared a surplus (income − expense) above
     * {@link #SURPLUS_THRESHOLD} in each of the last {@link #SURPLUS_TREND_MONTHS} PAST calendar
     * months, suggests directing the average surplus into the highest-priority active project —
     * per this codebase's existing "redirect to best-fit existing project" policy (the chatbot
     * never creates new projects), rather than proposing a brand-new savings fund.
     *
     * @return empty when there's no active project, or the surplus streak is broken
     */
    public Optional<ChatResponse.SavingsPlanSuggestion> checkSurplusTrend(UUID userId, boolean english) {
        List<Project> projects = activeProjects(userId);
        Project target = projects.stream().findFirst().orElse(null);
        if (target == null || target.getTracking() == null) return Optional.empty();

        LocalDate firstOfThisMonth = LocalDate.now().withDayOfMonth(1);
        BigDecimal totalSurplus = BigDecimal.ZERO;

        for (int i = 1; i <= SURPLUS_TREND_MONTHS; i++) {
            LocalDate monthStart = firstOfThisMonth.minusMonths(i);
            LocalDate monthEnd = monthStart.withDayOfMonth(monthStart.lengthOfMonth());

            BigDecimal income = transactionRepository.getTotalIncomeBetween(userId, monthStart, monthEnd);
            BigDecimal expense = transactionRepository.getTotalExpenseBetween(userId, monthStart, monthEnd);
            BigDecimal surplus = (income != null ? income : BigDecimal.ZERO)
                    .subtract(expense != null ? expense : BigDecimal.ZERO);

            if (surplus.compareTo(SURPLUS_THRESHOLD) < 0) return Optional.empty();
            totalSurplus = totalSurplus.add(surplus);
        }

        BigDecimal averageSurplus = totalSurplus
                .divide(BigDecimal.valueOf(SURPLUS_TREND_MONTHS), 0, RoundingMode.HALF_UP);

        return Optional.of(buildSurplusSuggestion(target, averageSurplus, english));
    }

    private ChatResponse.SavingsPlanSuggestion buildSurplusSuggestion(
            Project project, BigDecimal averageSurplus, boolean english) {
        BigDecimal current = project.totalContributed().amount();
        BigDecimal updated = current.add(averageSurplus);

        return ChatResponse.SavingsPlanSuggestion.builder()
                .projectId(project.getId().toString())
                .projectName(project.getName())
                .currentMoneySaved(current.longValue())
                .suggestedAddAmount(averageSurplus.longValue())
                .newMoneySaved(updated.longValue())
                .reason(english
                        ? String.format(
                        "You've cleared a surplus of about %,.0f VND/month for %d months straight — " +
                        "put it toward '%s'?",
                        averageSurplus, SURPLUS_TREND_MONTHS, project.getName())
                        : String.format(
                        "Bạn đang dư đều khoảng %,.0f VND/tháng suốt %d tháng liền — " +
                        "muốn góp vào '%s' không?",
                        averageSurplus, SURPLUS_TREND_MONTHS, project.getName()))
                .build();
    }

    /** Active projects (status ACTIVE — excludes COMPLETED/ABANDONED/FROZEN/EXPIRED/CANCELLED,
     *  future deadline, has tracking), highest priority first (HIGH &gt; MEDIUM &gt; LOW).
     *
     *  <p>Also excludes sub-personal (group-linked) projects — see
     *  {@link com.example.smartmoney.application.services ChatbotPromptBuilder}'s §5. This
     *  service drives both the explicit PROJECT_UPDATE suggestion path and the proactive nudges
     *  {@code checkFeasibility}/{@code checkMonthEndLeftover}/{@code checkSurplusTrend} that
     *  append reason text to the LLM reply. The orchestrator's post-filter can strip typed
     *  suggestion lists, but it can't scrub free-form reason text pointing at a group project
     *  by name — so this method removes those candidates at the source. Safe because
     *  activeProjects here is used purely for candidate selection, never for aggregate
     *  commitment math (which needs sub-personal projects included). */
    private List<Project> activeProjects(UUID userId) {
        LocalDate today = LocalDate.now();
        return projectRepository.findAllByUser(UserId.of(userId.toString())).stream()
                .filter(p -> p.getStatus() == ProjectStatus.ACTIVE
                        && !p.isSubPersonal()
                        && p.getDeadline().isAfter(today) && p.getTracking() != null
                        && ChronoUnit.MONTHS.between(today, p.getDeadline()) > 0)
                .sorted(Comparator.comparingInt(p -> -p.getPriority().ordinal()))
                .toList();
    }

    private ChatResponse.SavingsPlanSuggestion buildContributionSuggestion(
            Project project, BigDecimal amount, boolean english) {
        // Confirming this goes through AddProjectContributionUseCase (ProjectFilters →
        // Project.contributions + a ledger Transaction), never ProjectTracking — so "current"/
        // "new" must be based on the same contributions total the confirm step and the project's
        // real progress bar both read, not the separate (and never-updated-by-a-contribution)
        // tracking.moneySaved, which would show a stale figure.
        BigDecimal current = project.totalContributed().amount();
        BigDecimal updated = current.add(amount);

        return ChatResponse.SavingsPlanSuggestion.builder()
                .projectId(project.getId().toString())
                .projectName(project.getName())
                .currentMoneySaved(current.longValue())
                .suggestedAddAmount(amount.longValue())
                .newMoneySaved(updated.longValue())
                .reason(english
                        ? String.format(
                        "Add %,.0f VND to '%s' — new saved total: %,.0f VND.",
                        amount, project.getName(), updated)
                        : String.format(
                        "Góp thêm %,.0f VND vào '%s' — số tiền đã tiết kiệm mới: %,.0f VND.",
                        amount, project.getName(), updated))
                .build();
    }

    private ChatResponse.SavingsPlanSuggestion buildDebtSuggestion(
            Project project, ProjectTracking tracking, BigDecimal delta, boolean english) {
        BigDecimal current = tracking.getMoneyOwed() != null ? tracking.getMoneyOwed() : BigDecimal.ZERO;
        BigDecimal updated = current.add(delta).max(BigDecimal.ZERO);
        boolean increase = delta.compareTo(BigDecimal.ZERO) > 0;

        return ChatResponse.SavingsPlanSuggestion.builder()
                .projectId(project.getId().toString())
                .projectName(project.getName())
                .currentMoneyOwed(current.longValue())
                .suggestedMoneyOwed(updated.longValue())
                .reason(english
                        ? (increase
                           ? String.format(
                        "Recorded an extra %,.0f VND debt for '%s' — new debt: %,.0f VND.",
                        delta, project.getName(), updated)
                           : String.format(
                        "Reduced debt by %,.0f VND for '%s' — remaining debt: %,.0f VND.",
                        delta.abs(), project.getName(), updated))
                        : (increase
                           ? String.format(
                        "Ghi nhận phát sinh thêm %,.0f VND nợ cho '%s' — số nợ mới: %,.0f VND.",
                        delta, project.getName(), updated)
                           : String.format(
                        "Giảm %,.0f VND nợ cho '%s' — số nợ còn lại: %,.0f VND.",
                        delta.abs(), project.getName(), updated)))
                .build();
    }

    /** Match by project name mentioned in the message; else fall back to highest priority. */
    private Project pickTargetProject(List<Project> projects, String messageLow) {
        Optional<Project> named = projects.stream()
                .filter(p -> messageLow.contains(p.getName().toLowerCase()))
                .findFirst();
        if (named.isPresent()) return named.get();

        return projects.stream()
                .sorted(Comparator.comparingInt(p -> -p.getPriority().ordinal())) // HIGH first
                .findFirst()
                .orElse(null);
    }

    BigDecimal parseAmount(String text) {
        Matcher m = AMOUNT_PATTERN.matcher(text);
        while (m.find()) {
            String numStr = m.group(1).replace(",", "");
            String unit = m.group(2);
            try {
                BigDecimal num = new BigDecimal(numStr);
                if (unit != null) {
                    return switch (unit.toLowerCase()) {
                        case "tỷ", "ty", "billion" -> num.multiply(BigDecimal.valueOf(1_000_000_000));
                        case "triệu", "tr", "m" -> num.multiply(BigDecimal.valueOf(1_000_000));
                        case "k", "nghìn", "ngàn" -> num.multiply(BigDecimal.valueOf(1_000));
                        default -> num;
                    };
                }
                if (num.compareTo(BigDecimal.valueOf(1_000)) >= 0) return num;
            } catch (NumberFormatException ignored) {}
        }
        return BigDecimal.ZERO;
    }
}