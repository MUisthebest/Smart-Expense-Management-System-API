package com.example.smartmoney.domain.models.user.valueobjects;

import com.example.smartmoney.domain.exceptions.DomainException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

public class DateOfBirth {
    private final LocalDate value;

     protected DateOfBirth() {
        this.value = null;
    }   
    
    public DateOfBirth(String dateStr) {
        if (dateStr == null || dateStr.isEmpty()) {
            throw new DomainException("Date of birth cannot be empty");
        }
        
        try {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");
            this.value = LocalDate.parse(dateStr, formatter);
            
            // Validate age (at least 13 years old)
            LocalDate minDate = LocalDate.now().minusYears(13);
            if (value.isAfter(minDate)) {
                throw new DomainException("User must be at least 13 years old");
            }
            
            // Not too old (max 150 years)
            LocalDate maxDate = LocalDate.now().minusYears(150);
            if (value.isBefore(maxDate)) {
                throw new DomainException("Invalid date of birth");
            }
            
        } catch (DateTimeParseException e) {
            throw new DomainException("Date must be in dd/MM/yyyy format");
        }
    }
    
    public String getFormattedValue() {
        return value.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
    }
    
    public LocalDate getValue() {
        return value;
    }
}