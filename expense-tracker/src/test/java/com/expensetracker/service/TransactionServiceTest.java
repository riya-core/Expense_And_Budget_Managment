package com.expensetracker.service;

import com.expensetracker.dto.PageResponse;
import com.expensetracker.dto.TransactionRequest;
import com.expensetracker.dto.TransactionResponse;
import com.expensetracker.entity.Category;
import com.expensetracker.entity.Transaction;
import com.expensetracker.entity.TransactionType;
import com.expensetracker.entity.User;
import com.expensetracker.exception.BadRequestException;
import com.expensetracker.exception.ResourceNotFoundException;
import com.expensetracker.repository.CategoryRepository;
import com.expensetracker.repository.TransactionRepository;
import com.expensetracker.util.CurrentUser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransactionServiceTest {

    private static final Long UID = 7L;
    private static final LocalDate DAY = LocalDate.of(2026, 10, 13);

    @Mock TransactionRepository transactionRepository;
    @Mock CategoryRepository categoryRepository;
    @Mock BudgetAlertService budgetAlertService;
    @Mock CurrentUser currentUser;
    @InjectMocks TransactionService transactionService;

    private User me() {
        return User.builder().id(UID).name("Riya").email("riya@example.com").build();
    }

    private Category category(Long id, String name, TransactionType type) {
        return Category.builder().id(id).name(name).type(type).build();
    }

    private Transaction existing(Long id) {
        return Transaction.builder().id(id).user(me()).type(TransactionType.EXPENSE)
                .amount(new BigDecimal("100.00")).description("old").date(DAY).build();
    }

    // ---------- create ----------

    @Test
    void createSavesTrimsRoundsAndTriggersAlertCheck() {
        when(currentUser.get()).thenReturn(me());
        when(categoryRepository.findVisibleById(5L, UID))
                .thenReturn(Optional.of(category(5L, "Food", TransactionType.EXPENSE)));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(inv -> {
            Transaction t = inv.getArgument(0);
            t.setId(1L);
            return t;
        });

        TransactionResponse response = transactionService.create(
                new TransactionRequest(5L, TransactionType.EXPENSE, new BigDecimal("250.5"), "  Lunch ", DAY));

        assertEquals(1L, response.id());
        assertEquals("Food", response.categoryName());
        assertEquals(new BigDecimal("250.50"), response.amount());
        assertEquals("Lunch", response.description());
        ArgumentCaptor<Transaction> saved = ArgumentCaptor.forClass(Transaction.class);
        verify(budgetAlertService).check(saved.capture());
        assertEquals(1L, saved.getValue().getId());      // the hook gets the saved transaction
    }

    @Test
    void createWithoutCategoryIsAllowed() {
        when(currentUser.get()).thenReturn(me());
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(inv -> inv.getArgument(0));

        TransactionResponse response = transactionService.create(
                new TransactionRequest(null, TransactionType.INCOME, new BigDecimal("500"), null, DAY));

        assertNull(response.categoryId());
        assertNull(response.description());
        verify(categoryRepository, never()).findVisibleById(any(), any());
    }

    @Test
    void createRejectsUnknownCategory() {
        when(currentUser.get()).thenReturn(me());
        when(categoryRepository.findVisibleById(99L, UID)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> transactionService.create(
                new TransactionRequest(99L, TransactionType.EXPENSE, new BigDecimal("10"), "x", DAY)));
        verify(transactionRepository, never()).save(any());
        verify(budgetAlertService, never()).check(any());
    }

    @Test
    void createRejectsCategoryOfTheWrongType() {
        when(currentUser.get()).thenReturn(me());
        when(categoryRepository.findVisibleById(1L, UID))
                .thenReturn(Optional.of(category(1L, "Salary", TransactionType.INCOME)));

        assertThrows(BadRequestException.class, () -> transactionService.create(
                new TransactionRequest(1L, TransactionType.EXPENSE, new BigDecimal("10"), "x", DAY)));
        verify(transactionRepository, never()).save(any());
    }

    // ---------- update ----------

    @Test
    void updateChangesFieldsAndTriggersAlertCheck() {
        Transaction tx = existing(5L);
        when(currentUser.id()).thenReturn(UID);
        when(transactionRepository.findByIdAndUserId(5L, UID)).thenReturn(Optional.of(tx));
        when(categoryRepository.findVisibleById(6L, UID))
                .thenReturn(Optional.of(category(6L, "Rent", TransactionType.EXPENSE)));
        when(transactionRepository.save(tx)).thenReturn(tx);

        TransactionResponse response = transactionService.update(5L,
                new TransactionRequest(6L, TransactionType.EXPENSE, new BigDecimal("900"), "October rent", DAY));

        assertEquals("Rent", response.categoryName());
        assertEquals(new BigDecimal("900.00"), response.amount());
        assertEquals("October rent", response.description());
        verify(budgetAlertService).check(tx);
    }

    @Test
    void updateOfAnotherUsersTransactionIsNotFound() {
        when(currentUser.id()).thenReturn(UID);
        when(transactionRepository.findByIdAndUserId(5L, UID)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> transactionService.update(5L,
                new TransactionRequest(null, TransactionType.EXPENSE, new BigDecimal("1"), "x", DAY)));
        verify(budgetAlertService, never()).check(any());
    }

    // ---------- get / delete ----------

    @Test
    void getReturnsOwnTransaction() {
        when(currentUser.id()).thenReturn(UID);
        when(transactionRepository.findByIdAndUserId(5L, UID)).thenReturn(Optional.of(existing(5L)));

        assertEquals(5L, transactionService.get(5L).id());
    }

    @Test
    void deleteRemovesOwnTransactionWithoutAlertCheck() {
        Transaction tx = existing(5L);
        when(currentUser.id()).thenReturn(UID);
        when(transactionRepository.findByIdAndUserId(5L, UID)).thenReturn(Optional.of(tx));

        transactionService.delete(5L);

        verify(transactionRepository).delete(tx);
        verify(budgetAlertService, never()).check(any());
    }

    @Test
    void deleteOfUnknownTransactionIsNotFound() {
        when(currentUser.id()).thenReturn(UID);
        when(transactionRepository.findByIdAndUserId(5L, UID)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> transactionService.delete(5L));
        verify(transactionRepository, never()).delete(any(Transaction.class));
    }

    // ---------- list ----------

    @Test
    void listRejectsFromAfterTo() {
        assertThrows(BadRequestException.class, () -> transactionService.list(
                LocalDate.of(2026, 11, 1), LocalDate.of(2026, 10, 1), null, null, null, PageRequest.of(0, 10)));
    }

    @Test
    void listRejectsSortingByAnUnknownField() {
        when(currentUser.id()).thenReturn(UID);

        assertThrows(BadRequestException.class, () -> transactionService.list(
                null, null, null, null, null, PageRequest.of(0, 10, Sort.by("password"))));
    }

    @Test
    void listCapsPageSizeAndAddsStableTieBreaker() {
        when(currentUser.id()).thenReturn(UID);
        when(transactionRepository.findAll(ArgumentMatchers.<Specification<Transaction>>any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(existing(5L)), PageRequest.of(0, 100), 1));

        PageResponse<TransactionResponse> response = transactionService.list(
                null, null, null, null, null, PageRequest.of(0, 500, Sort.by(Sort.Direction.DESC, "date")));

        assertEquals(1, response.content().size());
        assertEquals(1L, response.totalElements());
        assertTrue(response.first());
        ArgumentCaptor<Pageable> used = ArgumentCaptor.forClass(Pageable.class);
        verify(transactionRepository).findAll(ArgumentMatchers.<Specification<Transaction>>any(), used.capture());
        assertEquals(100, used.getValue().getPageSize());
        assertTrue(used.getValue().getSort().stream().anyMatch(o -> o.getProperty().equals("id")));
    }
}
