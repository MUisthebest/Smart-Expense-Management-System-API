package com.example.smartmoney.application.services;

import com.example.smartmoney.application.dtos.responses.ChatResponse;
import com.example.smartmoney.domain.models.budget.Budget;
import com.example.smartmoney.domain.models.project.Project;
import com.example.smartmoney.domain.models.project.enums.ProjectPriority;
import com.example.smartmoney.domain.models.project.valueobjects.UserId;
import com.example.smartmoney.domain.repositories.BudgetRepository;
import com.example.smartmoney.domain.repositories.ProjectRepository;
import com.example.smartmoney.domain.repositories.TransactionRepository;
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
 * Given a hypothetical extra spend, computes the resulting safe-spending balance and
 * which saving projects would be delayed.
 *
 * This service is PURELY READ-ONLY — no data is written.
 *
 * Project-impact algorithm:
 *   Projects are sorted HIGH → LOW priority. Starting from the remaining budget
 *   after the extra spend, high-priority projects are funded first. Any project
 *   that cannot be funded within remaining budget is marked as "delayed".
 */
@Service
@RequiredArgsConstructor
public class SpendingSimulationService {

    private final BudgetRepository budgetRepository;
    private final ProjectRepository projectRepository;
    private final TransactionRepository transactionRepository;

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
     * @return SimulationResult with scenario description, new safe spending, and project impact
     */
    public ChatResponse.SimulationResult simulate(UUID userId, int month, int year, String messageLow) {

        // UC2 step 1: Parse the extra spend amount from the user's message
        BigDecimal extraAmount = parseAmount(messageLow);
        if (extraAmount.compareTo(BigDecimal.ZERO) == 0) {
            // No parseable amount found; use a sensible default and let the AI mention it
            extraAmount = DEFAULT_EXTRA_AMOUNT;
        }

        // UC2 step 2: Total budget limit set by the user for this month
        List<Budget> budgets = budgetRepository.findByUserIdMonthYear(userId, month, year);
        BigDecimal totalBudget = budgets.stream()
                .map(Budget::getAmountLimit)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // UC2 step 3: Actual expenses already recorded this month
        LocalDate monthStart = LocalDate.of(year, month, 1);
        LocalDate monthEnd = monthStart.withDayOfMonth(monthStart.lengthOfMonth());
        BigDecimal totalSpent = transactionRepository.getTotalExpenseBetween(userId, monthStart, monthEnd);
        if (totalSpent == null) totalSpent = BigDecimal.ZERO;

        // UC2 step 4: Budget still available before the extra spend
        BigDecimal remainingBudget = totalBudget.subtract(totalSpent).max(BigDecimal.ZERO);

        // UC2 step 5: Remaining after the hypothetical extra spend (can go negative)
        BigDecimal newSafeSpending = remainingBudget.subtract(extraAmount);

        // UC2 step 6: Determine which projects (if any) would be delayed
        String projectImpact = analyseProjectImpact(userId, newSafeSpending);

        String scenario = String.format(
                "Chi thêm %,.0f VND → ngân sách còn lại: %,.0f VND (đã chi: %,.0f / %,.0f VND tháng này)",
                extraAmount, newSafeSpending, totalSpent, totalBudget);

        return ChatResponse.SimulationResult.builder()
                .scenario(scenario)
                .newSafeSpending(newSafeSpending.longValue())
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
     * Determines which saving projects are at risk given the remaining budget.
     *
     * Priority ordering: HIGH → MEDIUM → LOW.
     * Each project's required monthly contribution = remaining_target / months_left.
     * Projects that cannot be funded within the remaining budget are marked delayed.
     */
    private String analyseProjectImpact(UUID userId, BigDecimal newSafeSpending) {
        List<Project> projects = projectRepository.findAllByUser(UserId.of(userId.toString()));
        LocalDate today = LocalDate.now();

        // Filter to only active projects that have future deadlines
        List<Project> activeProjects = projects.stream()
                .filter(p -> ChronoUnit.MONTHS.between(today, p.getDeadline()) > 0)
                .sorted(Comparator.comparing((Project p) -> p.getPriority().ordinal()).reversed()) // HIGH first
                .collect(Collectors.toList());

        if (activeProjects.isEmpty()) {
            return "Không có dự án đang hoạt động.";
        }

        if (newSafeSpending.compareTo(BigDecimal.ZERO) < 0) {
            // Completely over budget — all projects will be starved of contributions
            return String.format(
                    "Ngân sách bị vượt mức (âm %,.0f VND). TẤT CẢ %d dự án có nguy cơ bị trễ tiến độ tháng này.",
                    newSafeSpending.abs(), activeProjects.size());
        }

        // Greedy allocation: fund HIGH priority first, delay LOW priority when budget runs out
        BigDecimal available = newSafeSpending;
        List<String> funded = new ArrayList<>();
        List<String> delayed = new ArrayList<>();

        for (Project p : activeProjects) {
            long monthsLeft = ChronoUnit.MONTHS.between(today, p.getDeadline());
            BigDecimal monthlyNeeded = p.remaining().amount()
                    .divide(BigDecimal.valueOf(monthsLeft), 0, RoundingMode.CEILING);

            if (available.compareTo(monthlyNeeded) >= 0) {
                available = available.subtract(monthlyNeeded);
                funded.add(p.getName());
            } else {
                // Calculate how many months this project gets delayed
                // Delay = 1 month if we can fund nothing this month, more if deficit is large
                delayed.add(p.getName() + " (thiếu " + String.format("%,.0f VND", monthlyNeeded.subtract(available.max(BigDecimal.ZERO))) + ")");
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
