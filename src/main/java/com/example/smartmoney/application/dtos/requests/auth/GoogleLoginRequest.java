package com.example.smartmoney.application.dtos.requests.auth;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class GoogleLoginRequest {
    
    @NotBlank(message = "idToken is required")
    private String idToken;
    
    // Optional: You can also receive user data directly from frontend if needed
    private String email;
    private String name;
    private String picture;
    private String givenName;
    private String familyName;
}

