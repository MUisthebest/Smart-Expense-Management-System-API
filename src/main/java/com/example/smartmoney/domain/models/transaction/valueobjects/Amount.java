package com.example.smartmoney.domain.models.transaction.valueobjects;

import com.example.smartmoney.domain.exceptions.DomainException;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.math.BigDecimal;

@Getter
@EqualsAndHashCode
@ToString
public class Amount {
    private final BigDecimal value;

    public Amount(BigDecimal value) {
        if (value == null) {
            throw new DomainException("Amount cannot be null");
        }
        if (value.compareTo(BigDecimal.ZERO) <= 0) {
            throw new DomainException("Amount must be positive");
        }
        this.value = value;
    }
    
    public Amount(Double value) {
        this(BigDecimal.valueOf(value));
    }
    
    public Amount(Integer value) {
        this(BigDecimal.valueOf(value));
    }
}
