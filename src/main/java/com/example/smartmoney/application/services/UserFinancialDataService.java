package com.example.smartmoney.application.services;


import com.example.smartmoney.domain.models.budget.Budget;
import com.example.smartmoney.domain.models.project.Project;
import com.example.smartmoney.domain.models.project.ProjectTracking;
import com.example.smartmoney.domain.models.project.valueobjects.Money;
import com.example.smartmoney.domain.models.project.valueobjects.UserId;
import com.example.smartmoney.domain.models.transaction.Transaction;
import com.example.smartmoney.domain.models.transaction.enums.TransactionType;
import com.example.smartmoney.domain.models.user.User;
import com.example.smartmoney.domain.repositories.BudgetRepository;
import com.example.smartmoney.domain.repositories.ProjectRepository;
import com.example.smartmoney.domain.repositories.TransactionRepository;
import com.example.smartmoney.domain.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserFinancialDataService {

    private final TransactionRepository transactionRepository;
    private final ProjectRepository projectRepository;
    private final BudgetRepository budgetRepository;
    private final UserRepository userRepository;

    public enum DataType {
        TRANSACTIONS, PROJECTS, INCOME, PROFILE
    }

    /**
     * Lấy dữ liệu tài chính của người dùng theo các loại được yêu cầu.
     *
     * @param userId   ID của người dùng
     * @param dataTypes Các loại dữ liệu cần lấy (nếu rỗng thì không lấy gì)
     * @return Chuỗi mô tả dữ liệu, sẵn sàng để đưa vào prompt
     */
    public String getUserDataSummary(UUID userId, Set<DataType> dataTypes) {
        if (dataTypes == null || dataTypes.isEmpty()) {
            return "";
        }

        LocalDate now = LocalDate.now();
        LocalDateTime startOfMonth = now.withDayOfMonth(1).atStartOfDay();
        LocalDateTime endOfMonth = now.withDayOfMonth(now.lengthOfMonth()).atTime(23, 59, 59);

        // Chuẩn bị dữ liệu theo yêu cầu
        List<Transaction> transactions = dataTypes.contains(DataType.TRANSACTIONS)
                ? transactionRepository.findByUserIdWithFilters(userId, null, null, startOfMonth, endOfMonth, null, null)
                : Collections.emptyList();

        List<Project> projects = dataTypes.contains(DataType.PROJECTS)
                ? projectRepository.findAllByUser(UserId.of(userId.toString()))
                : Collections.emptyList();

        // Disabled: user_income / user_financial_profile tables removed.
        // INCOME and PROFILE data types are no longer populated.
        /*
        UserIncome income = dataTypes.contains(DataType.INCOME)
                ? userIncomeRepository.findByUserId(userId).orElse(null)
                : null;

        UserFinancialProfile profile = dataTypes.contains(DataType.PROFILE)
                ? profileRepository.findByUserId(userId).orElse(null)
                : null;
        */

        // Xây dựng chuỗi kết quả
        StringBuilder sb = new StringBuilder();
        sb.append("Thông tin tài chính của người dùng:\n");

        // Disabled: user_income / user_financial_profile tables removed.
        /*
        if (profile != null) {
            sb.append("- Tình trạng sống: ").append(profile.getLivingStatus()).append("\n");
            sb.append("- Mức thu nhập: ").append(profile.getIncomeLevel()).append("\n");
            sb.append("- Phong cách chi tiêu: ").append(profile.getSpendingStyle()).append("\n");
            sb.append("- Tình trạng gia đình: ").append(profile.getFamilyStatus()).append("\n");
            sb.append("- Hình thức làm việc: ").append(profile.getWorkStyle()).append("\n");
        }

        if (income != null) {
            sb.append("- Thu nhập ròng: ").append(income.getNetIncome()).append(" ").append(income.getCurrency()).append("\n");
            sb.append("- Thu nhập khả dụng: ").append(income.getUsableIncome()).append(" ").append(income.getCurrency()).append("\n");
            sb.append("- Chi tiêu an toàn: ").append(income.getSafeSpending()).append(" ").append(income.getCurrency()).append("\n");
        }
        */

        if (!transactions.isEmpty()) {
            double totalIncome = transactions.stream()
                    .filter(t -> t.getType() == TransactionType.INCOME)
                    .mapToDouble(t -> t.getAmount().getValue().doubleValue())
                    .sum();
            double totalExpense = transactions.stream()
                    .filter(t -> t.getType() == TransactionType.EXPENSE)
                    .mapToDouble(t -> t.getAmount().getValue().doubleValue())
                    .sum();
            sb.append("- Tổng thu nhập tháng này: ").append(totalIncome).append("\n");
            sb.append("- Tổng chi tiêu tháng này: ").append(totalExpense).append("\n");

            var categoryExpenses = transactions.stream()
                    .filter(t -> t.getType() == TransactionType.EXPENSE)
                    .collect(Collectors.groupingBy(
                            t -> t.getCategory().name(),
                            Collectors.summingDouble(t -> t.getAmount().getValue().doubleValue())
                    ));
            sb.append("- Chi tiêu theo danh mục (top 5):\n");
            categoryExpenses.entrySet().stream()
                    .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                    .limit(5)
                    .forEach(e -> sb.append("  * ").append(e.getKey()).append(": ").append(e.getValue()).append("\n"));
        } else if (dataTypes.contains(DataType.TRANSACTIONS)) {
            sb.append("- Không có giao dịch trong tháng này.\n");
        }

        if (!projects.isEmpty()) {
            sb.append("- Các dự án đang hoạt động:\n");
            projects.stream().limit(5).forEach(p -> {
                Money target = p.getTargetAmount();
                Money contributed = p.totalContributed();
                Money remaining = p.remaining();

                BigDecimal progress = p.progressPercent();

                long monthsLeft = ChronoUnit.MONTHS.between(
                        LocalDate.now(),
                        p.getDeadline()
                );

                sb.append("""
                    * %s
                    - Mục tiêu: %s
                    - Đã tiết kiệm: %s
                    - Còn thiếu: %s
                    - Tiến độ: %.1f%%
                    - Còn %d tháng
                    - Ưu tiên: %s
                    """
                .formatted(
                        p.getName(),
                        target,
                        contributed,
                        remaining,
                        progress,
                        monthsLeft,
                        p.getPriority()
                ));
            }
            );
        } else if (dataTypes.contains(DataType.PROJECTS)) {
            sb.append("- Không có dự án nào.\n");
        }

        return sb.toString();
    }

    /** Income, safe-spending headroom, and days-left for a month — the numeric values behind
     *  {@link #buildChatContext}'s prompt text, exposed so callers can run proactive checks
     *  (near-limit, feasibility, month-end leftover, surplus trend) without re-deriving them. */
    public record FinancialSnapshot(BigDecimal monthlyIncome, BigDecimal safeSpending, int daysLeft) {}

    public FinancialSnapshot getFinancialSnapshot(UUID userId, int month, int year) {
        List<Budget> budgets = budgetRepository.findByUserIdMonthYear(userId, month, year);
        BigDecimal totalBudgetLimit = budgets.stream()
                .map(Budget::getAmountLimit)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalBudgetSpent = budgets.stream()
                .map(b -> b.getSpent() != null ? b.getSpent() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<Project> projects = projectRepository.findAllByUser(UserId.of(userId.toString()));
        LocalDate today = LocalDate.now();

        User user = userRepository.findById(userId).orElse(null);
        BigDecimal monthlyIncome = user != null && user.getIncome() != null ? user.getIncome() : BigDecimal.ZERO;

        // safe_spending = income - active projects' monthly saving commitments, when the user
        // has completed financial onboarding; otherwise fall back to remaining budget headroom
        // this month so the prompt still has a sensible number.
        BigDecimal safeSpending;
        if (user != null && user.hasCompletedFinancialSetup()) {
            BigDecimal activeProjectMonthlyCommitment = projects.stream()
                    .filter(p -> p.getDeadline().isAfter(today) && p.getTracking() != null)
                    .map(p -> p.getTracking().getMonthlySaving())
                    .filter(Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            safeSpending = user.calculateSafeSpending(activeProjectMonthlyCommitment);
        } else {
            safeSpending = totalBudgetLimit.subtract(totalBudgetSpent).max(BigDecimal.ZERO);
        }

        int daysLeft = today.lengthOfMonth() - today.getDayOfMonth();
        return new FinancialSnapshot(monthlyIncome, safeSpending, daysLeft);
    }

    /**
     * Builds the full financial context block for the Gemini chat prompt.
     * Covers: income (from INCOME transactions), safe_spending (budget remaining),
     * budgets (with budgetId/category/spent/limit), and projects (with
     * monthlySaving/moneyOwed/monthsLeft) for the given month/year.
     */
    public String buildChatContext(UUID userId, int month, int year) {
        LocalDate monthStart = LocalDate.of(year, month, 1);
        LocalDate monthEnd = monthStart.withDayOfMonth(monthStart.lengthOfMonth());
        LocalDateTime start = monthStart.atStartOfDay();
        LocalDateTime end = monthEnd.atTime(23, 59, 59);

        // Income from INCOME-type transactions this month
        List<Transaction> txs = transactionRepository.findByUserIdWithFilters(
                userId, null, null, start, end, null, null);

        BigDecimal totalIncome = txs.stream()
                .filter(t -> t.getType() == TransactionType.INCOME)
                .map(t -> t.getAmount().getValue())
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalExpense = txs.stream()
                .filter(t -> t.getType() == TransactionType.EXPENSE)
                .map(t -> t.getAmount().getValue())
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Budgets for the month
        List<Budget> budgets = budgetRepository.findByUserIdMonthYear(userId, month, year);

        // Projects
        List<Project> projects = projectRepository.findAllByUser(UserId.of(userId.toString()));
        LocalDate today = LocalDate.now();

        FinancialSnapshot snapshot = getFinancialSnapshot(userId, month, year);
        BigDecimal monthlyIncome = snapshot.monthlyIncome();
        BigDecimal safeSpending = snapshot.safeSpending();
        int daysLeft = snapshot.daysLeft();

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("Thu nhập tháng %d/%d (giao dịch): %,.0f VND\n", month, year, totalIncome));
        sb.append(String.format("Thu nhập hàng tháng: %,.0f VND\n", monthlyIncome));
        sb.append(String.format("Chi tiêu thực tế: %,.0f VND\n", totalExpense));
        sb.append(String.format("Chi tiêu an toàn còn lại (safe_spending): %,.0f VND\n", safeSpending));
        sb.append(String.format("Today: %s, Days left: %d\n", today, daysLeft));

        // Budgets section
        if (!budgets.isEmpty()) {
            sb.append("\nNgân sách tháng ").append(month).append("/").append(year).append(":\n");
            budgets.forEach(b -> {
                BigDecimal spent = b.getSpent() != null ? b.getSpent() : BigDecimal.ZERO;
                BigDecimal pct = b.getAmountLimit().compareTo(BigDecimal.ZERO) > 0
                        ? spent.multiply(BigDecimal.valueOf(100))
                               .divide(b.getAmountLimit(), 1, RoundingMode.HALF_UP)
                        : BigDecimal.ZERO;
                sb.append(String.format("  budgetId=%s category=%s spent=%,.0f limit=%,.0f (%.1f%%)\n",
                        b.getId(), b.getCategory().name(), spent, b.getAmountLimit(), pct));
            });
        } else {
            sb.append("Chưa thiết lập ngân sách tháng ").append(month).append("/").append(year).append(".\n");
        }

        // Projects section
        if (!projects.isEmpty()) {
            sb.append("\nDự án đang hoạt động:\n");
            projects.stream()
                    .filter(p -> p.getDeadline().isAfter(today))
                    .limit(5)
                    .forEach(p -> {
                        long monthsLeft = ChronoUnit.MONTHS.between(today, p.getDeadline());
                        ProjectTracking tracking = p.getTracking();
                        BigDecimal monthlySaving = tracking != null ? tracking.getMonthlySaving() : BigDecimal.ZERO;
                        BigDecimal moneyOwed = tracking != null ? tracking.getMoneyOwed() : BigDecimal.ZERO;
                        sb.append(String.format(
                                "  %s: target=%s remaining=%s monthlySaving=%,.0f moneyOwed=%,.0f monthsLeft=%d priority=%s\n",
                                p.getName(),
                                p.getTargetAmount(),
                                p.remaining(),
                                monthlySaving,
                                moneyOwed,
                                monthsLeft,
                                p.getPriority()));
                    });
        }

        return sb.toString();
    }
}
