package com.example.smartmoney.infrastructure.security;

import com.example.smartmoney.application.dtos.responses.UserResponse;
import com.example.smartmoney.application.ports.auth.AuthPort;
import com.example.smartmoney.domain.models.user.User;
import com.example.smartmoney.domain.models.user.valueobjects.Email;
import com.example.smartmoney.domain.models.user.valueobjects.Phone;
import com.example.smartmoney.domain.models.user.valueobjects.RefreshToken;
import com.example.smartmoney.domain.exceptions.AuthenticationException;
import com.example.smartmoney.domain.models.user.enums.Role;
import com.example.smartmoney.domain.models.userprofile.enums.UserRole;
import com.example.smartmoney.domain.repositories.UserRepository;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Component
@RequiredArgsConstructor
@Slf4j
public class JwtTokenProvider implements AuthPort, UserDetailsService {
    
    private final JwtConfig jwtConfig;
    private final UserRepository userRepository;
    private final BCryptPasswordEncoder passwordEncoder;

    private SecretKey signingKey;
    private final Map<String, TokenData> resetTokenStore = new ConcurrentHashMap<>();
    private static final long RESET_TOKEN_EXPIRY_MINUTES = 10;
    
    private static class TokenData {
        String email;
        long expiryTime;
        
        TokenData(String email, long expiryTime) {
            this.email = email;
            this.expiryTime = expiryTime;
        }
        
        boolean isExpired() {
            return System.currentTimeMillis() > expiryTime;
        }
    }
    
    @PostConstruct
    public void init() {
        // Initialize signing key
        this.signingKey = Keys.hmacShaKeyFor(jwtConfig.getSecret().getBytes());
        
        log.info("JWT Token Provider initialized with secret length: {}", 
                jwtConfig.getSecret().length());
    }
    
