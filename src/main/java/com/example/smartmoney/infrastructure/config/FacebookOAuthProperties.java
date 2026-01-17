package com.example.smartmoney.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "app.facebook")
public class FacebookOAuthProperties {
    
    private String appId;
    private String appSecret;
    private String apiVersion = "v18.0";
    private String graphApiBaseUrl = "https://graph.facebook.com";
    private String fields = "id,name,email,picture.type(large)";
    private Integer tokenValiditySeconds = 3600;
    
    // Getters and Setters
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
    
    // Helper method to build Graph API URL
    public String getGraphApiUrl() {
        return String.format("%s/%s/me", graphApiBaseUrl, apiVersion);
    }
    
    // Helper method to build debug token URL
    public String getDebugTokenUrl(String inputToken) {
        return String.format("%s/%s/debug_token?input_token=%s&access_token=%s|%s",
                graphApiBaseUrl,
                apiVersion,
                inputToken,
                appId,
                appSecret);
    }
    
    @Override
    public String toString() {
        return "FacebookOAuthProperties{" +
                "appId='" + (appId != null ? "***" + appId.substring(Math.max(0, appId.length() - 4)) : "null") + '\'' +
                ", apiVersion='" + apiVersion + '\'' +
                ", graphApiBaseUrl='" + graphApiBaseUrl + '\'' +
                ", tokenValiditySeconds=" + tokenValiditySeconds +
                '}';
    }
}