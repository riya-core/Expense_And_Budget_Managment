package com.expensetracker.util;

import com.expensetracker.dto.AuthUserDto;
import com.expensetracker.dto.CategoryResponse;
import com.expensetracker.dto.TransactionResponse;
import com.expensetracker.entity.Category;
import com.expensetracker.entity.Transaction;
import com.expensetracker.entity.User;

import java.time.temporal.ChronoUnit;

/**
 * Entity -> DTO mapping. Entities are never returned from controllers.
 * Call these inside a @Transactional service method (open-in-view is off, so lazy
 * relations such as transaction.category can only be read inside the transaction).
 */
public final class Mappers {

    private Mappers() {}

    public static AuthUserDto toDto(User u) {
        return new AuthUserDto(u.getId(), u.getName(), u.getEmail());
    }

    public static CategoryResponse toResponse(Category c) {
        return new CategoryResponse(c.getId(), c.getName(), c.getType(), c.getUser() == null);
    }

    public static TransactionResponse toResponse(Transaction t) {
        Category c = t.getCategory();
        return new TransactionResponse(
                t.getId(),
                c == null ? null : c.getId(),
                c == null ? null : c.getName(),
                t.getType(),
                t.getAmount(),
                t.getDescription(),
                t.getDate(),
                t.getCreatedAt() == null ? null : t.getCreatedAt().truncatedTo(ChronoUnit.SECONDS));
    }
}
