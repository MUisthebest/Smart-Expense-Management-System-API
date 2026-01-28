package com.example.smartmoney.infrastructure.persistence.entities;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

@Entity
@Table(name = "otps")
@Getter
@Setter
public class OTPEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "email", nullable = false, length = 100)
    private String email;
    @Column(name = "otp", nullable = false, length = 10)
    private String otp;
    @Column(name = "expired_at", nullable = false)
    private LocalDateTime expiredAt;
    @Column(name = "verified", nullable = false)
    private boolean verified;
}
