package com.example.smartmoney.application.usecases.budget;

import com.example.smartmoney.application.usecases.queue.ProcessQueueRequestUseCase;
import com.example.smartmoney.domain.exceptions.ProjectErrorCode;
import com.example.smartmoney.domain.exceptions.ProjectException;
import com.example.smartmoney.domain.models.message.MessageRecord;
import com.example.smartmoney.domain.models.project.valueobjects.UserId;
import com.example.smartmoney.domain.models.transaction.Transaction;
import com.example.smartmoney.domain.models.transaction.enums.Category;
import com.example.smartmoney.domain.models.transaction.enums.TransactionType;
import com.example.smartmoney.domain.repositories.TransactionRepository;
import com.example.smartmoney.domain.repositories.UserFinancialProfileRepository;
import com.example.smartmoney.domain.repositories.UserIncomeRepository;
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

    private static final String DUTY =
            "BUDGET_ALLOCATION_PLAN";

    private final UserIncomeRepository userIncomeRepository;
    private final UserFinancialProfileRepository
            userFinancialProfileRepository;
    private final TransactionRepository transactionRepository;
    private final UserRepository userRepository;
    private final ProcessQueueRequestUseCase
            processQueueRequestUseCase;
    private final ObjectMapper objectMapper;

    public GenerateBudgetAllocationPlanUseCase(
            UserIncomeRepository userIncomeRepository,
            UserFinancialProfileRepository
                    userFinancialProfileRepository,
            TransactionRepository transactionRepository,
            UserRepository userRepository,
            ProcessQueueRequestUseCase
                    processQueueRequestUseCase,
            ObjectMapper objectMapper
    ) {
        this.userIncomeRepository = userIncomeRepository;
        this.userFinancialProfileRepository = userFinancialProfileRepository;
        this.transactionRepository = transactionRepository;
        this.userRepository = userRepository;
        this.processQueueRequestUseCase = processQueueRequestUseCase;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public MessageRecord execute(UserId userId) {
        UUID uid = userId.value();

        var user = userRepository.findById(uid)
                .orElseThrow(() -> new ProjectException(
                        "User not found",
                        ProjectErrorCode.PROJECT_PERMISSION_DENIED
                ));

        validateUserSetup(user);

        /*
         * Đây là domain model do domain repository trả về,
         * không phải UserIncomeEntity.
         */
        var income = userIncomeRepository
                .findByUserId(uid)
                .orElseThrow(() -> new ProjectException(
                        "User income not found",
                        ProjectErrorCode.INCOME_PROFILE_REQUIRED
                ));

        /*
         * Đây là domain model do domain repository trả về,
         * không phải UserFinancialProfileEntity.
         */
        var profile = userFinancialProfileRepository
                .findByUserId(uid)
                .orElseThrow(() -> new ProjectException(
                        "User financial profile not found",
                        ProjectErrorCode.FINANCIAL_PROFILE_REQUIRED
                ));

        YearMonth currentMonth = YearMonth.now();

        Map<String, Object> payload = new LinkedHashMap<>();

        payload.put("user_id", uid.toString());

        /*
         * Dù không có transaction history, AI vẫn cần
         * safe_spending để tính tổng ngân sách.
         */
        payload.put("safe_spending", income.getSafeSpending());
        payload.put("currency", income.getCurrency());
        payload.put("month", currentMonth.toString());

        Map<String, Object> incomeInfo = new LinkedHashMap<>();

        incomeInfo.put("net_income", income.getNetIncome());
        incomeInfo.put("usable_income", income.getUsableIncome());
        incomeInfo.put("safe_spending", income.getSafeSpending());
        incomeInfo.put("auto_invest_surplus", income.getAutoInvestSurplus());

        payload.put("income_info", incomeInfo);

        Map<String, Object> userProfile = new LinkedHashMap<>();

        userProfile.put("role", toAiValue(profile.getRole()));
        userProfile.put("living_status", toAiValue(profile.getLivingStatus()));
        userProfile.put("income_level", toAiValue(profile.getIncomeLevel()));
        userProfile.put("transport_mode", toAiValue(profile.getTransportMode()));
        userProfile.put("spending_style", toAiValue(profile.getSpendingStyle()));
        userProfile.put("work_style", toAiValue(profile.getWorkStyle()));
        userProfile.put("family_status", toAiValue(profile.getFamilyStatus()));
        userProfile.put("study_intensity", toAiValue(profile.getStudyIntensity()));
        userProfile.put("health_need", toAiValue(profile.getHealthNeed()));

        payload.put("user_profile", userProfile);

        /*
         * Có dữ liệu transaction:
         * thêm history_features.
         *
         * Không có transaction:
         * bỏ hoàn toàn history_features, AI sẽ dự đoán
         * dựa trên user_profile và income_info.
         */
        buildHistoryFeatures(uid, currentMonth)
                .ifPresent(historyFeatures ->
                payload.put(
                        "history_features",
                        historyFeatures
                )
        );

        String payloadJson = serializePayload(payload);

        return processQueueRequestUseCase.execute(
                payloadJson,
                DUTY,
                uid.toString()
        );
    }

    private void validateUserSetup(
            com.example.smartmoney.domain.models.user.User user
    ) {
        if (!user.isIncomeSetupCompleted()) {
            throw new ProjectException(
                    "Please set up your income profile before using Budget AI",
                    ProjectErrorCode.INCOME_PROFILE_REQUIRED
            );
        }

        if (!user.isFinancialSetupCompleted()) {
            throw new ProjectException(
                    "Please set up your financial profile before using Budget AI",
                    ProjectErrorCode.FINANCIAL_PROFILE_REQUIRED
            );
        }
    }

    /**
     * Tạo feature lịch sử đúng schema của history_budget_model.
     *
     * Ví dụ currentMonth = 2026-06:
     *
     * - tháng thứ nhất: 2026-03
     * - tháng thứ hai: 2026-04
     * - tháng gần nhất: 2026-05
     *
     * Không lấy dữ liệu tháng 06 vì tháng hiện tại chưa hoàn tất.
     */
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
}
