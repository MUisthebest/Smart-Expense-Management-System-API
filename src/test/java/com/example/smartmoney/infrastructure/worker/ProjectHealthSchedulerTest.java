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
@DisplayName("ProjectHealthScheduler — per-user fan-out into the project-health producer")
class ProjectHealthSchedulerTest {

    @Mock private UserRepository userRepository;
    @Mock private SuggestionProducer suggestionProducer;

    @Test
    @DisplayName("checks every eligible user even when one blows up")
    void checksEveryUserDespiteFailure() {
        UUID failing = UUID.randomUUID();
        UUID healthy = UUID.randomUUID();
        when(userRepository.findAllForDigest()).thenReturn(List.of(
                User.builder().id(failing).build(), User.builder().id(healthy).build()));
        doThrow(new RuntimeException("boom"))
                .when(suggestionProducer).maybeProduceProjectHealth(eq(failing), any(LocalDate.class));

        new ProjectHealthScheduler(userRepository, suggestionProducer).runProjectHealthCheck();

        verify(suggestionProducer).maybeProduceProjectHealth(eq(healthy), any(LocalDate.class));
    }
}
