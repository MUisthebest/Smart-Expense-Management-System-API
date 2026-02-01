package com.example.smartmoney.application.dtos.requests.transactions;

import com.example.smartmoney.domain.models.transaction.enums.Category;
import com.example.smartmoney.domain.models.transaction.enums.TransactionType;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class CreateTransactionRequest {
    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.01", message = "Amount must be greater than 0")
    private BigDecimal amount;
    
    @NotNull(message = "Transaction type is required")
    private TransactionType type;
    
    @NotNull(message = "Category is required")
    private Category category;
    
    @Size(max = 500, message = "Description must not exceed 500 characters")
    private String description;
    
    @NotBlank(message = "Date is required")
    @Pattern(regexp = "\\d{2}/\\d{2}/\\d{4} \\d{2}:\\d{2}", message = "Date must be in dd/MM/yyyy HH:mm format")
    private String date;
}
