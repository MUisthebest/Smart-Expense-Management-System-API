package com.example.smartmoney.application.usecases.insight;

import com.example.smartmoney.application.usecases.notification.CreateNotificationUseCase;
import com.example.smartmoney.domain.models.budget.Budget;
import com.example.smartmoney.domain.models.project.Project;
import com.example.smartmoney.domain.models.project.enums.ProjectPriority;
import com.example.smartmoney.domain.models.project.enums.ProjectType;
import com.example.smartmoney.domain.models.project.valueobjects.Money;
import com.example.smartmoney.domain.models.project.valueobjects.UserId;
import com.example.smartmoney.domain.models.suggestion.SuggestionTopicStat;
import com.example.smartmoney.domain.models.transaction.enums.Category;
import com.example.smartmoney.domain.models.user.User;
import com.example.smartmoney.domain.models.user.enums.Intervention_Level;
import com.example.smartmoney.domain.repositories.BudgetRepository;
import com.example.smartmoney.domain.repositories.SuggestionTopicStatRepository;
import com.example.smartmoney.domain.repositories.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("PositiveReinforcementUseCase — milestone & good-month cheers (Wave 4)")
class PositiveReinforcementUseCaseTest {

    @Mock private UserRepository userRepository;
    @Mock private BudgetRepository budgetRepository;
    @Mock private SuggestionTopicStatRepository topicStatRepository;
    @Mock private CreateNotificationUseCase createNotificationUseCase;
    @Mock private InsightNarrator narrator; // narrate() null by default → push uses the i18n code

    private PositiveReinforcementUseCase useCase;
    private final UUID userId = UUID.randomUUID();
    private static final YearMonth CLOSED = YearMonth.of(2026, 6);

    @BeforeEach
    void setUp() {
        Clock fixed = Clock.fixed(Instant.parse("2026-07-01T09:00:00Z"), ZoneOffset.UTC);
        useCase = new PositiveReinforcementUseCase(userRepository, budgetRepository,
                topicStatRepository, createNotificationUseCase, narrator, fixed);
    }

    private void stubUser(Intervention_Level level) {
        when(userRepository.findById(userId))
                .thenReturn(Optional.of(User.builder().id(userId).interventionLevel(level).build()));
    }

    private Project project(String target) {
        return Project.createNew(UserId.of(userId.toString()), ProjectType.PERSONAL,
                ProjectPriority.MEDIUM, "Trip", null,
                new Money(new BigDecimal(target), "VND"), LocalDate.now().plusMonths(6));
    }

    private Budget budget(Category category, String limit, String spent) {
        Budget b = Budget.create(userId, category, new BigDecimal(limit),
                CLOSED.getMonthValue(), CLOSED.getYear());
        b.setSpent(new BigDecimal(spent));
        return b;
    }

    // ---- PROJECT_MILESTONE -----------------------------------------------------------------

    @Test
    @DisplayName("crossing 25% → one push deep-linked to the project, and the milestone is marked")
    void milestoneCrossingCelebratesOnce() {
        stubUser(Intervention_Level.GENTLE);
        when(topicStatRepository.find(eq(userId), any())).thenReturn(Optional.empty());
        Project p = project("1000");

        useCase.maybeCelebrateMilestone(userId, p, new BigDecimal("0.00"), new BigDecimal("30.00"));

        verify(createNotificationUseCase).createNotification(
                eq(userId), eq("notification.insight.project_milestone|Trip"),
                eq("app://projects/" + p.getId().value()));
        ArgumentCaptor<SuggestionTopicStat> marker = ArgumentCaptor.forClass(SuggestionTopicStat.class);
        verify(topicStatRepository).save(marker.capture());
        assertEquals("MILESTONE:" + p.getId().value() + ":25", marker.getValue().getDedupKey());
    }

    @Test
    @DisplayName("a single jump over several milestones celebrates only the highest one")
    void bigJumpCelebratesHighestMilestone() {
        stubUser(Intervention_Level.GENTLE);
        when(topicStatRepository.find(eq(userId), any())).thenReturn(Optional.empty());
        Project p = project("1000");

        useCase.maybeCelebrateMilestone(userId, p, new BigDecimal("10.00"), new BigDecimal("80.00"));

        ArgumentCaptor<SuggestionTopicStat> marker = ArgumentCaptor.forClass(SuggestionTopicStat.class);
        verify(topicStatRepository).save(marker.capture());
        assertEquals("MILESTONE:" + p.getId().value() + ":75", marker.getValue().getDedupKey());
        verify(createNotificationUseCase, times(1)).createNotification(any(), any(), any());
    }

    @Test
    @DisplayName("reaching 100% (completion) is a celebrated milestone too")
    void completionIsAMilestone() {
        stubUser(Intervention_Level.HARD); // positives are presence-of-push gated: HARD celebrates
        when(topicStatRepository.find(eq(userId), any())).thenReturn(Optional.empty());
        Project p = project("1000");

        useCase.maybeCelebrateMilestone(userId, p, new BigDecimal("90.00"), new BigDecimal("100.00"));

        ArgumentCaptor<SuggestionTopicStat> marker = ArgumentCaptor.forClass(SuggestionTopicStat.class);
        verify(topicStatRepository).save(marker.capture());
        assertEquals("MILESTONE:" + p.getId().value() + ":100", marker.getValue().getDedupKey());
    }

