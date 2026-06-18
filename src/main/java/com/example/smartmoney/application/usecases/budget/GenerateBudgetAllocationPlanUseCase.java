package com.example.smartmoney.application.usecases.budget;

import com.example.smartmoney.application.usecases.queue.ProcessQueueRequestUseCase;
import com.example.smartmoney.domain.exceptions.ProjectErrorCode;
import com.example.smartmoney.domain.exceptions.ProjectException;
import com.example.smartmoney.domain.models.message.MessageRecord;
import com.example.smartmoney.domain.models.project.valueobjects.UserId;
import com.example.smartmoney.domain.repositories.UserRepository;
import com.example.smartmoney.infrastructure.persistence.entities.UserFinancialProfileEntity;
import com.example.smartmoney.infrastructure.persistence.entities.UserIncomeEntity;
import com.example.smartmoney.infrastructure.persistence.repositories.JpaTransactionRepository;
import com.example.smartmoney.infrastructure.persistence.repositories.JpaUserFinancialProfileRepository;
import com.example.smartmoney.infrastructure.persistence.repositories.JpaUserIncomeRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.YearMonth;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class GenerateBudgetAllocationPlanUseCase {

    private static final String DUTY = "BUDGET_ALLOCATION_PLAN";

    private final JpaUserIncomeRepository userIncomeRepository;
    private final JpaUserFinancialProfileRepository userFinancialProfileRepository;
    private final JpaTransactionRepository transactionRepository;
    private final UserRepository userRepository;
    private final ProcessQueueRequestUseCase processQueueRequestUseCase;
    private final ObjectMapper objectMapper;

    public GenerateBudgetAllocationPlanUseCase(
            JpaUserIncomeRepository userIncomeRepository,
            JpaUserFinancialProfileRepository userFinancialProfileRepository,
            JpaTransactionRepository transactionRepository,
            UserRepository userRepository,
            ProcessQueueRequestUseCase processQueueRequestUseCase,
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
                .orElseThrow(() -> new ProjectException("User not found", ProjectErrorCode.PROJECT_PERMISSION_DENIED));

        if (!user.isIncomeSetupCompleted()) {
            throw new ProjectException(
                    "Please set up your income profile before using Budget AI",
                    ProjectErrorCode.INCOME_PROFILE_REQUIRED);
        }

        if (!user.isFinancialSetupCompleted()) {
            throw new ProjectException(
                    "Please set up your financial profile before using Budget AI",
                    ProjectErrorCode.FINANCIAL_PROFILE_REQUIRED);
        }

        UserIncomeEntity income = userIncomeRepository.findByUserId(uid)
                .orElseThrow(() -> new RuntimeException("User income not found"));

        UserFinancialProfileEntity profile = userFinancialProfileRepository.findByUserId(uid)
                .orElseThrow(() -> new RuntimeException("User financial profile not found"));

        YearMonth currentMonth = YearMonth.now();

        Map<String, Object> historyFeatures = buildHistoryFeatures(uid, currentMonth);

        Map<String, Object> payload = Map.of(
                "user_id", uid.toString(),
                "safe_spending", income.getSafeSpending(),
                "currency", income.getCurrency(),
                "month", currentMonth.toString(),

                "income_info", Map.of(
                        "net_income", income.getNetIncome(),
                        "usable_income", income.getUsableIncome(),
                        "safe_spending", income.getSafeSpending(),
                        "auto_invest_surplus", income.getAutoInvestSurplus()
                ),

                "user_profile", Map.of(
                        "role", toAiValue(profile.getRole()),
                        "living_status", toAiValue(profile.getLivingStatus()),
                        "income_level", toAiValue(profile.getIncomeLevel()),
                        "transport_mode", toAiValue(profile.getTransportMode()),
                        "spending_style", toAiValue(profile.getSpendingStyle()),
                        "work_style", toAiValue(profile.getWorkStyle()),
                        "family_status", toAiValue(profile.getFamilyStatus()),
                        "study_intensity", toAiValue(profile.getStudyIntensity()),
                        "health_need", toAiValue(profile.getHealthNeed())
                ),

                "history_features", historyFeatures
        );

        try {
            String payloadJson = objectMapper.writeValueAsString(payload);

            return processQueueRequestUseCase.execute(
                    payloadJson,
                    DUTY,
                    uid.toString()
            );

        } catch (Exception e) {
            throw new RuntimeException("Failed to build budget allocation AI payload", e);
        }
    }

    private String toAiValue(Enum<?> value) {
        return value.name().toLowerCase(Locale.ROOT);
    }

    private Map<String, Object> buildHistoryFeatures(UUID userId, YearMonth currentMonth) {
        // Tạm thời có thể để default trước để test flow BE → Redis → AI.
        // Sau đó thay bằng logic tính avg_3m_ratio từ transaction history.

        return Map.of(
                "FOOD_avg_3m_ratio", 0.28,
                "CLOTHING_avg_3m_ratio", 0.02,
                "TRANSPORTATION_avg_3m_ratio", 0.10,
                "SHOPPING_avg_3m_ratio", 0.15,
                "ENTERTAINMENT_avg_3m_ratio", 0.08,
                "UTILITIES_avg_3m_ratio", 0.14,
                "HEALTH_avg_3m_ratio", 0.05,
                "EDUCATION_avg_3m_ratio", 0.08,
                "OTHER_avg_3m_ratio", 0.10
        );
    }
}
