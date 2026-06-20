package com.example.smartmoney.infrastructure.persistence.entities;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "push_tokens")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PushTokenEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    private UUID userId;

    @Column(unique = true, nullable = false)
    private String pushToken;

    private String platform; // android / ios

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}