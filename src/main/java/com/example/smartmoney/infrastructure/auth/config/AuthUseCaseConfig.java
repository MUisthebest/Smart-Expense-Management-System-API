package com.example.smartmoney.infrastructure.auth.config;

import com.example.smartmoney.application.ports.auth.AuthPort;
import com.example.smartmoney.application.ports.auth.ExternalOAuthPort;
import com.example.smartmoney.application.usecases.auth.FacebookLoginUseCase;
import com.example.smartmoney.application.usecases.auth.GoogleLoginUseCase;
import com.example.smartmoney.domain.repositories.UserRepository;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AuthUseCaseConfig {

    private final FacebookOAuthProperties facebookOAuthProperties;
    private final GoogleOAuthProperties googleOAuthProperties;

    public AuthUseCaseConfig(FacebookOAuthProperties facebookOAuthProperties, GoogleOAuthProperties googleOAuthProperties) {
        this.facebookOAuthProperties = facebookOAuthProperties;
        this.googleOAuthProperties = googleOAuthProperties;
    }

    @Bean
    @ConditionalOnProperty(name = "app.facebook.app-secret")
    public FacebookLoginUseCase facebookLoginUseCase(
            UserRepository userRepository,
            AuthPort authPort,
            ExternalOAuthPort externalOAuthPort
    ) {
        return new FacebookLoginUseCase(
                userRepository,
                authPort,
                externalOAuthPort
        );
    }

    @Bean
    @ConditionalOnBean(ExternalOAuthPort.class)
    @ConditionalOnProperty(name = "app.google.oauth2.client-id")
    public GoogleLoginUseCase googleLoginUseCase(
            UserRepository userRepository,
            AuthPort authPort,
            ExternalOAuthPort externalOAuthPort
    ) {
        return new GoogleLoginUseCase(
                userRepository,
                authPort,
                externalOAuthPort
        );
    }
}
