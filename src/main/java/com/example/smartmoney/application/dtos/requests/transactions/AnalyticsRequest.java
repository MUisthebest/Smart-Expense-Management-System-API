package com.example.smartmoney.application.dtos.requests.transactions;

import lombok.Data;

@Data
public class AnalyticsRequest {

    private Integer year;

    private Integer month;

    private String type;

    private Integer day;
}