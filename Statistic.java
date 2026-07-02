package com.example.bankservice.model;

import java.math.BigDecimal;

public record Statistic(
        String type,
        BigDecimal amount
) {}

