package com.example.smartmoney.application.usecases.insight;

import com.example.smartmoney.application.ports.llm.LlmPort;
import com.example.smartmoney.domain.models.insight.Insight;
import com.example.smartmoney.domain.models.insight.InsightType;
import com.example.smartmoney.domain.models.insight.Severity;
import com.example.smartmoney.domain.models.transaction.enums.Category;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("InsightNarrator — LLM phrases, never computes; best-effort with code fallback")
class InsightNarratorTest {

    private Insight budgetPaceInsight() {
        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("spent", new BigDecimal("600.00"));
        metrics.put("limit", new BigDecimal("1000.00"));
        metrics.put("projectedEndOfMonth", new BigDecimal("1200.00"));
        return Insight.builder()
                .type(InsightType.BUDGET_PACE)
                .category(Category.FOOD)
                .severity(Severity.WARN)
                .period(YearMonth.of(2026, 6))
                .metrics(metrics)
                .code("insight.budget_pace.food")
                .build();
    }

    @Test
    @DisplayName("disabled → returns null and never touches the LLM (Phase-1 code fallback)")
    void disabledNeverCallsLlm() {
        LlmPort llm = mock(LlmPort.class);
        InsightNarrator narrator = new InsightNarrator(llm, false);

        assertNull(narrator.narrate(budgetPaceInsight()));
        verify(llm, never()).call(anyString());
    }

    @Test
    @DisplayName("LLM failure → null (the model outage never propagates)")
    void llmFailureFallsBackToNull() {
        LlmPort llm = mock(LlmPort.class);
        when(llm.call(anyString())).thenThrow(new RuntimeException("LLM service unavailable"));
        InsightNarrator narrator = new InsightNarrator(llm, true);

        assertNull(narrator.narrate(budgetPaceInsight()));
    }

    @Test
    @DisplayName("blank reply → null (nothing usable to show)")
    void blankReplyReturnsNull() {
        LlmPort llm = mock(LlmPort.class);
        when(llm.call(anyString())).thenReturn("   ");
        InsightNarrator narrator = new InsightNarrator(llm, true);

        assertNull(narrator.narrate(budgetPaceInsight()));
    }

    @Test
    @DisplayName("success → trimmed narrative")
    void successReturnsTrimmedNarrative() {
        LlmPort llm = mock(LlmPort.class);
        when(llm.call(anyString())).thenReturn("  Your food spending is on track to hit 1200 this month.  ");
        InsightNarrator narrator = new InsightNarrator(llm, true);

        assertEquals("Your food spending is on track to hit 1200 this month.",
                narrator.narrate(budgetPaceInsight()));
    }

    @Test
    @DisplayName("guardrail: the prompt hands the LLM the computed figures and forbids inventing numbers")
    void promptCarriesComputedFiguresAndForbidsInvention() {
        LlmPort llm = mock(LlmPort.class);
        when(llm.call(anyString())).thenReturn("ok");
        InsightNarrator narrator = new InsightNarrator(llm, true);

        narrator.narrate(budgetPaceInsight());

        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        verify(llm).call(promptCaptor.capture());
        String prompt = promptCaptor.getValue();

        // The exact pre-computed numbers are handed to the model (it phrases, it does not compute).
        assertTrue(prompt.contains("600.00"), prompt);
        assertTrue(prompt.contains("1000.00"), prompt);
        assertTrue(prompt.contains("1200.00"), prompt);
        // The stable i18n code travels with it.
        assertTrue(prompt.contains("insight.budget_pace.food"), prompt);
        // And the model is explicitly forbidden from inventing/recomputing figures.
        assertTrue(prompt.toLowerCase().contains("do not invent"), prompt);
    }
}
