package com.expensetracker.dto;

import com.expensetracker.entity.TransactionType;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

public record TransactionRequest(
        Long categoryId,                                   // optional
        @NotNull TransactionType type,
        @NotNull @DecimalMin("0.01") @DecimalMax("9999999999.99") BigDecimal amount,
        @Size(max = 255) String description,
        @NotNull LocalDate date) {}
