package com.expensetracker.repository;

import com.expensetracker.entity.Transaction;
import org.springframework.data.jpa.domain.Specification;

import java.util.Locale;

/** Text search for GET /api/transactions?q= (description contains the text, ignoring capitals). */
public final class TransactionSearchSpecs {

    /** "!" is the LIKE escape character (a backslash would need extra escaping inside MySQL strings). */
    private static final char ESCAPE = '!';

    private TransactionSearchSpecs() {}

    /** null or blank text means "no filter". Transactions without a description never match a search. */
    public static Specification<Transaction> descriptionContains(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String pattern = likePattern(text);
        return (root, query, cb) -> cb.like(cb.lower(root.<String>get("description")), pattern, ESCAPE);
    }

    /** "%text%" in lower case. The characters ! % _ are escaped so a search for "50%" matches literally. */
    static String likePattern(String text) {
        String escaped = text.trim().toLowerCase(Locale.ROOT)
                .replace("!", "!!")     // escape character first
                .replace("%", "!%")
                .replace("_", "!_");
        return "%" + escaped + "%";
    }
}
