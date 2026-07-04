package com.example.smartmoney.application.usecases.budget;

import com.example.smartmoney.application.dtos.requests.project.ProjectFilterRequest;
import com.example.smartmoney.application.usecases.queue.ProcessQueueRequestUseCase;
import com.example.smartmoney.domain.exceptions.DomainException;
import com.example.smartmoney.domain.exceptions.BudgetErrorCode;
import com.example.smartmoney.domain.models.message.MessageRecord;
import com.example.smartmoney.domain.models.project.enums.ProjectStatus;
import com.example.smartmoney.domain.models.project.valueobjects.UserId;
import com.example.smartmoney.domain.models.transaction.Transaction;
import com.example.smartmoney.domain.models.transaction.enums.Category;
import com.example.smartmoney.domain.models.transaction.enums.TransactionType;
import com.example.smartmoney.domain.models.user.User;
import com.example.smartmoney.domain.models.project.Project;
import com.example.smartmoney.domain.repositories.ProjectRepository;
import com.example.smartmoney.domain.repositories.TransactionRepository;
import com.example.smartmoney.domain.repositories.UserRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.*;

@Service
public class GenerateBudgetAllocationPlanUseCase {

    private static final String DUTY = "BUDGET_ALLOCATION_PLAN";

    private final UserRepository userRepository;
    private final TransactionRepository transactionRepository;
    private final ProcessQueueRequestUseCase processQueueRequestUseCase;
    private final ProjectRepository projectRepository;
    private final ObjectMapper objectMapper;


