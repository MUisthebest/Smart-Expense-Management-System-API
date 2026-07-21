package com.example.smartmoney.application.usecases.auth;

import com.example.smartmoney.application.ports.otp.OtpCachePort;
import com.example.smartmoney.domain.models.user.valueobjects.Email;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CheckOtpExistsUseCaseTest {

    @Mock
    private OtpCachePort otpCachePort;

    @InjectMocks
    private CheckOtpExistsUseCase checkOtpExistsUseCase;

    @Test
    void shouldReturnTrueWhenOtpExistsInRedis() {
        when(otpCachePort.exists(any(Email.class))).thenReturn(true);

        boolean exists = checkOtpExistsUseCase.execute("user@example.com");

        assertThat(exists).isTrue();
        verify(otpCachePort).exists(any(Email.class));
    }
}
