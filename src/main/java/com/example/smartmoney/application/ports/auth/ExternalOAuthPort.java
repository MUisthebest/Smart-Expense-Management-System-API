package com.example.smartmoney.application.ports.auth;
import com.example.smartmoney.application.dtos.requests.auth.ExternalOAuthUser;
import java.util.Map;

public interface ExternalOAuthPort {
    ExternalOAuthUser verifyGoogleToken(String idToken);
    Map<String, Object> validateAndExtractExternalToken(String token) throws Exception;
    long getGoogleTokenValiditySeconds();
    long getFacebookTokenValiditySeconds();
}