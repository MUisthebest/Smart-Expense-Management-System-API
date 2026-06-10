package com.example.smartmoney.domain.models.project;

import com.example.smartmoney.domain.models.project.enums.JoinStatus;
import com.example.smartmoney.domain.models.project.valueobjects.UserId;

import java.math.BigDecimal;
import java.util.Objects;

public class ProjectMember {
    private final UserId userId;
    private JoinStatus joinStatus;
    private boolean admin;

    /**
     * The monthly amount this member has pledged to contribute. Set at invite
     * time (or via {@code UpdateMemberCommitmentUseCase}). Zero/absent for
     * legacy rows or members of PERSONAL projects.
     */
    private BigDecimal monthlyCommitmentAmount;

    /**
     * Running tally of missed monthly contributions. Incremented by the
     * monthly arrears worker when a member fails to contribute their full
     * {@link #monthlyCommitmentAmount} in a given cycle. Reset to zero on kick
     * (DEFAULTED) or reduced via catch-up contributions.
     */
    private BigDecimal arrearsBalance;

    public ProjectMember(UserId userId, JoinStatus joinStatus, boolean admin) {
        this(userId, joinStatus, admin, BigDecimal.ZERO, BigDecimal.ZERO);
    }

    public ProjectMember(UserId userId,
                         JoinStatus joinStatus,
                         boolean admin,
                         BigDecimal monthlyCommitmentAmount,
                         BigDecimal arrearsBalance) {
        this.userId = Objects.requireNonNull(userId);
        this.joinStatus = Objects.requireNonNull(joinStatus);
        this.admin = admin;
        this.monthlyCommitmentAmount = normalize(monthlyCommitmentAmount);
        this.arrearsBalance = normalize(arrearsBalance);
    }

    public UserId getUserId() {
        return userId;
    }

    public JoinStatus getJoinStatus() {
        return joinStatus;
    }

    public boolean isAdmin() {
        return admin;
    }

    public BigDecimal getMonthlyCommitmentAmount() {
        return monthlyCommitmentAmount;
    }

    public BigDecimal getArrearsBalance() {
        return arrearsBalance;
    }

    public void invite() {
        this.joinStatus = JoinStatus.INVITED;
    }

    public void accept() {
        this.joinStatus = JoinStatus.JOINED;
    }

    public void leave() {
        this.joinStatus = JoinStatus.LEFT;
    }

    public void setAdmin(boolean admin) {
        this.admin = admin;
    }

    /**
     * Sets or updates the monthly commitment. Validated &gt;= 0; callers that
     * intend to clear should pass {@link BigDecimal#ZERO}.
     */
    public void setCommitment(BigDecimal amount) {
        if (amount == null) {
            throw new IllegalArgumentException("Commitment amount is required");
        }
        if (amount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Commitment amount must be >= 0");
        }
        this.monthlyCommitmentAmount = amount;
    }

    /**
     * Increments {@link #arrearsBalance} by the (positive) shortfall for the
     * cycle just assessed. Called by the monthly arrears worker.
     */
    public void accrueArrears(BigDecimal shortfall) {
        if (shortfall == null || shortfall.compareTo(BigDecimal.ZERO) <= 0) return;
        this.arrearsBalance = this.arrearsBalance.add(shortfall);
    }

    /**
     * Reduces arrears by the supplied amount, flooring at zero. Used when a
     * member catches up via a manual contribution.
     */
    public void reduceArrears(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) return;
        BigDecimal next = this.arrearsBalance.subtract(amount);
        this.arrearsBalance = next.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : next;
    }

    public void wipeArrears() {
        this.arrearsBalance = BigDecimal.ZERO;
    }

    /**
     * Terminal kick state: marks the member as DEFAULTED and wipes arrears
     * (debt is forgiven; admin must lower the goal or rebalance).
     */
    public void markDefaulted() {
        this.joinStatus = JoinStatus.DEFAULTED;
        wipeArrears();
    }

    public boolean isDefaulted() {
        return this.joinStatus == JoinStatus.DEFAULTED;
    }

    /**
     * @return {@code true} when {@link #arrearsBalance} has reached or exceeded
     * 3× the {@link #monthlyCommitmentAmount} (the kick / freeze threshold).
     * Returns {@code false} when commitment is zero (cannot default on nothing).
     */
    public boolean isOver3MonthDefault() {
        if (monthlyCommitmentAmount.compareTo(BigDecimal.ZERO) <= 0) return false;
        BigDecimal threshold = monthlyCommitmentAmount.multiply(BigDecimal.valueOf(3));
        return arrearsBalance.compareTo(threshold) >= 0;
    }

    private static BigDecimal normalize(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
