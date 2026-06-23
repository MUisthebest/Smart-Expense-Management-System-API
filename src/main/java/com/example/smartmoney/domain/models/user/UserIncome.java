package com.example.smartmoney.domain.models.user;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public class UserIncome {
    private final UUID id;
    private Integer netIncome;
    private Integer usableIncome;
    private Integer safeSpending;
    private String currency;
    private String calculationNote;
    private boolean autoInvestSurplus;
    private Instant lastCalculatedAt;
    private final Instant createdAt;
    private Instant updatedAt;

    public UserIncome(
            UUID id,
            Integer netIncome,
            Integer usableIncome,
            Integer safeSpending,
            String currency,
            String calculationNote,
            boolean autoInvestSurplus,
            Instant lastCalculatedAt,
            Instant createdAt,
            Instant updatedAt
    ) {
        this.id = Objects.requireNonNull(id);
        this.netIncome = netIncome;
        this.usableIncome = usableIncome;
        this.safeSpending = safeSpending;
        this.currency = currency;
        this.calculationNote = calculationNote;
        this.autoInvestSurplus = autoInvestSurplus;
        this.lastCalculatedAt = lastCalculatedAt;
        this.createdAt = Objects.requireNonNull(createdAt);
        this.updatedAt = Objects.requireNonNull(updatedAt);
        validate();
    }

    public static UserIncome createNew(UUID userId, Integer netIncome, Integer usableIncome, String currency, String calculationNote, Boolean autoInvestSurplus) {
        Instant now = Instant.now();
        boolean autoInvest = autoInvestSurplus != null ? autoInvestSurplus : false;
        return new UserIncome(userId, netIncome, usableIncome, null, currency, calculationNote, autoInvest, null, now, now);
    }

    public void update(Integer netIncome, Integer usableIncome, String currency, String calculationNote, Boolean autoInvestSurplus) {
        if (netIncome != null) {
            this.netIncome = netIncome;
        }
        if (usableIncome != null) {
            this.usableIncome = usableIncome;
        }
        if (currency != null) {
            this.currency = currency;
        }
        if (calculationNote != null) {
            this.calculationNote = calculationNote;
        }
        if (autoInvestSurplus != null) {
            this.autoInvestSurplus = autoInvestSurplus;
        }
        this.updatedAt = Instant.now();
        validate();
    }

    private void validate() {
        if (netIncome != null && netIncome < 0) {
            throw new IllegalArgumentException("netIncome must be >= 0");
        }
        if (usableIncome != null && usableIncome < 0) {
            throw new IllegalArgumentException("usableIncome must be >= 0");
        }
        if (netIncome != null && usableIncome != null && usableIncome >= netIncome) {
            throw new IllegalArgumentException("usableIncome must be less than netIncome");
        }
        if (currency != null && currency.isBlank()) {
            throw new IllegalArgumentException("currency must not be blank");
        }
    }

    public UUID getId() {
        return id;
    }

    public Integer getNetIncome() {
        return netIncome;
    }

    public Integer getUsableIncome() {
        return usableIncome;
    }

    public Integer getSafeSpending() {
        return safeSpending;
    }

    public void setSafeSpending(Integer safeSpending) {
        this.safeSpending = safeSpending;
        this.updatedAt = Instant.now();
    }

    public Boolean getAutoInvestSurplus() {
        return autoInvestSurplus;
    }

    public String getCurrency() {
        return currency;
    }

    public String getCalculationNote() {
        return calculationNote;
    }

    public Instant getLastCalculatedAt() {
        return lastCalculatedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
