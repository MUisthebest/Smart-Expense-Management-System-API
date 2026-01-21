package com.example.smartmoney.infrastructure.auth.external;

import com.example.smartmoney.infrastructure.auth.config.SupabaseJwtProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigInteger;
import java.security.AlgorithmParameters;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPublicKeySpec;
import java.security.spec.EllipticCurve;
import java.security.spec.ECFieldFp;
import java.security.spec.ECGenParameterSpec;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
public class SupabaseJwtValidator {
    
    private final SupabaseJwtProperties properties;
    private final ObjectMapper objectMapper;
    private PublicKey cachedPublicKey;
    
    @Autowired
    public SupabaseJwtValidator(SupabaseJwtProperties properties) {
        this.properties = properties;
        this.objectMapper = new ObjectMapper();
        properties.validateConfig();
    }
    
    /**
     * Validate Supabase JWT token and extract user information.
     * Uses ES256 algorithm with the public key from SUPABASE_JWT_JWK environment variable.
     */
    public Map<String, Object> validateAndExtractClaims(String token) {
        try {
            // Remove "Bearer " prefix if present
            String cleanToken = token.startsWith("Bearer ") ? token.substring(7) : token;
            
            // Get the public key from JWK (configured in environment)
            PublicKey publicKey = getSupabasePublicKey();
            
            // Decode JWT with public key using ES256
            Claims claims = Jwts.parserBuilder()
                    .setSigningKey(publicKey)
                    .build()
                    .parseClaimsJws(cleanToken)
                    .getBody();
            
            log.debug("✅ Supabase JWT token validated successfully");
            
            // Extract user info
            Map<String, Object> claimsMap = new HashMap<>(claims);
            
            // Supabase stores user info in 'user' claim
            if (claimsMap.containsKey("user")) {
                Object userObj = claimsMap.get("user");
                if (userObj instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> userMap = (Map<String, Object>) userObj;
                    log.debug("Extracted user from Supabase JWT: {}", userMap.get("email"));
                    return userMap;
                }
            }
            
            // Fallback: extract from top-level claims
            String sub = claims.getSubject();
            String email = (String) claims.get("email");
            
            Map<String, Object> userInfo = new HashMap<>();
            userInfo.put("id", sub);
            userInfo.put("email", email);
            
            return userInfo;
            
        } catch (Exception e) {
            log.error("❌ Failed to validate Supabase JWT token: {}", e.getMessage());
            throw new RuntimeException("Invalid Supabase JWT token: " + e.getMessage(), e);
        }
    }
    
    /**
     * Get Supabase's public key from JWK stored in environment variable.
     * Supabase uses ES256 (secp256r1) with x,y coordinates.
     * Caches the key to avoid repeated parsing.
     */
    private PublicKey getSupabasePublicKey() {
        if (cachedPublicKey != null) {
            return cachedPublicKey;
        }
        
        try {
            String jwkJson = properties.getJwtJwk();
            if (jwkJson == null || jwkJson.isEmpty()) {
                throw new RuntimeException("SUPABASE_JWT_JWK is not configured");
            }
            
            log.debug("Parsing JWK from environment variable");
            JsonNode jwk = objectMapper.readTree(jwkJson);
            
            // Extract x and y coordinates from JWK
            String x = jwk.get("x").asText();
            String y = jwk.get("y").asText();
            
            if (x == null || x.isEmpty() || y == null || y.isEmpty()) {
                throw new RuntimeException("JWK missing x or y coordinate");
            }
            
            // Decode x and y from base64url
            byte[] xBytes = Base64.getUrlDecoder().decode(x);
            byte[] yBytes = Base64.getUrlDecoder().decode(y);
            
            // Build EC public key from x and y coordinates
            cachedPublicKey = reconstructPublicKey(xBytes, yBytes);
            log.info("✅ Supabase ES256 public key loaded from JWT_JWK environment variable");
            
            return cachedPublicKey;
            
        } catch (Exception e) {
            log.error("Failed to parse Supabase JWK: {}", e.getMessage());
            throw new RuntimeException("Failed to load Supabase public key: " + e.getMessage(), e);
        }
    }
    
    /**
     * Reconstruct EC public key from x and y coordinates (ES256 - secp256r1 / P-256)
     * Uses proper P-256 curve parameters
     */
    private PublicKey reconstructPublicKey(byte[] x, byte[] y) throws Exception {
        ECPoint ecPoint = new ECPoint(
            new BigInteger(1, x),
            new BigInteger(1, y)
        );

        // Load chuẩn P-256 từ JVM
        AlgorithmParameters parameters = AlgorithmParameters.getInstance("EC");
        parameters.init(new ECGenParameterSpec("secp256r1"));
        ECParameterSpec ecParams = parameters.getParameterSpec(ECParameterSpec.class);

        ECPublicKeySpec pubKeySpec = new ECPublicKeySpec(ecPoint, ecParams);
        return KeyFactory.getInstance("EC").generatePublic(pubKeySpec);
    }
    
    /**
     * Check if token is valid (not expired, correct signature)
     */
    public boolean isTokenValid(String token) {
        try {
            validateAndExtractClaims(token);
            return true;
        } catch (Exception e) {
            log.warn("Token validation failed: {}", e.getMessage());
            return false;
        }
    }
}
