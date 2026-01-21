package com.example.smartmoney.application.dtos.requests.auth;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FacebookLoginRequest {
    
    /**
     * Supabase JWT access token from frontend.
     * This is obtained after Facebook OAuth authentication via Supabase.
     * Frontend gets this from supabase.auth.getSession().access_token
     */
    @NotBlank(message = "accessToken is required - use Supabase JWT token from frontend")
    private String accessToken;
}