    public GenerateBudgetAllocationPlanUseCase(
            UserRepository userRepository,
            ProjectRepository projectRepository,
            TransactionRepository transactionRepository,
            ProcessQueueRequestUseCase processQueueRequestUseCase,
            ObjectMapper objectMapper
    ) {
        this.userRepository = userRepository;
        this.projectRepository = projectRepository;
        this.transactionRepository = transactionRepository;
        this.processQueueRequestUseCase = processQueueRequestUseCase;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public MessageRecord execute(UserId userId) {
        UUID uid = userId.value();

        User user = userRepository.findById(uid)
                .orElseThrow(() -> new DomainException(
                        "User not found",
                        BudgetErrorCode.USER_NOT_FOUND
                ));

        validateUserSetup(user);

        YearMonth currentMonth = YearMonth.now();

        BigDecimal activeProjectCommitment = calculateActiveProjectMonthlyCommitment(userId);

        BigDecimal safeSpending = user.calculateSafeSpending(activeProjectCommitment);

        Map<String, Object> payload = new LinkedHashMap<>();

        payload.put("user_id", uid.toString());
        payload.put("month", currentMonth.toString());

        Map<String, Object> financialSetup = new LinkedHashMap<>();

        financialSetup.put("safe_spending", safeSpending);


        financialSetup.put("savingPace", user.getSavingPace().name());

        financialSetup.put("interventionLevel", user.getInterventionLevel().name());

        financialSetup.put("focusMode", user.getFocusMode().name());

        payload.put("financialSetup", financialSetup);
        /*
         * Có transaction history thì gửi để AI học theo thói quen chi tiêu.
         * Không có history thì AI vẫn có thể dựa trên income + financial_setup.
         */
        buildLlmSpendingHistory(uid, currentMonth)
                .ifPresent(spendingHistory ->
                        payload.put("spendingHistory", spendingHistory)
                );

        try {
            String payloadJson = serializePayload(payload);

            return processQueueRequestUseCase.execute(payloadJson, DUTY, uid.toString());
        } catch (DomainException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new DomainException(
                    "Failed to build budget allocation AI payload",
                    BudgetErrorCode.AI_REQUEST_FAILED,
                    exception
            );
        }
    }

    private void validateUserSetup(User user) {
        if (!user.hasCompletedFinancialSetup()) {
            throw new DomainException(
                    "Please complete financial setup before generating a budget",
                    BudgetErrorCode.FINANCIAL_SETUP_REQUIRED
            );
        }
    }

    private Optional<Map<String, Object>> buildLlmSpendingHistory(
            UUID userId,
            YearMonth currentMonth
    ) {
        Optional<Map<String, Object>> rawFeatures =
                buildHistoryFeatures(userId, currentMonth);

        if (rawFeatures.isEmpty()) {
            return Optional.empty();
        }

        Map<String, Object> raw = rawFeatures.get();

        Map<String, Object> history = new LinkedHashMap<>();

        history.put(
                "monthsObserved",
                raw.get("months_history_count")
        );

        history.put(
                "lastMonthTotalSpend",
                raw.get("last_1m_total_spend")
        );

        history.put(
                "averageMonthlySpend",
                raw.get("total_spend_avg_3m")
        );

        Map<String, Object> categories = new LinkedHashMap<>();

        for (Category category : Category.values()) {
            String categoryName = category.name();

            Object lastMonthRatio =
                    raw.get("last_1m_" + categoryName + "_ratio");

            Object averageRatio =
                    raw.get("avg_3m_" + categoryName + "_ratio");

            if (lastMonthRatio == null || averageRatio == null) {
                continue;
            }

            double lastRatio = ((Number) lastMonthRatio).doubleValue();
            double avgRatio = ((Number) averageRatio).doubleValue();

            // Không gửi category hoàn toàn không có dữ liệu.
            if (lastRatio == 0.0 && avgRatio == 0.0) {
                continue;
            }

            Map<String, Object> categoryData = new LinkedHashMap<>();
            categoryData.put("lastMonthRatio", lastRatio);
            categoryData.put("averageRatio", avgRatio);

            categories.put(categoryName, categoryData);
        }

        history.put("categories", categories);

        return Optional.of(history);
    }

    private Optional<Map<String, Object>> buildHistoryFeatures(
            UUID userId,
            YearMonth currentMonth
    ) {
        YearMonth firstHistoryMonth = currentMonth.minusMonths(3);

        YearMonth lastHistoryMonth = currentMonth.minusMonths(1);

        LocalDateTime fromDate = firstHistoryMonth
                .atDay(1)
                .atStartOfDay();

        LocalDateTime toDate = currentMonth
                .atDay(1)
                .atStartOfDay();

        List<Transaction> transactions = transactionRepository.findByUserIdWithFilters(
                userId,
                null,
                null,
                fromDate,
                toDate,
                null,
                TransactionType.EXPENSE
        );

        if (transactions == null || transactions.isEmpty()) {
            return Optional.empty();
        }

        /*
         * Khởi tạo đủ ba tháng, kể cả tháng không có giao dịch.
         *
         * YearMonth -> Category -> tổng tiền.
         */
        Map<YearMonth, EnumMap<Category, BigDecimal>> monthlyCategoryAmounts = new LinkedHashMap<>();

        for (int monthOffset = 0; monthOffset < 3; monthOffset++) {
            YearMonth month = firstHistoryMonth.plusMonths(monthOffset);
            monthlyCategoryAmounts.put(month, createEmptyCategoryMap());
        }

        for (Transaction transaction : transactions) {
            if (!isValidExpenseTransaction(transaction)) {
                continue;
            }

            YearMonth transactionMonth = YearMonth.from(transaction.getDate());

            /*
             * Defensive check trong trường hợp repository
             * trả dữ liệu ngoài khoảng yêu cầu.
             */
            if (transactionMonth.isBefore(firstHistoryMonth)
                    || transactionMonth.isAfter(lastHistoryMonth)) {
                continue;
            }

            BigDecimal amount = transaction
                    .getAmount()
                    .getValue()
                    .abs();

            monthlyCategoryAmounts.get(transactionMonth)
                    .merge(
                            transaction.getCategory(),
                            amount,
                            BigDecimal::add
                    );
        }

        /*
         * Tính tổng tiền từng tháng.
         */
        Map<YearMonth, BigDecimal> monthlyTotals = new LinkedHashMap<>();

        int monthsHistoryCount = 0;

        for (Map.Entry<YearMonth, EnumMap<Category, BigDecimal>> entry
                : monthlyCategoryAmounts.entrySet()) {

            BigDecimal monthlyTotal = entry.getValue()
                    .values()
                    .stream()
                    .reduce(
                            BigDecimal.ZERO,
                            BigDecimal::add
                    );

            monthlyTotals.put(entry.getKey(), monthlyTotal);

            if (monthlyTotal.compareTo(BigDecimal.ZERO) > 0) {
                monthsHistoryCount++;
            }
        }

        if (monthsHistoryCount == 0) {
            return Optional.empty();
        }

        Map<String, Object> historyFeatures = new LinkedHashMap<>();

        historyFeatures.put("has_transaction_history", 1);
        historyFeatures.put("months_history_count", monthsHistoryCount);

        /*
         * Với mỗi category, tính:
         *
         * - ratio của tháng gần nhất
         * - average ratio trong các tháng có dữ liệu
         * - standard deviation trong các tháng có dữ liệu
         */
        for (Category category : Category.values()) {
            List<Double> categoryRatios = new ArrayList<>();

            for (Map.Entry<YearMonth, EnumMap<Category, BigDecimal>> entry
                    : monthlyCategoryAmounts.entrySet()) {

                YearMonth month = entry.getKey();

                BigDecimal monthlyTotal = monthlyTotals.getOrDefault(
                        month,
                        BigDecimal.ZERO
                );
                /*
                 * Chỉ đưa tháng có transaction vào phép tính
                 * avg/std.
                 */
                if (monthlyTotal.compareTo(BigDecimal.ZERO) <= 0) {
                    continue;
                }

                BigDecimal categoryAmount = entry.getValue()
                        .getOrDefault(
                                category,
                                BigDecimal.ZERO
                        );

                double ratio = categoryAmount.divide(
                                monthlyTotal,
                                8,
                                RoundingMode.HALF_UP
                        )
                        .doubleValue();

                categoryRatios.add(ratio);
            }

            /*
             * Ratio tháng gần nhất.
             *
             * Nếu tháng gần nhất không có transaction,
             * ratio bằng 0.
             */
            BigDecimal lastMonthTotal = monthlyTotals.getOrDefault(
                    lastHistoryMonth,
                    BigDecimal.ZERO
            );

            BigDecimal lastMonthCategoryAmount = monthlyCategoryAmounts
                    .get(lastHistoryMonth)
                    .getOrDefault(
                            category,
                            BigDecimal.ZERO
                    );

            double lastMonthRatio = 0.0;

            if (lastMonthTotal.compareTo(BigDecimal.ZERO) > 0) {
                lastMonthRatio = lastMonthCategoryAmount
                        .divide(
                                lastMonthTotal,
                                8,
                                RoundingMode.HALF_UP
                        )
                        .doubleValue();
            }

            double averageRatio = calculateAverage(categoryRatios);
            double ratioStandardDeviation = calculatePopulationStandardDeviation(categoryRatios);

            historyFeatures.put("last_1m_" + category.name() + "_ratio", roundFeature(lastMonthRatio));
            historyFeatures.put("avg_3m_" + category.name() + "_ratio", roundFeature(averageRatio));
            historyFeatures.put("std_3m_" + category.name() + "_ratio", roundFeature(ratioStandardDeviation));
        }

        /*
         * Các feature tổng tiền.
         */
        BigDecimal lastMonthTotal = monthlyTotals.getOrDefault(
                lastHistoryMonth,
                BigDecimal.ZERO
        );

        List<Double> monthlySpendingValues = monthlyTotals
                .values()
                .stream()
                .filter(total ->
                        total.compareTo(BigDecimal.ZERO) > 0
                )
                .map(BigDecimal::doubleValue)
                .toList();

        double averageMonthlySpend = calculateAverage(
                monthlySpendingValues
        );

        double monthlySpendStandardDeviation = calculatePopulationStandardDeviation(
                monthlySpendingValues
        );

        historyFeatures.put("last_1m_total_spend", lastMonthTotal.doubleValue());
        historyFeatures.put("total_spend_avg_3m", roundMoneyFeature(averageMonthlySpend));
        historyFeatures.put("total_spend_std_3m", roundMoneyFeature(monthlySpendStandardDeviation));

        return Optional.of(historyFeatures);
    }


    private EnumMap<Category, BigDecimal> createEmptyCategoryMap() {
        EnumMap<Category, BigDecimal> categoryAmounts = new EnumMap<>(Category.class);

        for (Category category : Category.values()) {
            categoryAmounts.put(category, BigDecimal.ZERO);
        }

        return categoryAmounts;
    }

    private boolean isValidExpenseTransaction(Transaction transaction) {
        if (transaction == null
                || transaction.getType() == null
                || transaction.getDate() == null
                || transaction.getCategory() == null
                || transaction.getAmount() == null
                || transaction.getAmount().getValue() == null) {
            return false;
        }

        if (transaction.getType()
                != TransactionType.EXPENSE) {
            return false;
        }

        return transaction
                .getAmount()
                .getValue()
                .abs()
                .compareTo(BigDecimal.ZERO) > 0;
    }

    private double calculateAverage(List<Double> values) {
        if (values == null || values.isEmpty()) {
            return 0.0;
        }

        return values
                .stream()
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(0.0);
    }

    /**
     * Population standard deviation, tương đương numpy.std
     * với ddof=0.
     *
     * Phải bảo đảm logic này giống với feature engineering
     * đã sử dụng khi train history model.
     */
    private double calculatePopulationStandardDeviation(List<Double> values) {
        if (values == null || values.size() <= 1) {
            return 0.0;
        }

        double mean = calculateAverage(values);

        double variance = values
                .stream()
                .mapToDouble(value -> {
                    double difference =
                            value - mean;

                    return difference * difference;
                })
                .average()
                .orElse(0.0);

        return Math.sqrt(variance);
    }

    private double roundFeature(double value) {
        return BigDecimal
                .valueOf(value)
                .setScale(
                        8,
                        RoundingMode.HALF_UP
                )
                .doubleValue();
    }

    private double roundMoneyFeature(double value) {
        return BigDecimal
                .valueOf(value)
                .setScale(
                        2,
                        RoundingMode.HALF_UP
                )
                .doubleValue();
    }

    private String serializePayload(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(
                    payload
            );
        } catch (JsonProcessingException exception) {
            throw new RuntimeException(
                    "Failed to serialize budget allocation AI payload",
                    exception
            );
        }
    }

    private String toAiValue(Enum<?> value) {
        if (value == null) {
            return null;
        }

        return value.name().toLowerCase(Locale.ROOT);
    }

    private BigDecimal calculateActiveProjectMonthlyCommitment(
            UserId userId
    ) {
        ProjectFilterRequest activeProjectFilter =
                new ProjectFilterRequest(
                        null, // search
                        null, // type
                        ProjectStatus.ACTIVE,
                        null  // priority
                );

        List<Project> activeProjects =
                projectRepository.findAllByUser(
                        userId,
                        activeProjectFilter
                );

        return activeProjects.stream()
                .map(Project::calculateMonthlyCommitment)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}