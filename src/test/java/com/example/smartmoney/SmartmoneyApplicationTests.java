package com.example.smartmoney;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mail.javamail.JavaMailSender;

import com.example.smartmoney.application.usecases.auth.GoogleLoginUseCase;

import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
class SmartmoneyApplicationTests {

	@MockitoBean
	private GoogleLoginUseCase googleLoginUseCase;

    @MockitoBean
    private JavaMailSender javaMailSender;

	@Test
	void contextLoads() {
	}

}
