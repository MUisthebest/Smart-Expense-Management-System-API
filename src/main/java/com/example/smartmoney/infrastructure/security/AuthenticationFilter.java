package com.example.smartmoney.infrastructure.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class AuthenticationFilter extends OncePerRequestFilter {
    
    private final JwtTokenProvider jwtTokenProvider;
    private final UserDetailsService userDetailsService;
    private final JwtConfig jwtConfig;

    @Override
    protected  boolean shouldNotFilterAsyncDispatch() {
        return true;
    }
    @Override
    protected boolean shouldNotFilterErrorDispatch() {
        return true;
    }
    
    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        
        try {
            String token = extractTokenFromRequest(request);

            if (StringUtils.hasText(token)) {
                if (!jwtTokenProvider.validateToken(token)) {
                    log.warn("❌ Token invalid or expired");

                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    return;
                }

                UUID userId = jwtTokenProvider.getUserIdFromToken(token);

                UserDetails userDetails = userDetailsService.loadUserByUsername(userId.toString());

                UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(
                        userDetails,
                        null,
                        userDetails.getAuthorities()
                    );

                authentication.setDetails(
                    new WebAuthenticationDetailsSource().buildDetails(request)
                );

                SecurityContextHolder.getContext().setAuthentication(authentication);

                log.debug("Authenticated user ID: {}", userId);
            }
            } 
            catch (Exception ex) {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                return;
            }
        
        filterChain.doFilter(request, response);
    }
    
    /**
     * Extract JWT token from request
     */
    private String extractTokenFromRequest(HttpServletRequest request) {
        // Get token from Authorization header
        String bearerToken = request.getHeader(jwtConfig.getHeaderName());
        
        if (StringUtils.hasText(bearerToken) && 
            bearerToken.startsWith(jwtConfig.getTokenPrefix())) {
            return bearerToken.substring(jwtConfig.getTokenPrefix().length());
        }
        
        // Try to get from query parameter (for WebSocket, etc.)
        String tokenParam = request.getParameter("token");
        if (StringUtils.hasText(tokenParam)) {
            return tokenParam;
        }
        
        return null;
    }
    
    /**
     * Should not filter certain paths
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();
        
        // Skip filter for public endpoints
        return path.startsWith("/api/v1/auth/login") ||
               path.startsWith("/api/v1/auth/register") ||
               path.startsWith("/h2-console/") ||
               path.startsWith("/swagger-ui/") ||
               path.startsWith("/v3/api-docs/") ||
               path.equals("/actuator/health") ||
               path.startsWith("/api/v1/projects/invites/accept-direct") ||
               path.equals("/login") ||
               path.startsWith("/error");
    }
}