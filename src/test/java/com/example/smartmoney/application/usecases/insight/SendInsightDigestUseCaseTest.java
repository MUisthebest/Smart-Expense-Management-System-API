package com.example.smartmoney.application.usecases.insight;

import com.example.smartmoney.application.dtos.responses.insight.InsightResponse;
import com.example.smartmoney.application.usecases.notification.CreateNotificationUseCase;
import com.example.smartmoney.domain.models.insight.Severity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("SendInsightDigestUseCase — batched weekly digest (P2-F)")
class SendInsightDigestUseCaseTest {

    @Mock private GenerateInsightsUseCase generateInsightsUseCase;
    @Mock private CreateNotificationUseCase createNotificationUseCase;

    @InjectMocks private SendInsightDigestUseCase useCase;

    private final UUID userId = UUID.randomUUID();
    private static final LocalDate AS_OF = LocalDate.of(2026, 6, 15);

    private InsightResponse insight(Severity severity) {
        return InsightResponse.builder().severity(severity).build();
    }

    @Test
    @DisplayName("batches >= WARN insights into one digest and reports it was sent")
    void sendsDigestWhenActionable() {
        when(generateInsightsUseCase.execute(userId, AS_OF)).thenReturn(List.of(
                insight(Severity.INFO), insight(Severity.WARN), insight(Severity.CRITICAL)));

        boolean sent = useCase.sendFor(userId, AS_OF);

        assertTrue(sent);
        // only the 2 actionable (WARN + CRITICAL) are counted
        verify(createNotificationUseCase).createNotification(
                eq(userId), eq("notification.digest.weekly|2"), eq("app://insights"));
    }

    @Test
    @DisplayName("no actionable insights → no notification, reports nothing sent")
    void noDigestWhenOnlyInfo() {
        when(generateInsightsUseCase.execute(userId, AS_OF))
                .thenReturn(List.of(insight(Severity.INFO)));

        boolean sent = useCase.sendFor(userId, AS_OF);

        assertFalse(sent);
        verify(createNotificationUseCase, never()).createNotification(any(), any(), any());
    }

    @Test
    @DisplayName("no insights at all → no notification")
    void noDigestWhenEmpty() {
        when(generateInsightsUseCase.execute(userId, AS_OF)).thenReturn(List.of());

        assertFalse(useCase.sendFor(userId, AS_OF));
        verify(createNotificationUseCase, never()).createNotification(any(), any(), any());
    }
}
