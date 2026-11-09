package com.expensetracker.service;

import com.expensetracker.dto.TransactionRequest;
import com.expensetracker.entity.Transaction;
import com.expensetracker.entity.TransactionType;
import com.expensetracker.exception.BadRequestException;
import com.expensetracker.repository.CategoryRepository;
import com.expensetracker.repository.TransactionRepository;
import com.expensetracker.util.CurrentUser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/** The date range check runs first, so these tests need no stubbing. */
@ExtendWith(MockitoExtension.class)
class TransactionServiceHardeningTest {

    @Mock TransactionRepository transactionRepository;
    @Mock CategoryRepository categoryRepository;
    @Mock BudgetAlertService budgetAlertService;
    @Mock CurrentUser currentUser;
    @InjectMocks TransactionService transactionService;

    private TransactionRequest on(LocalDate date) {
        return new TransactionRequest(null, TransactionType.EXPENSE, new BigDecimal("10"), "x", date);
    }

    @Test
    void createRejectsADateBefore2000() {
        assertThrows(BadRequestException.class,
                () -> transactionService.create(on(LocalDate.of(1999, 12, 31))));
        verify(transactionRepository, never()).save(any(Transaction.class));
    }

    @Test
    void createRejectsADateMoreThanAYearAhead() {
        assertThrows(BadRequestException.class,
                () -> transactionService.create(on(LocalDate.now().plusYears(2))));
        verify(transactionRepository, never()).save(any(Transaction.class));
    }

    @Test
    void createRejectsAnAbsurdFarFutureYear() {
        assertThrows(BadRequestException.class,
                () -> transactionService.create(on(LocalDate.of(9999, 12, 31))));
    }

    @Test
    void updateAppliesTheSameDateRule() {
        assertThrows(BadRequestException.class,
                () -> transactionService.update(5L, on(LocalDate.of(1990, 1, 1))));
        verify(transactionRepository, never()).save(any(Transaction.class));
    }
}
