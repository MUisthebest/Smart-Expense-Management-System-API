package com.example.smartmoney;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.mail.javamail.JavaMailSender;

import com.cloudinary.Cloudinary;
import com.example.smartmoney.application.ports.auth.ExternalOAuthPort;
import com.example.smartmoney.application.usecases.auth.GoogleLoginUseCase;
import com.example.smartmoney.infrastructure.auth.external.SupabaseJwtValidator;
import com.example.smartmoney.infrastructure.config.kafka.KafkaJobConsumer;

import org.springframework.test.context.bean.override.mockito.MockitoBean;


@SpringBootTest(properties = {
    "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration"
})
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
	private KafkaJobConsumer kafkaJobConsumer;

    @MockitoBean
    private JavaMailSender javaMailSender;

	@Test
	@org.junit.jupiter.api.Disabled("Context loading requires all external service configurations - use specific unit tests instead")
	void contextLoads() {
	}

}
