package com.expensetracker.service;

import com.expensetracker.entity.Transaction;
import org.springframework.stereotype.Service;

/**
 * STUB so P1's TransactionService compiles. This file is OWNED BY P2, who replaces
 * the whole file with the real implementation (keep the signature identical).
 * P1 calls check(tx) in TransactionService.create() and update(), never in delete.
 */
@Service
public class BudgetAlertService {

    public void check(Transaction tx) {
        // P2 implements: compute spent / limit and create WARNING or EXCEEDED alerts.
    }
}
