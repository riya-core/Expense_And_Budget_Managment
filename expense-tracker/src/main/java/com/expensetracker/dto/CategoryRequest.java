package com.expensetracker.dto;

import com.expensetracker.entity.TransactionType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CategoryRequest(
        @Schema(example = "Coffee") @NotBlank @Size(max = 100) String name,
        @Schema(example = "EXPENSE") @NotNull TransactionType type) {}
