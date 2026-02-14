package com.example.smartmoney.infrastructure.persistence.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "users")
@Getter
@Setter
public class UserEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    @Column(name = "username", unique = true, nullable = false, length = 20)
    private String username;
    
    @Column(name = "full_name", nullable = false, length = 40)
    private String fullName;
    
    @Column(name = "avatar", length = 500)
    private String avatar;
    
    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;
    
    @Column(name = "date_of_birth", nullable = false)
    private LocalDate dateOfBirth;
    
    @Column(name = "phone", unique = false, nullable = false, length = 20)
    private String phone;
    
    @Column(name = "email", unique = true, nullable = false, length = 30)
    private String email;
    
    @Column(name = "coin", nullable = false)
    private Integer coin = 0;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 10)
    private Role role = Role.USER;
    
    @Column(name = "refresh_token", length = 500)
    private String refreshToken;
    
    @Column(name = "refresh_token_expiry")
    private LocalDateTime refreshTokenExpiry;
    
    @Column(name = "rate", nullable = false)
    private Double rate = 5.0;
    
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
    
    @Column(name = "is_active", nullable = false)
    private boolean active = true;
    
    // Enum phải là public để MapStruct truy cập được
    public enum Role {
        USER,
        ADMIN
    }
}