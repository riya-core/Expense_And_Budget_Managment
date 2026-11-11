package com.expensetracker.dto;

import com.expensetracker.entity.TransactionType;
import com.fasterxml.jackson.annotation.JsonProperty;

public record CategoryResponse(
        Long id,
        String name,
        TransactionType type,
        @JsonProperty("isDefault") boolean isDefault) {}
