package com.expensetracker.service;

import com.expensetracker.dto.CategoryRequest;
import com.expensetracker.dto.CategoryResponse;
import com.expensetracker.entity.Category;
import com.expensetracker.entity.TransactionType;
import com.expensetracker.entity.User;
import com.expensetracker.exception.BadRequestException;
import com.expensetracker.exception.ConflictException;
import com.expensetracker.exception.ResourceNotFoundException;
import com.expensetracker.repository.CategoryRepository;
import com.expensetracker.repository.TransactionRepository;
import com.expensetracker.util.CurrentUser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CategoryServiceTest {

    private static final Long UID = 7L;

    @Mock CategoryRepository categoryRepository;
    @Mock TransactionRepository transactionRepository;
    @Mock CurrentUser currentUser;
    @InjectMocks CategoryService categoryService;

    private User me() {
        return User.builder().id(UID).name("Riya").email("riya@example.com").build();
    }

    private Category defaultCategory(Long id, String name, TransactionType type) {
        return Category.builder().id(id).name(name).type(type).user(null).build();
    }

    private Category myCategory(Long id, String name, TransactionType type) {
        return Category.builder().id(id).name(name).type(type).user(me()).build();
    }

    // ---------- list ----------

    @Test
    void listWithoutTypeReturnsDefaultsAndOwnWithFlag() {
        when(currentUser.id()).thenReturn(UID);
        when(categoryRepository.findVisible(UID)).thenReturn(List.of(
                defaultCategory(1L, "Food", TransactionType.EXPENSE),
                myCategory(10L, "Coffee", TransactionType.EXPENSE)));

        List<CategoryResponse> result = categoryService.list(null);

        assertEquals(2, result.size());
        assertTrue(result.get(0).isDefault());
        assertFalse(result.get(1).isDefault());
        verify(categoryRepository, never()).findVisibleByType(any(), any());
    }

    @Test
    void listWithTypeUsesTheTypeQuery() {
        when(currentUser.id()).thenReturn(UID);
        when(categoryRepository.findVisibleByType(UID, TransactionType.INCOME))
                .thenReturn(List.of(defaultCategory(2L, "Salary", TransactionType.INCOME)));

        List<CategoryResponse> result = categoryService.list(TransactionType.INCOME);

        assertEquals(1, result.size());
        assertEquals("Salary", result.get(0).name());
    }

    // ---------- create ----------

    @Test
    void createSavesCategoryForCurrentUser() {
        when(currentUser.get()).thenReturn(me());
        when(categoryRepository.findVisible(UID)).thenReturn(List.of(
                defaultCategory(1L, "Food", TransactionType.EXPENSE)));
        when(categoryRepository.save(any(Category.class))).thenAnswer(inv -> {
            Category c = inv.getArgument(0);
            c.setId(10L);
            return c;
        });

        CategoryResponse response = categoryService.create(new CategoryRequest("  Coffee ", TransactionType.EXPENSE));

        assertEquals(10L, response.id());
        assertEquals("Coffee", response.name());
        assertFalse(response.isDefault());
        ArgumentCaptor<Category> saved = ArgumentCaptor.forClass(Category.class);
        verify(categoryRepository).save(saved.capture());
        assertEquals(UID, saved.getValue().getUser().getId());
    }

    @Test
    void createRejectsDuplicateNameIgnoringCase() {
        when(currentUser.get()).thenReturn(me());
        when(categoryRepository.findVisible(UID)).thenReturn(List.of(
                defaultCategory(1L, "Food", TransactionType.EXPENSE)));

        assertThrows(ConflictException.class,
                () -> categoryService.create(new CategoryRequest(" food ", TransactionType.EXPENSE)));
        verify(categoryRepository, never()).save(any());
    }

    @Test
    void sameNameWithDifferentTypeIsAllowed() {
        when(currentUser.get()).thenReturn(me());
        when(categoryRepository.findVisible(UID)).thenReturn(List.of(
                defaultCategory(1L, "Food", TransactionType.EXPENSE)));
        when(categoryRepository.save(any(Category.class))).thenAnswer(inv -> inv.getArgument(0));

        CategoryResponse response = categoryService.create(new CategoryRequest("Food", TransactionType.INCOME));

        assertEquals(TransactionType.INCOME, response.type());
    }

    // ---------- update ----------

    @Test
    void updateRenamesOwnCategory() {
        Category own = myCategory(10L, "Coffee", TransactionType.EXPENSE);
        when(currentUser.id()).thenReturn(UID);
        when(categoryRepository.findByIdAndUserId(10L, UID)).thenReturn(Optional.of(own));
        when(categoryRepository.findVisible(UID)).thenReturn(List.of(own));

        CategoryResponse response = categoryService.update(10L, new CategoryRequest("Tea", TransactionType.EXPENSE));

        assertEquals("Tea", response.name());
        verify(categoryRepository).save(own);
    }

    @Test
    void updateDefaultCategoryIsRejected() {
        when(currentUser.id()).thenReturn(UID);
        when(categoryRepository.findByIdAndUserId(1L, UID)).thenReturn(Optional.empty());
        when(categoryRepository.findVisibleById(1L, UID))
                .thenReturn(Optional.of(defaultCategory(1L, "Food", TransactionType.EXPENSE)));

        assertThrows(BadRequestException.class,
                () -> categoryService.update(1L, new CategoryRequest("Meals", TransactionType.EXPENSE)));
        verify(categoryRepository, never()).save(any());
    }

    @Test
    void updateSomeoneElsesCategoryLooksNotFound() {
        when(currentUser.id()).thenReturn(UID);
        when(categoryRepository.findByIdAndUserId(99L, UID)).thenReturn(Optional.empty());
        when(categoryRepository.findVisibleById(99L, UID)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> categoryService.update(99L, new CategoryRequest("X", TransactionType.EXPENSE)));
    }

    @Test
    void changingTypeIsBlockedWhenCategoryHasTransactions() {
        Category own = myCategory(10L, "Coffee", TransactionType.EXPENSE);
        when(currentUser.id()).thenReturn(UID);
        when(categoryRepository.findByIdAndUserId(10L, UID)).thenReturn(Optional.of(own));
        when(transactionRepository.existsByCategoryId(10L)).thenReturn(true);

        assertThrows(ConflictException.class,
                () -> categoryService.update(10L, new CategoryRequest("Coffee", TransactionType.INCOME)));
        verify(categoryRepository, never()).save(any());
    }

    // ---------- delete ----------

    @Test
    void deleteRemovesOwnCategory() {
        Category own = myCategory(10L, "Coffee", TransactionType.EXPENSE);
        when(currentUser.id()).thenReturn(UID);
        when(categoryRepository.findByIdAndUserId(10L, UID)).thenReturn(Optional.of(own));
        when(transactionRepository.existsByCategoryId(10L)).thenReturn(false);

        categoryService.delete(10L);

        verify(categoryRepository).delete(own);
        verify(categoryRepository).flush();
    }

    @Test
    void deleteIsBlockedWhenTransactionsUseIt() {
        Category own = myCategory(10L, "Coffee", TransactionType.EXPENSE);
        when(currentUser.id()).thenReturn(UID);
        when(categoryRepository.findByIdAndUserId(10L, UID)).thenReturn(Optional.of(own));
        when(transactionRepository.existsByCategoryId(10L)).thenReturn(true);

        assertThrows(ConflictException.class, () -> categoryService.delete(10L));
        verify(categoryRepository, never()).delete(any());
    }
}
