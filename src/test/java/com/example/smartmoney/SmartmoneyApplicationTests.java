package com.example.smartmoney;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.mail.javamail.JavaMailSender;

import com.cloudinary.Cloudinary;
import com.example.smartmoney.application.ports.auth.ExternalOAuthPort;
import com.example.smartmoney.application.usecases.auth.GoogleLoginUseCase;
import com.example.smartmoney.infrastructure.auth.external.SupabaseJwtValidator;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
class SmartmoneyApplicationTests {

	@MockitoBean
	private GoogleLoginUseCase googleLoginUseCase;

	@MockitoBean
	private ExternalOAuthPort externalOAuthPort;

	@MockitoBean
	private SupabaseJwtValidator supabaseJwtValidator;

	@MockitoBean
	private Cloudinary cloudinary;

    @MockitoBean
    private JavaMailSender javaMailSender;

	@Test
	@org.junit.jupiter.api.Disabled("Context loading requires all external service configurations - use specific unit tests instead")
	void contextLoads() {
	}

}
