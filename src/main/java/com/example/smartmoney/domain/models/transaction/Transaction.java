package com.example.smartmoney.domain.models.transaction;

import com.example.smartmoney.domain.models.transaction.enums.TransactionType;
import com.example.smartmoney.domain.models.transaction.enums.Category;
import com.example.smartmoney.domain.models.transaction.valueobjects.Amount;
import com.example.smartmoney.domain.exceptions.DomainException;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Builder(toBuilder = true)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Getter
@Setter
public class Transaction {
    private UUID id;
    private UUID userId;
    private Amount amount;
    private TransactionType type;
    private Category category;
    private String description;
    private LocalDateTime date;
    /** Non-null only for project-contribution transactions (press-to-contribute). */
    private UUID projectId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // Factory method
    public static Transaction create(
            UUID userId,
            Amount amount,
            TransactionType type,
            Category category,
            String description,
            LocalDateTime date) {
        return create(userId, amount, type, category, description, date, null);
    }

    /** Factory carrying an optional {@code projectId} (set for PROJECT_CONTRIBUTION). */
    public static Transaction create(
            UUID userId,
            Amount amount,
            TransactionType type,
            Category category,
            String description,
            LocalDateTime date,
            UUID projectId) {

        if (userId == null) {
            throw new DomainException("UserId cannot be null");
        }
        if (amount == null) {
            throw new DomainException("Amount cannot be null");
        }
        if (type == null) {
            throw new DomainException("TransactionType cannot be null");
        }
        if (category == null) {
            throw new DomainException("Category cannot be null");
        }
        if (date == null) {
            throw new DomainException("Date cannot be null");
        }

        Transaction transaction = new Transaction();
        transaction.setUserId(userId);
        transaction.setAmount(amount);
        transaction.setType(type);
        transaction.setCategory(category);
        transaction.setDescription(description);
        transaction.setDate(date);
        transaction.setProjectId(projectId);
        transaction.setCreatedAt(LocalDateTime.now());
        transaction.setUpdatedAt(LocalDateTime.now());

        return transaction;
    }
    
    // Business methods
    
    public void update(Amount amount, Category category, String description, LocalDateTime date) {
        if (amount != null) {
            this.amount = amount;
        }
        if (category != null) {
            this.category = category;
        }
        if (description != null) {
            this.description = description;
        }
        if (date != null) {
            this.date = date;
        }
        this.updatedAt = LocalDateTime.now();
    }
}
