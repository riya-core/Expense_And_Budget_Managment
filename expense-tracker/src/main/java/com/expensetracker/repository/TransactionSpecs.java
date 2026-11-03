package com.expensetracker.repository;

import com.expensetracker.entity.Transaction;
import com.expensetracker.entity.TransactionType;
import jakarta.persistence.criteria.JoinType;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;

/** Building blocks for GET /api/transactions filtering. Combine with .and(). */
public final class TransactionSpecs {

    private TransactionSpecs() {}

    public static Specification<Transaction> belongsTo(Long userId) {
        return (root, q, cb) -> cb.equal(root.get("user").get("id"), userId);
    }

    public static Specification<Transaction> from(LocalDate from) {
        return (root, q, cb) -> from == null ? cb.conjunction()
                : cb.greaterThanOrEqualTo(root.<LocalDate>get("date"), from);
    }

    public static Specification<Transaction> to(LocalDate to) {
        return (root, q, cb) -> to == null ? cb.conjunction()
                : cb.lessThanOrEqualTo(root.<LocalDate>get("date"), to);
    }

    public static Specification<Transaction> category(Long categoryId) {
        return (root, q, cb) -> categoryId == null ? cb.conjunction()
                : cb.equal(root.get("category").get("id"), categoryId);
    }

    public static Specification<Transaction> type(TransactionType type) {
        return (root, q, cb) -> type == null ? cb.conjunction()
                : cb.equal(root.get("type"), type);
    }

    /** Fetch the category in the same query (avoids N+1) but not on the count query. */
    public static Specification<Transaction> withCategory() {
        return (root, q, cb) -> {
            if (q != null && !Long.class.equals(q.getResultType()) && !long.class.equals(q.getResultType())) {
                root.fetch("category", JoinType.LEFT);
            }
            return cb.conjunction();
        };
    }
}
