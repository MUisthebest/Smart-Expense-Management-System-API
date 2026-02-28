package com.example.smartmoney.infrastructure.persistence.entities;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "otps")
@Getter
@Setter
public class OTPEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(name = "email", nullable = false, length = 100)
    private String email;
    @Column(name = "otp", nullable = false, length = 10)
    private String otp;
    @Column(name = "expired_at", nullable = false)
    private LocalDateTime expiredAt;
    @Column(name = "verified", nullable = false)
    private boolean verified;
    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 10)
    private Type type;
    public enum Type {
        VERIFY,
        UPDATE
    }
}
