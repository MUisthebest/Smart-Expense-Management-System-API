package com.example.smartmoney.infrastructure.common;

import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;

import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@SecurityScheme(
    name = "bearerAuth",
    type = SecuritySchemeType.HTTP,
    bearerFormat = "JWT",
    scheme = "bearer"
)
public class SwaggerConfig {

    @Value("${server.servlet.context-path:/api}")
    private String contextPath;

    // Follows the deployed API rather than a hardcoded localhost, so "Try it out" in
    // Swagger UI hits whichever backend this instance actually is.
    @Value("${app.base-url:http://localhost:8080}")
    private String baseUrl;

    @Bean
    public OpenAPI smartMoneyOpenAPI() {
        String base = baseUrl.endsWith("/")
                ? baseUrl.substring(0, baseUrl.length() - 1)
                : baseUrl;

        return new OpenAPI()
            .addServersItem(new Server()
                .url(base + contextPath)  // Thêm context-path
                .description("API Base Path"))
            .addSecurityItem(new SecurityRequirement().addList("bearerAuth"));
    }


    @Bean
    public GroupedOpenApi authApi() {
        return GroupedOpenApi.builder()
            .group("authentication")
            .pathsToMatch("/api/v1/auth/**")   // ✅ có /
            .build();
    }

    @Bean
    public GroupedOpenApi userApi() {
        return GroupedOpenApi.builder()
            .group("users")
            .pathsToMatch("/api/v1/users/**") // ✅ có /
            .build();
    }

    @Bean
    public GroupedOpenApi publicApi() {
        return GroupedOpenApi.builder()
            .group("public")
            .pathsToMatch("/api/v1/public/**", "/v1/auth/**") // ✅
            .build();
    }

    @Bean
    public GroupedOpenApi adminApi() {
        return GroupedOpenApi.builder()
            .group("admin")
            .pathsToMatch("/api/v1/admin/**") // ✅
            .build();
    }

    @Bean
    public GroupedOpenApi allApi() {
        return GroupedOpenApi.builder()
            .group("all")
            .pathsToMatch("/api/**") // ✅
            .build();
    }
}
