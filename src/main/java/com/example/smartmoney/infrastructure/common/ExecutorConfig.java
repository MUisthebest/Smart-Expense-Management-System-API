package com.example.smartmoney.infrastructure.common;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Configuration
public class ExecutorConfig {

    @Bean
    public ExecutorService notificationExecutor() {
        return Executors.newFixedThreadPool(1); // 👈 chỉnh tùy server
    }
}