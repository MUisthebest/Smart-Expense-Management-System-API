package com.example.smartmoney.infrastructure.security;

import com.example.smartmoney.application.ports.AuthPort;
import com.example.smartmoney.domain.models.user.User;
import com.example.smartmoney.domain.models.user.valueobjects.Email;
import com.example.smartmoney.domain.models.user.valueobjects.RefreshToken;
import com.example.smartmoney.domain.exceptions.AuthenticationException;
import com.example.smartmoney.domain.repositories.UserRepository;
import com.example.smartmoney.infrastructure.config.JwtConfig;
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

@Component
@RequiredArgsConstructor
@Slf4j
public class JwtTokenProvider implements AuthPort, UserDetailsService {
    
    private final JwtConfig jwtConfig;
    private final UserRepository userRepository;
    private final BCryptPasswordEncoder passwordEncoder;
    
    private SecretKey signingKey;
    
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
            .claim("role", user.getRole().name())
            .claim("authorities", authorities)
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
            
            Long userId = Long.parseLong(claims.getSubject());
            
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
    public Long getUserIdFromToken(String token) {
        try {
            Claims claims = Jwts.parserBuilder()
                .setSigningKey(signingKey)
                .build()
                .parseClaimsJws(token)
                .getBody();
            return Long.parseLong(claims.getSubject());
        } catch (JwtException | IllegalArgumentException ex) {
            throw new AuthenticationException("Invalid token");
        }
    }
    
    public boolean validateToken(String token) {
        try {
            Jwts.parserBuilder()
                .setSigningKey(signingKey)
                .build()
                .parseClaimsJws(token);
            return true;
        } catch (ExpiredJwtException ex) {
            log.debug("Token expired: {}", ex.getMessage());
            return false;
        } catch (JwtException | IllegalArgumentException ex) {
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
            Long userId = Long.parseLong(username);
            User user = userRepository.findById(userId)
                .orElseThrow(() -> new UsernameNotFoundException("User not found with id: " + userId));
            
            return convertToUserDetails(user);
            
        } catch (NumberFormatException e) {
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
    public Optional<Long> extractUserIdFromToken(String token) {
        try {
            Claims claims = Jwts.parserBuilder()
                .setSigningKey(signingKey)
                .build()
                .parseClaimsJws(token)
                .getBody();
            return Optional.of(Long.parseLong(claims.getSubject()));
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