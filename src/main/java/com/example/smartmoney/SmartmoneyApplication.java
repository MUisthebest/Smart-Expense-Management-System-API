package com.example.smartmoney;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.info.License;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication
@EnableJpaRepositories
@OpenAPIDefinition(
    info = @Info(
        title = "Smart Money API",
        version = "1.0.0",
        description = "API for Smart Money application - Quản lý tài chính thông minh",
        contact = @Contact(
            name = "Smart Money Support",
            email = "support@smartmoney.com",
            url = "https://smartmoney.com"
        ),
        license = @License(
            name = "MIT License",
            url = "https://opensource.org/licenses/MIT"
        )
    )
    // No `servers` block on purpose: the server entry is built at runtime in
    // SwaggerConfig#smartMoneyOpenAPI from app.base-url (APP_BASE_URL), so the deployed
    // hostname stays in the environment instead of being compiled into the jar.
)
@ConfigurationPropertiesScan
@EnableScheduling
public class SmartmoneyApplication {
    @jakarta.annotation.PostConstruct
    public void init() {
        java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("Asia/Ho_Chi_Minh"));
    }

    public static void main(String[] args) {
        SpringApplication.run(SmartmoneyApplication.class, args);
    }
}