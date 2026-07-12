package com.example.smartmoney.infrastructure.persistence.entities;

import com.example.smartmoney.domain.models.user.enums.Focus_Mode;
import com.example.smartmoney.domain.models.user.enums.Intervention_Level;
import com.example.smartmoney.domain.models.user.enums.Saving_Pace;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
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
    
    @Column(name = "username", unique = true, nullable = false, length = 30)
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

    @Column(name = "income", precision = 19, scale = 2)
    private BigDecimal income;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "focus", columnDefinition = "focus")
    private Focus_Mode focusMode;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(
            name = "intervention_level",
            columnDefinition = "intervention_level"
    )
    private Intervention_Level interventionLevel;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "pace", columnDefinition = "pace")
    private Saving_Pace savingPace;

    @Column(name = "auto_invest_surplus")
    private Boolean autoInvestSurplus;
    
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
    
    @Column(name = "is_email_verified", nullable = false)
    private boolean emailVerified = false;
    
    @Column(name = "notification_enabled", nullable = false)
    private boolean notificationEnabled = true;

    // Enum phải là public để MapStruct truy cập được
    public enum Role {
        USER,
        ADMIN
    }
}