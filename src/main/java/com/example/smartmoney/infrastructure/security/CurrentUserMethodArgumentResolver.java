package com.example.smartmoney.infrastructure.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.MethodParameter;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.util.UUID;

@Component
@Slf4j
public class CurrentUserMethodArgumentResolver implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.getParameterType().equals(UUID.class) &&
               parameter.hasParameterAnnotation(CurrentUser.class);
    }

    @Override
    public Object resolveArgument(
            MethodParameter parameter,
            ModelAndViewContainer mavContainer,
            NativeWebRequest webRequest,
            WebDataBinderFactory binderFactory) throws Exception {
        
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        
        if (authentication == null || !authentication.isAuthenticated()) {
            log.warn("No authenticated user found in security context");
            return null;
        }
        
        try {
            // The principal is a UserDetails object where the username is the userId
            Object principal = authentication.getPrincipal();
            
            if (principal instanceof UserDetails) {
                String userId = ((UserDetails) principal).getUsername();
                UUID userIdUUID = UUID.fromString(userId);
                log.debug("Resolved current user ID: {}", userIdUUID);
                return userIdUUID;
            }
        } catch (IllegalArgumentException ex) {
            log.error("Failed to parse user ID from authentication principal", ex);
        }
        
        return null;
    }
}