    @Test
    @DisplayName("an already-celebrated milestone stays celebrated (marker suppresses the repeat)")
    void milestoneNeverRepeats() {
        stubUser(Intervention_Level.GENTLE);
        when(topicStatRepository.find(eq(userId), any()))
                .thenReturn(Optional.of(SuggestionTopicStat.start(userId, "MILESTONE:x:25")));

        useCase.maybeCelebrateMilestone(userId, project("1000"),
                new BigDecimal("20.00"), new BigDecimal("30.00"));

        verify(createNotificationUseCase, never()).createNotification(any(), any(), any());
        verify(topicStatRepository, never()).save(any());
    }

    @Test
    @DisplayName("no milestone crossed → nothing")
    void noCrossingNoCheer() {
        stubUser(Intervention_Level.GENTLE);

        useCase.maybeCelebrateMilestone(userId, project("1000"),
                new BigDecimal("30.00"), new BigDecimal("40.00"));

        verify(createNotificationUseCase, never()).createNotification(any(), any(), any());
    }

    @Test
    @DisplayName("NOTIFY (pull-only) user: no celebration, no marker")
    void notifyUserGetsNoCheer() {
        stubUser(Intervention_Level.NOTIFY);

        useCase.maybeCelebrateMilestone(userId, project("1000"),
                new BigDecimal("0.00"), new BigDecimal("50.00"));

        verify(createNotificationUseCase, never()).createNotification(any(), any(), any());
        verify(topicStatRepository, never()).save(any());
    }

    // ---- GOOD_MONTH --------------------------------------------------------------------------

    @Test
    @DisplayName("every budget under its limit → one cheer, marked for the month")
    void goodMonthCelebrated() {
        stubUser(Intervention_Level.GENTLE);
        when(topicStatRepository.find(userId, "GOOD_MONTH:" + CLOSED)).thenReturn(Optional.empty());
        when(budgetRepository.findByUserIdMonthYear(userId, CLOSED.getMonthValue(), CLOSED.getYear()))
                .thenReturn(List.of(
                        budget(Category.FOOD, "3000000", "2500000"),
                        budget(Category.ENTERTAINMENT, "1000000", "1000000"))); // at the limit still counts

        assertTrue(useCase.maybeCelebrateGoodMonth(userId, CLOSED));

        verify(createNotificationUseCase).createNotification(
                eq(userId), eq("notification.insight.good_month"), eq("app://budgets"));
        ArgumentCaptor<SuggestionTopicStat> marker = ArgumentCaptor.forClass(SuggestionTopicStat.class);
        verify(topicStatRepository).save(marker.capture());
        assertEquals("GOOD_MONTH:" + CLOSED, marker.getValue().getDedupKey());
    }

    @Test
    @DisplayName("one blown budget vetoes the cheer")
    void blownBudgetVetoesGoodMonth() {
        stubUser(Intervention_Level.GENTLE);
        when(topicStatRepository.find(userId, "GOOD_MONTH:" + CLOSED)).thenReturn(Optional.empty());
        when(budgetRepository.findByUserIdMonthYear(userId, CLOSED.getMonthValue(), CLOSED.getYear()))
                .thenReturn(List.of(
                        budget(Category.FOOD, "3000000", "2500000"),
                        budget(Category.ENTERTAINMENT, "1000000", "1200000")));

        assertFalse(useCase.maybeCelebrateGoodMonth(userId, CLOSED));

        verify(createNotificationUseCase, never()).createNotification(any(), any(), any());
    }

    @Test
    @DisplayName("no budgets that month → nothing was 'stayed under', no cheer")
    void noBudgetsNoGoodMonth() {
        stubUser(Intervention_Level.GENTLE);
        when(topicStatRepository.find(userId, "GOOD_MONTH:" + CLOSED)).thenReturn(Optional.empty());
        when(budgetRepository.findByUserIdMonthYear(userId, CLOSED.getMonthValue(), CLOSED.getYear()))
                .thenReturn(List.of());

        assertFalse(useCase.maybeCelebrateGoodMonth(userId, CLOSED));

        verify(createNotificationUseCase, never()).createNotification(any(), any(), any());
    }

    @Test
    @DisplayName("an already-celebrated month is skipped before budgets are even loaded (demo re-run)")
    void goodMonthNeverRepeats() {
        stubUser(Intervention_Level.GENTLE);
        when(topicStatRepository.find(userId, "GOOD_MONTH:" + CLOSED))
                .thenReturn(Optional.of(SuggestionTopicStat.start(userId, "GOOD_MONTH:" + CLOSED)));

        assertFalse(useCase.maybeCelebrateGoodMonth(userId, CLOSED));

        verify(budgetRepository, never()).findByUserIdMonthYear(any(), anyInt(), anyInt());
        verify(createNotificationUseCase, never()).createNotification(any(), any(), any());
    }
}
