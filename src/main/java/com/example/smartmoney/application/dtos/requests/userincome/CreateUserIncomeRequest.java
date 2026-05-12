package com.example.smartmoney.application.dtos.requests.userincome;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateUserIncomeRequest {

    @NotNull(message = "netIncome is required")
    @DecimalMin(value = "0", inclusive = true, message = "netIncome must be >= 0")
    private Integer netIncome;

    @NotNull(message = "usableIncome is required")
    @DecimalMin(value = "0", inclusive = true, message = "usableIncome must be >= 0")
    private Integer usableIncome;

    @Size(max = 10, message = "currency must not exceed 10 characters")
    private String currency;

    @Size(max = 255, message = "calculationNote must not exceed 255 characters")
    private String calculationNote;

    private Boolean autoInvestSurplus;
}
