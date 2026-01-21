package com.example.smartmoney.infrastructure.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Configuration
@ConfigurationProperties(prefix = "app.facebook")
public class FacebookOAuthProperties {

    private String appId;
    private String appSecret;
    private String apiVersion = "v18.0";
    private String graphApiBaseUrl = "https://graph.facebook.com";
    private String fields = "id,name,email,picture.type(large)";
    private Integer tokenValiditySeconds = 3600;

    // Getters & Setters
    public String getAppId() {
        return appId;
    }

    public void setAppId(String appId) {
        this.appId = appId;
    }

    public String getAppSecret() {
        return appSecret;
    }

    public void setAppSecret(String appSecret) {
        this.appSecret = appSecret;
    }

    public String getApiVersion() {
        return apiVersion;
    }

    public void setApiVersion(String apiVersion) {
        this.apiVersion = apiVersion;
    }

    public String getGraphApiBaseUrl() {
        return graphApiBaseUrl;
    }

    public void setGraphApiBaseUrl(String graphApiBaseUrl) {
        this.graphApiBaseUrl = graphApiBaseUrl;
    }

    public String getFields() {
        return fields;
    }

    public void setFields(String fields) {
        this.fields = fields;
    }

    public Integer getTokenValiditySeconds() {
        return tokenValiditySeconds;
    }

    public void setTokenValiditySeconds(Integer tokenValiditySeconds) {
        this.tokenValiditySeconds = tokenValiditySeconds;
    }

    // Helper method
    public String getGraphApiUrl() {
        return String.format("%s/%s/me", graphApiBaseUrl, apiVersion);
    }

    public String getDebugTokenUrl(String inputToken) {
        String appAccessToken = appId + "|" + appSecret;

        try {
            String encodedInputToken = URLEncoder.encode(inputToken, StandardCharsets.UTF_8);
            String encodedAccessToken = URLEncoder.encode(appAccessToken, StandardCharsets.UTF_8);

            String url = String.format(
                "%s/%s/debug_token?input_token=%s&access_token=%s",
                graphApiBaseUrl,
                apiVersion,
                encodedInputToken,
                encodedAccessToken
            );

            log.debug("Debugging Facebook token with URL: {}", url);
            return url;
        } catch (Exception e) {
            log.error("Error encoding Facebook debug token URL", e);
            throw new RuntimeException("Failed to encode debug token URL", e);
        }
    }
}
