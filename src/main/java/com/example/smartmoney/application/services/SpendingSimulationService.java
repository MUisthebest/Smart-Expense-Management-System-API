package com.example.smartmoney.application.services;

import com.example.smartmoney.application.dtos.responses.ChatResponse;
import com.example.smartmoney.domain.exceptions.DomainException;
import com.example.smartmoney.domain.models.project.Project;
import com.example.smartmoney.domain.models.project.enums.ProjectStatus;
import com.example.smartmoney.domain.models.project.valueobjects.UserId;
import com.example.smartmoney.domain.models.user.User;
import com.example.smartmoney.domain.repositories.ProjectRepository;
import com.example.smartmoney.domain.repositories.TransactionRepository;
import com.example.smartmoney.domain.repositories.UserRepository;
import com.example.smartmoney.domain.services.EnvelopeCalculator;
import com.example.smartmoney.domain.services.EnvelopeSnapshot;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * UC2 — What-if Simulation:
 * Given a hypothetical extra spend, computes the resulting balance and which saving projects
 * would be delayed.
 *
 * <p>This service is PURELY READ-ONLY — no data is written.
 *
 * <p><b>Envelope arithmetic</b>: the "still-spendable this month" answer is computed via
 * {@link EnvelopeCalculator#compute(User, List, BigDecimal)} — the trait-aware envelope
 * (safeSpending − targetSavings) minus the month-to-date actual spend — NOT the old
 * {@code totalBudget − totalSpent} shortcut. The old formula depended on the user having set
 * budgets for every relevant category and treated any un-budgeted headroom as spendable, which
 * silently over- or under-counted by a savingCapacity-linked amount. See {@link
 * EnvelopeCalculator} javadoc for the specific inconsistencies this fix removes.
 *
 * <p><b>Project-impact algorithm</b>:
 * Projects are sorted HIGH → LOW priority. Starting from the still-spendable remainder after
 * the extra spend, high-priority projects are funded first. Any project that cannot be funded
 * within the remaining amount is marked as "delayed".
 */
@Service
@RequiredArgsConstructor
public class SpendingSimulationService {

    private final ProjectRepository projectRepository;
    private final TransactionRepository transactionRepository;
    private final UserRepository userRepository;
    private final EnvelopeCalculator envelopeCalculator;

    // Matches Vietnamese/English monetary amounts: "1M", "1 triệu", "500K", "1,500,000"
    private static final Pattern AMOUNT_PATTERN = Pattern.compile(
            "([\\d][\\d,.]*)\\s*(tỷ|ty|billion|triệu|tr|M|m|K|k|nghìn|ngàn)?",
            Pattern.UNICODE_CASE);

    // Default amount used when no parseable number is found in the message
    private static final BigDecimal DEFAULT_EXTRA_AMOUNT = BigDecimal.valueOf(500_000);

    /**
     * Runs the what-if simulation and returns structured results.
     *
     * @param userId     the authenticated user
     * @param month      month being simulated (1-12)
     * @param year       year being simulated
     * @param messageLow user message in lowercase, used to parse the extra spend amount
     * @return SimulationResult with scenario description, new spendable-remainder, and project impact
     */
    public ChatResponse.SimulationResult simulate(UUID userId, int month, int year, String messageLow) {

        // UC2 step 1: Parse the extra spend amount from the user's message
        BigDecimal extraAmount = parseAmount(messageLow);
        if (extraAmount.compareTo(BigDecimal.ZERO) == 0) {
            // No parseable amount found; use a sensible default and let the AI mention it
            extraAmount = DEFAULT_EXTRA_AMOUNT;
        }

        // UC2 step 2: Actual expenses already recorded this month (excludes PROJECT_CONTRIBUTION —
        // that's bookkeeping, not real spending; the repository already filters those out).
        LocalDate monthStart = LocalDate.of(year, month, 1);
        LocalDate monthEnd = monthStart.withDayOfMonth(monthStart.lengthOfMonth());
        BigDecimal totalSpent = transactionRepository.getTotalExpenseBetween(userId, monthStart, monthEnd);
        if (totalSpent == null) totalSpent = BigDecimal.ZERO;

        // UC2 step 3: Compute the true envelope + real remaining via the calculator. This
        // replaces the pre-fix "totalBudget − totalSpent" shortcut. Fails gracefully if the user
        // hasn't completed financial setup — a domain exception carries through so ChatWithAI can
        // present a helpful message rather than crashing the turn.
        Optional<User> userOpt = userRepository.findById(userId);
        List<Project> active = activeProjects(userId);

        BigDecimal envelope;
        BigDecimal realRemaining;
        BigDecimal newSpendableRemainder;
        try {
            EnvelopeSnapshot snap = envelopeCalculator.compute(userOpt.orElse(null), active, totalSpent);
            envelope = snap.envelope();
            realRemaining = snap.realRemaining();
            newSpendableRemainder = realRemaining.subtract(extraAmount);
        } catch (DomainException e) {
            // No income/setup yet — degrade to a purely descriptive result, no project impact
            envelope = BigDecimal.ZERO;
            realRemaining = BigDecimal.ZERO;
            newSpendableRemainder = extraAmount.negate();
        }

        // UC2 step 4: Determine which projects (if any) would be delayed under the new remainder
        String projectImpact = analyseProjectImpact(active, newSpendableRemainder);

        String scenario = String.format(
                "Chi thêm %,.0f VND → còn lại: %,.0f VND (envelope tháng %,.0f, đã chi %,.0f)",
                extraAmount, newSpendableRemainder, envelope, totalSpent);

        return ChatResponse.SimulationResult.builder()
                .scenario(scenario)
                .newSafeSpending(newSpendableRemainder.longValue())
                .projectImpact(projectImpact)
                .build();
    }

    /**
     * Builds a context string summarising the simulation for the AI prompt,
     * so the LLM can explain the impact naturally in Vietnamese.
     */
    public String buildSimulationContext(UUID userId, int month, int year, String messageLow) {
        ChatResponse.SimulationResult result = simulate(userId, month, year, messageLow);
        return String.format(
                "Kết quả mô phỏng kịch bản:\n" +
                        "- %s\n" +
                        "- Ngân sách an toàn còn lại: %,.0f VND\n" +
                        "- Tác động đến dự án: %s\n",
                result.getScenario(),
                (double) result.getNewSafeSpending(),
                result.getProjectImpact());
    }

    /**
     * Determines which saving projects are at risk given the remaining spendable amount.
     *
     * <p>Priority ordering: HIGH → MEDIUM → LOW.
     * Each project's required monthly contribution = remaining_target / months_left.
     * Projects that cannot be funded within the remaining amount are marked delayed.
     */
    private String analyseProjectImpact(List<Project> activeProjects, BigDecimal newRemainder) {
        LocalDate today = LocalDate.now();

        if (activeProjects.isEmpty()) {
            return "Không có dự án đang hoạt động.";
        }

        if (newRemainder.compareTo(BigDecimal.ZERO) < 0) {
            // Completely over budget — all projects will be starved of contributions
            return String.format(
                    "Ngân sách bị vượt mức (âm %,.0f VND). TẤT CẢ %d dự án có nguy cơ bị trễ tiến độ tháng này.",
                    newRemainder.abs(), activeProjects.size());
        }

        // Greedy allocation: fund HIGH priority first, delay LOW priority when budget runs out
        BigDecimal available = newRemainder;
        List<String> funded = new ArrayList<>();
        List<String> delayed = new ArrayList<>();

        for (Project p : activeProjects) {
            long monthsLeft = ChronoUnit.MONTHS.between(today, p.getDeadline());
            if (monthsLeft <= 0) continue;
            BigDecimal monthlyNeeded = p.remaining().amount()
                    .divide(BigDecimal.valueOf(monthsLeft), 0, RoundingMode.CEILING);

            if (available.compareTo(monthlyNeeded) >= 0) {
                available = available.subtract(monthlyNeeded);
                funded.add(p.getName());
            } else {
                delayed.add(p.getName() + " (thiếu "
                        + String.format("%,.0f VND", monthlyNeeded.subtract(available.max(BigDecimal.ZERO)))
                        + ")");
            }
        }

        if (delayed.isEmpty()) {
            return String.format("Tất cả %d dự án không bị ảnh hưởng (còn dư %,.0f VND sau phân bổ).",
                    funded.size(), available);
        }

        StringBuilder sb = new StringBuilder();
        if (!funded.isEmpty()) {
            sb.append("Dự án không bị ảnh hưởng: ").append(String.join(", ", funded)).append(". ");
        }
        sb.append("Dự án có nguy cơ bị trễ tiến độ: ").append(String.join(", ", delayed)).append(".");
        return sb.toString();
    }

    /**
     * Active projects with future deadlines, highest priority first (HIGH → MEDIUM → LOW).
     * Same filter shape as {@code ProjectHypothesisService#activeProjects}, used both for the
     * envelope calculation and for the project-impact allocation.
     */
    private List<Project> activeProjects(UUID userId) {
        LocalDate today = LocalDate.now();
        return projectRepository.findAllByUser(UserId.of(userId.toString())).stream()
                .filter(p -> p.getStatus() == ProjectStatus.ACTIVE
                        && p.getDeadline() != null
                        && ChronoUnit.MONTHS.between(today, p.getDeadline()) > 0)
                .sorted(Comparator.comparing((Project p) -> p.getPriority().ordinal()).reversed())
                .collect(Collectors.toList());
    }

    /**
     * Parses a monetary VND amount from a Vietnamese/English financial message.
     *
     * Supported formats: "2 tỷ", "1M", "1m", "1 triệu", "1.5 tr", "500K", "500k",
     *                    "500 nghìn", "1,500,000" (bare number ≥ 1,000 only)
     *
     * Package-private for unit testing.
     */
    BigDecimal parseAmount(String text) {
        Matcher m = AMOUNT_PATTERN.matcher(text);
        while (m.find()) {
            // Remove thousands-separator commas before parsing
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
                // Bare number without unit — only accept if it looks like a realistic VND amount
                if (num.compareTo(BigDecimal.valueOf(1_000)) >= 0) {
                    return num;
                }
            } catch (NumberFormatException ignored) {
                // Skip malformed tokens
            }
        }
        return BigDecimal.ZERO; // caller applies DEFAULT_EXTRA_AMOUNT when result is zero
    }
}