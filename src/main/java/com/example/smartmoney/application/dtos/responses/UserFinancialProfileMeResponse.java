package com.example.smartmoney.application.dtos.responses;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class UserFinancialProfileMeResponse {

    private boolean configured;

    private UserFinancialProfileResponse profile;
}
