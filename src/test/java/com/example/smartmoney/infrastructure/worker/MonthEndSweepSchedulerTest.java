package com.example.smartmoney.infrastructure.worker;

import com.example.smartmoney.application.usecases.insight.SuggestionProducer;
import com.example.smartmoney.domain.models.user.User;
import com.example.smartmoney.domain.repositories.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("MonthEndSweepScheduler — per-user fan-out into the existing savings producer")
class MonthEndSweepSchedulerTest {

    @Mock private UserRepository userRepository;
    @Mock private SuggestionProducer suggestionProducer;

    @Test
    @DisplayName("sweeps every eligible user even when one blows up")
    void sweepsEveryUserDespiteFailure() {
        UUID failing = UUID.randomUUID();
        UUID healthy = UUID.randomUUID();
        when(userRepository.findAllForDigest()).thenReturn(List.of(
                User.builder().id(failing).build(), User.builder().id(healthy).build()));
        doThrow(new RuntimeException("boom"))
                .when(suggestionProducer).maybeProduceSavingsContribution(eq(failing), any(LocalDate.class));

        new MonthEndSweepScheduler(userRepository, suggestionProducer).runMonthEndSweep();

        verify(suggestionProducer).maybeProduceSavingsContribution(eq(healthy), any(LocalDate.class));
    }
}
