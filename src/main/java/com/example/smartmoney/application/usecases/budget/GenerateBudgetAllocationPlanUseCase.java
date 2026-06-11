package com.example.smartmoney.application.usecases.budget;

import com.example.smartmoney.application.usecases.queue.ProcessQueueRequestUseCase;
import com.example.smartmoney.domain.models.message.MessageRecord;
import com.example.smartmoney.domain.models.project.valueobjects.UserId;
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
    private final ProcessQueueRequestUseCase processQueueRequestUseCase;
    private final ObjectMapper objectMapper;

    public GenerateBudgetAllocationPlanUseCase(
            JpaUserIncomeRepository userIncomeRepository,
            JpaUserFinancialProfileRepository userFinancialProfileRepository,
            JpaTransactionRepository transactionRepository,
            ProcessQueueRequestUseCase processQueueRequestUseCase,
            ObjectMapper objectMapper
    ) {
        this.userIncomeRepository = userIncomeRepository;
        this.userFinancialProfileRepository = userFinancialProfileRepository;
        this.transactionRepository = transactionRepository;
        this.processQueueRequestUseCase = processQueueRequestUseCase;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public MessageRecord execute(UserId userId) {
        UUID uid = userId.value();

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
                "TRANSPORT_avg_3m_ratio", 0.12,
                "SHOPPING_avg_3m_ratio", 0.15,
                "ENTERTAINMENT_avg_3m_ratio", 0.08,
                "UTILITIES_avg_3m_ratio", 0.14,
                "HEALTH_avg_3m_ratio", 0.05,
                "EDUCATION_avg_3m_ratio", 0.08,
                "OTHER_avg_3m_ratio", 0.10
        );
    }
}
