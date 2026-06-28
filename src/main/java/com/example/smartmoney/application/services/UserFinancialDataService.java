package com.example.smartmoney.application.services;


import com.example.smartmoney.domain.models.project.Project;
import com.example.smartmoney.domain.models.project.valueobjects.Money;
import com.example.smartmoney.domain.models.project.valueobjects.UserId;
import com.example.smartmoney.domain.models.transaction.Transaction;
import com.example.smartmoney.domain.models.transaction.enums.TransactionType;
// Disabled: user_income / user_financial_profile tables removed.
// import com.example.smartmoney.domain.models.user.UserIncome;
// import com.example.smartmoney.domain.models.userprofile.UserFinancialProfile;
import com.example.smartmoney.domain.repositories.ProjectContributionRepository;
import com.example.smartmoney.domain.repositories.ProjectRepository;
import com.example.smartmoney.domain.repositories.TransactionRepository;
// import com.example.smartmoney.domain.repositories.UserFinancialProfileRepository;
// import com.example.smartmoney.domain.repositories.UserIncomeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
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
    // Disabled: user_income / user_financial_profile tables removed.
    // private final UserIncomeRepository userIncomeRepository;
    // private final UserFinancialProfileRepository profileRepository;

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
}    
