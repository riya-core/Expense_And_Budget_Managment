package com.expensetracker.dto;

import com.expensetracker.entity.TransactionType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

public record TransactionRequest(
        @Schema(example = "5", description = "Optional. A default category or one of your own; its type must match.")
        Long categoryId,
        @Schema(example = "EXPENSE") @NotNull TransactionType type,
        @Schema(example = "250.50", description = "Greater than 0, at most 2 decimals are kept")
        @NotNull @DecimalMin("0.01") @DecimalMax("9999999999.99") BigDecimal amount,
        @Schema(example = "Lunch") @Size(max = 255) String description,
        @Schema(example = "2026-11-03", description = "yyyy-MM-dd, between 2000-01-01 and one year from today")
        @NotNull LocalDate date) {}
