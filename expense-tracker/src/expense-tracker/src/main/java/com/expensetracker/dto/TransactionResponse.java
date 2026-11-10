package com.expensetracker.dto;

import com.expensetracker.entity.TransactionType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record TransactionResponse(
        Long id,
        Long categoryId,
        String categoryName,
        TransactionType type,
        BigDecimal amount,
        String description,
        LocalDate date,
        LocalDateTime createdAt) {}
