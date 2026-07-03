package com.example.smartmoney.application.usecases.insight;

import com.example.smartmoney.application.usecases.notification.CreateNotificationUseCase;
import com.example.smartmoney.domain.models.transaction.Transaction;
import com.example.smartmoney.domain.models.transaction.enums.Category;
import com.example.smartmoney.domain.models.transaction.enums.TransactionType;
import com.example.smartmoney.domain.models.transaction.valueobjects.Amount;
import com.example.smartmoney.domain.models.user.User;
import com.example.smartmoney.domain.repositories.TransactionRepository;
import com.example.smartmoney.domain.repositories.UserRepository;
import com.example.smartmoney.domain.services.SpendingInsightAnalyzer;
import com.example.smartmoney.domain.models.user.enums.Intervention_Level;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("SendMonthlyRecapUseCase — closed-month recap pings (Wave 3)")
class SendMonthlyRecapUseCaseTest {

    @Mock private TransactionRepository transactionRepository;
    @Mock private UserRepository userRepository;
    @Mock private CreateNotificationUseCase createNotificationUseCase;
    @Mock private InsightNarrator narrator; // narrate() null by default → push uses the i18n code
    @Mock private PositiveReinforcementUseCase positiveReinforcementUseCase;

    private SendMonthlyRecapUseCase useCase;
    private final UUID userId = UUID.randomUUID();
    /** Recap run on July 1st → the judged (closed) month is June 2026. */
    private static final LocalDate AS_OF = LocalDate.of(2026, 7, 1);
    private static final YearMonth CLOSED = YearMonth.of(2026, 6);

    @BeforeEach
    void setUp() {
        useCase = new SendMonthlyRecapUseCase(transactionRepository, userRepository,
                new SpendingInsightAnalyzer(), createNotificationUseCase, narrator,
                positiveReinforcementUseCase);
    }

    private void stubUser(Intervention_Level level) {
        when(userRepository.findById(userId))
                .thenReturn(Optional.of(User.builder().id(userId).interventionLevel(level).build()));
    }

    private Transaction income(int month, String amount) {
        return Transaction.create(userId, new Amount(new BigDecimal(amount)), TransactionType.INCOME,
                Category.OTHER, "salary", LocalDateTime.of(2026, month, 5, 12, 0));
    }

    private Transaction expense(int month, String amount) {
        return Transaction.create(userId, new Amount(new BigDecimal(amount)), TransactionType.EXPENSE,
                Category.FOOD, "spending", LocalDateTime.of(2026, month, 10, 12, 0));
    }

    /**
     * A bad closed June on a flat 5000 salary: only 2000 arrived (40% → WARN income drop) and
     * 3000 went out (ratio 1.5 → CRITICAL cashflow).
     */
    private List<Transaction> badJuneHistory() {
        List<Transaction> history = new ArrayList<>();
        for (int m = 3; m <= 5; m++) {
            history.add(income(m, "5000"));
        }
        history.add(income(6, "2000"));
        history.add(expense(6, "3000"));
        return history;
    }

    @Test
    @DisplayName("GENTLE user, bad month → both recap pings, deep-linked to transactions")
    void gentleUserGetsBothPings() {
        stubUser(Intervention_Level.GENTLE);
        when(transactionRepository.findByUserIdWithFilters(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(badJuneHistory());

        assertTrue(useCase.sendFor(userId, AS_OF));

        verify(createNotificationUseCase).createNotification(
                eq(userId), eq("notification.insight.income_drop"), eq("app://transactions"));
        verify(createNotificationUseCase).createNotification(
                eq(userId), eq("notification.insight.cashflow_negative"), eq("app://transactions"));
    }

    @Test
    @DisplayName("HARD (CRITICAL-only) user gets the CRITICAL cashflow ping but not the WARN income drop")
    void hardUserOnlyCriticalFindings() {
        stubUser(Intervention_Level.HARD);
        when(transactionRepository.findByUserIdWithFilters(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(badJuneHistory());

        assertTrue(useCase.sendFor(userId, AS_OF));

        verify(createNotificationUseCase).createNotification(
                eq(userId), eq("notification.insight.cashflow_negative"), eq("app://transactions"));
        verify(createNotificationUseCase, never()).createNotification(
                eq(userId), eq("notification.insight.income_drop"), any());
    }

    @Test
    @DisplayName("NOTIFY (pull-only) user: nothing sent, history never even loaded")
    void notifyUserGetsNothing() {
        stubUser(Intervention_Level.NOTIFY);

        assertFalse(useCase.sendFor(userId, AS_OF));

        verify(transactionRepository, never()).findByUserIdWithFilters(any(), any(), any(), any(), any(), any(), any());
        verify(createNotificationUseCase, never()).createNotification(any(), any(), any());
    }

    @Test
    @DisplayName("an unremarkable month → no pings, returns false")
    void healthyMonthSendsNothing() {
        stubUser(Intervention_Level.GENTLE);
        List<Transaction> history = new ArrayList<>();
        for (int m = 3; m <= 6; m++) {
            history.add(income(m, "5000"));
        }
        history.add(expense(6, "1000")); // well inside income
        when(transactionRepository.findByUserIdWithFilters(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(history);

        assertFalse(useCase.sendFor(userId, AS_OF));

        verify(createNotificationUseCase, never()).createNotification(any(), any(), any());
    }

    // ---- Wave 4 (item 9): GOOD_MONTH rides the recap ---------------------------------------

    @Test
    @DisplayName("no negative finding → the good-month check runs for the closed month")
    void healthyMonthOffersGoodMonthCheck() {
        stubUser(Intervention_Level.GENTLE);
        List<Transaction> history = new ArrayList<>();
        for (int m = 3; m <= 6; m++) {
            history.add(income(m, "5000"));
        }
        history.add(expense(6, "1000"));
        when(transactionRepository.findByUserIdWithFilters(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(history);
        when(positiveReinforcementUseCase.maybeCelebrateGoodMonth(userId, CLOSED)).thenReturn(true);

        assertTrue(useCase.sendFor(userId, AS_OF));

        verify(positiveReinforcementUseCase).maybeCelebrateGoodMonth(userId, CLOSED);
    }

    @Test
    @DisplayName("a negative finding exists (even if gated off) → no good-month check at all")
    void badMonthNeverChecksGoodMonth() {
        // HARD gate drops the WARN income-drop ping, but the bad facts still veto the cheer.
        stubUser(Intervention_Level.HARD);
        when(transactionRepository.findByUserIdWithFilters(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(badJuneHistory());

        useCase.sendFor(userId, AS_OF);

        verify(positiveReinforcementUseCase, never()).maybeCelebrateGoodMonth(any(), any());
    }
}
