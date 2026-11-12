package com.expensetracker.service;

import com.expensetracker.dto.PageResponse;
import com.expensetracker.dto.TransactionResponse;
import com.expensetracker.entity.Transaction;
import com.expensetracker.exception.BadRequestException;
import com.expensetracker.repository.CategoryRepository;
import com.expensetracker.repository.TransactionRepository;
import com.expensetracker.util.CurrentUser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransactionServiceSearchTest {

    private static final Long UID = 7L;

    @Mock TransactionRepository transactionRepository;
    @Mock CategoryRepository categoryRepository;
    @Mock BudgetAlertService budgetAlertService;
    @Mock CurrentUser currentUser;
    @InjectMocks TransactionService transactionService;

    @Test
    void searchTextLongerThan100CharactersIsRejected() {
        assertThrows(BadRequestException.class, () -> transactionService.list(
                null, null, null, null, "x".repeat(101), PageRequest.of(0, 10)));
        verify(transactionRepository, never())
                .findAll(ArgumentMatchers.<Specification<Transaction>>any(), any(Pageable.class));
    }

    @Test
    void searchTextOfExactly100CharactersIsAccepted() {
        when(currentUser.id()).thenReturn(UID);
        when(transactionRepository.findAll(ArgumentMatchers.<Specification<Transaction>>any(), any(Pageable.class)))
                .thenReturn(new PageImpl<Transaction>(List.of(), PageRequest.of(0, 10), 0));

        PageResponse<TransactionResponse> response = transactionService.list(
                null, null, null, null, "x".repeat(100), PageRequest.of(0, 10));

        assertEquals(0, response.totalElements());
    }

    @Test
    void blankSearchTextIsIgnoredNotRejected() {
        when(currentUser.id()).thenReturn(UID);
        when(transactionRepository.findAll(ArgumentMatchers.<Specification<Transaction>>any(), any(Pageable.class)))
                .thenReturn(new PageImpl<Transaction>(List.of(), PageRequest.of(0, 10), 0));

        PageResponse<TransactionResponse> response = transactionService.list(
                null, null, null, null, "    ", PageRequest.of(0, 10));

        assertEquals(0, response.totalElements());
    }
}
