package com.example.smartmoney.infrastructure.persistence.entities;

import com.example.smartmoney.domain.models.project.enums.JoinStatus;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "project_members",
        uniqueConstraints = @UniqueConstraint(name = "uk_project_member", columnNames = {"project_id","user_id"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProjectMemberEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "join_status",  nullable = false, length = 20)
    private JoinStatus joinStatus;

    @Column(name = "is_admin", nullable = false)
    private boolean admin;

    /** V13: pledged monthly contribution amount; 0 for legacy members. */
    @Column(name = "monthly_commitment_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal monthlyCommitmentAmount;

    /** V13: running unpaid pledge accumulated by the arrears worker. */
    @Column(name = "arrears_balance", nullable = false, precision = 19, scale = 2)
    private BigDecimal arrearsBalance;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}

