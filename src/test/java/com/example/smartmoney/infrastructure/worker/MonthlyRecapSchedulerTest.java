package com.example.smartmoney.infrastructure.worker;

import com.example.smartmoney.application.usecases.insight.SendMonthlyRecapUseCase;
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
@DisplayName("MonthlyRecapScheduler — per-user fan-out")
class MonthlyRecapSchedulerTest {

    @Mock private UserRepository userRepository;
    @Mock private SendMonthlyRecapUseCase sendMonthlyRecapUseCase;

    @Test
    @DisplayName("one user's failure does not abort the batch")
    void oneFailureDoesNotAbortBatch() {
        UUID failing = UUID.randomUUID();
        UUID healthy = UUID.randomUUID();
        when(userRepository.findAllForDigest()).thenReturn(List.of(
                User.builder().id(failing).build(), User.builder().id(healthy).build()));
        when(sendMonthlyRecapUseCase.sendFor(eq(failing), any(LocalDate.class)))
                .thenThrow(new RuntimeException("boom"));
        when(sendMonthlyRecapUseCase.sendFor(eq(healthy), any(LocalDate.class))).thenReturn(true);

        new MonthlyRecapScheduler(userRepository, sendMonthlyRecapUseCase).runMonthlyRecap();

        verify(sendMonthlyRecapUseCase).sendFor(eq(healthy), any(LocalDate.class));
    }
}
