package com.example.smartmoney.infrastructure.auth.adapters;

import com.example.smartmoney.application.dtos.requests.auth.ExternalOAuthUser;
import com.example.smartmoney.application.ports.auth.ExternalOAuthPort;
import com.example.smartmoney.infrastructure.auth.external.SupabaseJwtValidator;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import lombok.RequiredArgsConstructor;

import com.example.smartmoney.infrastructure.auth.config.GoogleOAuthProperties;
import com.example.smartmoney.infrastructure.auth.config.FacebookOAuthProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import java.util.Map;

@Component
@ConditionalOnProperty(
    name = "app.google.oauth2.enabled",
    havingValue = "true",
    matchIfMissing = false
)
@RequiredArgsConstructor
public class ExternalOAuthAdapter implements ExternalOAuthPort {

    private final GoogleIdTokenVerifier verifier;
    private final SupabaseJwtValidator validator;
    private final GoogleOAuthProperties GoogleOAuthProperties;
    private final FacebookOAuthProperties FacebookOAuthProperties;

    @Override
    public ExternalOAuthUser verifyGoogleToken(String idToken) {
        try {
            var token = verifier.verify(idToken);
            if (token == null) return null;

            var payload = token.getPayload();

            return new ExternalOAuthUser(
                    payload.getEmail(),
                    (String) payload.get("name"),
                    (String) payload.get("picture")
            );
        } catch (Exception e) {
            return null;
        }
    }
    @Override
    public Map<String, Object> validateAndExtractExternalToken(String token) throws Exception {
        return validator.validateAndExtractClaims(token);
    }   
    @Override
    public long getGoogleTokenValiditySeconds() {
        return GoogleOAuthProperties.getTokenValiditySeconds().longValue();
    }
    @Override
    public long getFacebookTokenValiditySeconds() {
        return FacebookOAuthProperties.getTokenValiditySeconds().longValue();
    }
}
