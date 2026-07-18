package com.example.smartmoney.domain.models.budget;

import com.example.smartmoney.domain.exceptions.DomainException;
import com.example.smartmoney.domain.models.transaction.enums.Category;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Builder(toBuilder = true)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Getter
@Setter
public class Budget {
    private UUID id;
    private UUID userId;
    private Category category;
    private BigDecimal amountLimit;
    private BigDecimal spent;
    private Integer month;
    private Integer year;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static Budget create(
            UUID userId,
            Category category,
            BigDecimal amountLimit,
            Integer month,
            Integer year) {
        
        if (userId == null) {
            throw new DomainException("UserId cannot be null");
        }
        if (category == null) {
            throw new DomainException("Category cannot be null");
        }
        if (amountLimit == null || amountLimit.compareTo(BigDecimal.ZERO) <= 0) {
            throw new DomainException("Amount limit must be greater than 0");
        }
        if (month == null || month < 1 || month > 12) {
            throw new DomainException("Month must be between 1 and 12");
        }
        if (year == null || year < 1900) {
            throw new DomainException("Year must be valid (>= 1900)");
        }
        
        Budget budget = new Budget();
        budget.setUserId(userId);
        budget.setCategory(category);
        budget.setAmountLimit(amountLimit);
        budget.setSpent(BigDecimal.ZERO);
        budget.setMonth(month);
        budget.setYear(year);
        budget.setCreatedAt(LocalDateTime.now());
        budget.setUpdatedAt(LocalDateTime.now());
        
        return budget;
    }
    
    // Business methods
    public void updateAmountLimit(BigDecimal newLimit) {
        if (newLimit == null || newLimit.compareTo(BigDecimal.ZERO) <= 0) {
            throw new DomainException("Amount limit must be greater than 0");
        }
        this.amountLimit = newLimit;
        this.updatedAt = LocalDateTime.now();
    }
    
    // Update spent amount - can exceed amount_limit
    public void updateSpent(BigDecimal newSpent) {
        if (newSpent == null) {
            throw new DomainException("Spent amount cannot be null");
        }
        if (newSpent.compareTo(BigDecimal.ZERO) < 0) {
            throw new DomainException("Spent amount cannot be negative");
        }
        this.spent = newSpent;
        this.updatedAt = LocalDateTime.now();
    }
    
    // Calculate remaining amount
    public BigDecimal calculateRemaining(BigDecimal spent) {
        return this.amountLimit.subtract(spent);
    }
    
    // Calculate progress percentage
    public Double calculateProgressPercent(BigDecimal spent) {
        if (spent == null || spent.compareTo(BigDecimal.ZERO) <= 0) {
            return 0.0;
        }
        return (spent.doubleValue() / this.amountLimit.doubleValue()) * 100;
    }
    
    // Determine alert level based on spending
    public AlertLevel getAlertLevel(BigDecimal spent) {
        if (spent == null) {
            return AlertLevel.SAFE;
        }
        
        double progressPercent = calculateProgressPercent(spent);
        
        if (progressPercent >= 100) {
            return AlertLevel.EXCEEDED;
        } else if (progressPercent >= 80) {
            return AlertLevel.WARNING;
        } else if (progressPercent >= 50) {
            return AlertLevel.CAUTION;
        }
        return AlertLevel.SAFE;
    }
    
    public enum AlertLevel {
        SAFE,
        CAUTION,
        WARNING,
        EXCEEDED
    }
}