    @Override
    public String generateAccessToken(User user) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + jwtConfig.getAccessTokenExpiration());
        
        // Create authorities
        List<String> authorities = new ArrayList<>();
        authorities.add("ROLE_" + user.getRole().name());
        
        return Jwts.builder()
            .setSubject(user.getId().toString())
            .claim("email", user.getEmail().getValue())
            .claim("username", user.getUsername())
                .claim("phone number", user.getPhone().getValue())
            .claim("role", user.getRole().name())
            .claim("authorities", authorities)
                .claim("active", user.isActive())
                .claim("coin", user.getCoin())
            .setIssuer(jwtConfig.getIssuer())
            .setAudience(jwtConfig.getAudience())
            .setIssuedAt(now)
            .setExpiration(expiryDate)
            .signWith(signingKey, SignatureAlgorithm.HS256)
            .compact();
    }
    
    @Override
    public String generateRefreshToken(User user) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + jwtConfig.getRefreshTokenExpiration());
        
        String token = Jwts.builder()
            .setSubject(user.getId().toString())
            .claim("type", "refresh")
            .setIssuer(jwtConfig.getIssuer())
            .setAudience(jwtConfig.getAudience())
            .setIssuedAt(now)
            .setExpiration(expiryDate)
            .signWith(signingKey, SignatureAlgorithm.HS256)
            .compact();
        
        // Save refresh token to user
        user.updateRefreshToken(
            RefreshToken.create(token, 
                jwtConfig.getRefreshTokenExpiration() / (60 * 1000)) // Convert to minutes
        );
        userRepository.save(user);
        
        log.debug("Generated refresh token for user ID: {}", user.getId());
        return token;
    }

    @Override
    public String generateResetToken(String email) {
        String token = UUID.randomUUID().toString();
        
        long expiryTime = System.currentTimeMillis() + 
            (RESET_TOKEN_EXPIRY_MINUTES * 60 * 1000);
        
        resetTokenStore.put(token, new TokenData(email, expiryTime));
        
        log.debug("Generated reset token for email: {}, token: {}", email, token);
        return token;
    }
    
    @Override
    public String validateResetToken(String resetToken) {
        TokenData tokenData = resetTokenStore.get(resetToken);
        
        if (tokenData == null) {
            log.debug("Reset token not found: {}", resetToken);
            return null;
        }
        
        if (tokenData.isExpired()) {
            log.debug("Reset token expired: {}", resetToken);
            resetTokenStore.remove(resetToken);
            return null;
        }
        
        return tokenData.email;
    }
    
    @Override
    public void invalidateResetToken(String token) {
        resetTokenStore.remove(token);
        log.debug("Reset token invalidated: {}", token);
    }
    
    @Override
    public void cleanupExpiredTokens() {
        int initialSize = resetTokenStore.size();
        resetTokenStore.entrySet().removeIf(entry -> entry.getValue().isExpired());
        int removedCount = initialSize - resetTokenStore.size();
        
        if (removedCount > 0) {
            log.debug("Cleaned up {} expired reset tokens", removedCount);
        }
    }
    
    @Override
    public boolean validatePassword(String rawPassword, String encodedPassword) {
        return passwordEncoder.matches(rawPassword, encodedPassword);
    }
    
    @Override
    public String encodePassword(String rawPassword) {
        return passwordEncoder.encode(rawPassword);
    }
    
    @Override
    public User validateRefreshToken(String refreshToken) {
        try {
            Claims claims = Jwts.parserBuilder()
                .setSigningKey(signingKey)
                .build()
                .parseClaimsJws(refreshToken)
                .getBody();
            
            // Validate token type
            String type = claims.get("type", String.class);
            if (!"refresh".equals(type)) {
                throw new AuthenticationException("Invalid token type");
            }
            
            // Validate issuer and audience
            if (!jwtConfig.getIssuer().equals(claims.getIssuer()) ||
                !jwtConfig.getAudience().equals(claims.getAudience())) {
                throw new AuthenticationException("Invalid token issuer or audience");
            }
            
            UUID userId = UUID.fromString(claims.getSubject());
            
            // Find user and validate refresh token
            return userRepository.findById(userId)
                .filter(user -> user.getRefreshToken() != null)
                .filter(user -> user.getRefreshToken().getToken().equals(refreshToken))
                .filter(user -> !user.getRefreshToken().isExpired())
                .orElseThrow(() -> new AuthenticationException("Invalid or expired refresh token"));
                
        } catch (ExpiredJwtException ex) {
            throw new AuthenticationException("Refresh token has expired");
        } catch (JwtException | IllegalArgumentException ex) {
            throw new AuthenticationException("Invalid refresh token");
        }
    }
    
    @Override
    public User getUserByEmail(Email email) {
        return userRepository.findByEmail(email)
            .orElseThrow(() -> new AuthenticationException("User not found with email: " + email.getValue()));
    }

    
    // Các method helper không thuộc interface
    public UUID getUserIdFromToken(String token) {
        try {
            Claims claims = Jwts.parserBuilder()
                .setSigningKey(signingKey)
                .build()
                .parseClaimsJws(token)
                .getBody();
            return UUID.fromString(claims.getSubject());
        } catch (JwtException | IllegalArgumentException ex) {
            throw new AuthenticationException("Invalid token");
        }
    }

    // Get user profile from token
    public UserResponse getUserByAccessToken(String authHeader) {
        try {
            String token = (authHeader != null && authHeader.startsWith("Bearer "))
                    ? authHeader.substring(7)
                    : authHeader;

            Claims claims = Jwts.parserBuilder()
                    .setSigningKey(signingKey)
                    .build()
                    .parseClaimsJws(token)
                    .getBody();

            UUID userId = UUID.fromString(claims.getSubject());
            String email = claims.get("email", String.class);
            String username = claims.get("username", String.class);
            String phone = claims.get("phone number", String.class);
            String roleStr = claims.get("role", String.class);
            Integer coin = claims.get("coin", Integer.class);
            Boolean active = claims.get("active", Boolean.class);

            return UserResponse.builder()
                    .id(userId)
                    .email(email)
                    .username(username)
                    .phone(phone)
                    .role(roleStr != null ? Role.valueOf(roleStr) : null)
                    .coin(coin)
                    .active(active)
                    .build();

        } catch (ExpiredJwtException ex) {
            throw new AuthenticationException("Access token has expired");
        } catch (JwtException | IllegalArgumentException ex) {
            throw new AuthenticationException("Invalid access token");
        }
    }
    
    public boolean validateToken(String token) {
        try {
            Jwts.parserBuilder()
                .setSigningKey(signingKey)
                .build()
                .parseClaimsJws(token);
            return true;
        } 
        catch (ExpiredJwtException ex) {
            throw new AuthenticationException("Token expired");
        } 
        catch (JwtException | IllegalArgumentException ex) {
                log.debug("Invalid token: {}", ex.getMessage());
                return false;
            }
    }
    
    public Claims getClaimsFromToken(String token) {
        try {
            return Jwts.parserBuilder()
                .setSigningKey(signingKey)
                .build()
                .parseClaimsJws(token)
                .getBody();
        } catch (JwtException ex) {
            throw new AuthenticationException("Failed to parse token claims");
        }
    }
    
    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        try {
            UUID userId = UUID.fromString(username);
            User user = userRepository.findById(userId)
                .orElseThrow(() -> new UsernameNotFoundException("User not found with id: " + userId));
            
            return convertToUserDetails(user);
            
        } catch (IllegalArgumentException e) {
            // If username is not a number, try to find by email
            try {
                Email email = new Email(username);
                User user = userRepository.findByEmail(email)
                    .orElseThrow(() -> new UsernameNotFoundException("User not found with email: " + username));
                return convertToUserDetails(user);
            } catch (Exception ex) {
                throw new UsernameNotFoundException("User not found with identifier: " + username);
            }
        }
    }
    
    private UserDetails convertToUserDetails(User user) {
        // Build authorities
        List<GrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()));
        
        // Add additional authorities based on user properties
        if (user.getCoin() > 1000) {
            authorities.add(new SimpleGrantedAuthority("PREMIUM_USER"));
        }
        
        return new org.springframework.security.core.userdetails.User(
            user.getId().toString(),
            user.getPassword().getHash(),
            user.isActive(),
            true,  // account not expired
            true,  // credentials not expired
            true,  // account not locked
            authorities
        );
    }
    
    /**
     * Extract user ID from token without validation
     */
    public Optional<UUID> extractUserIdFromToken(String token) {
        try {
            Claims claims = Jwts.parserBuilder()
                .setSigningKey(signingKey)
                .build()
                .parseClaimsJws(token)
                .getBody();
            return Optional.of(UUID.fromString(claims.getSubject()));
        } catch (Exception e) {
            return Optional.empty();
        }
    }
    
    /**
     * Get remaining validity of token in milliseconds
     */
    public long getRemainingValidity(String token) {
        try {
            Claims claims = getClaimsFromToken(token);
            Date expiration = claims.getExpiration();
            return expiration.getTime() - System.currentTimeMillis();
        } catch (Exception e) {
            return 0;
        }
    }
}