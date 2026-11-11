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
import com.expensetracker.util.Mappers;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Categories: default ones (user is null) are shared and read-only; custom ones belong to one user.
 * Every lookup is scoped to the logged-in user.
 */
@Service
@RequiredArgsConstructor
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final TransactionRepository transactionRepository;
    private final CurrentUser currentUser;

    @Transactional(readOnly = true)
    public List<CategoryResponse> list(TransactionType type) {
        Long userId = currentUser.id();
        List<Category> categories = (type == null)
                ? categoryRepository.findVisible(userId)
                : categoryRepository.findVisibleByType(userId, type);
        return categories.stream().map(Mappers::toResponse).toList();
    }

    @Transactional
    public CategoryResponse create(CategoryRequest request) {
        User user = currentUser.get();
        String name = request.name().trim();
        if (nameTaken(user.getId(), name, request.type(), null)) {
            throw new ConflictException("A category with this name already exists");
        }
        Category category = Category.builder()
                .name(name)
                .type(request.type())
                .user(user)
                .build();
        return Mappers.toResponse(categoryRepository.save(category));
    }

    @Transactional
    public CategoryResponse update(Long id, CategoryRequest request) {
        Long userId = currentUser.id();
        Category category = findOwned(id, userId);
        String name = request.name().trim();

        if (category.getType() != request.type() && transactionRepository.existsByCategoryId(id)) {
            throw new ConflictException("Cannot change the type of a category that has transactions");
        }
        if (nameTaken(userId, name, request.type(), id)) {
            throw new ConflictException("A category with this name already exists");
        }
        category.setName(name);
        category.setType(request.type());
        categoryRepository.save(category);
        return Mappers.toResponse(category);
    }

    @Transactional
    public void delete(Long id) {
        Long userId = currentUser.id();
        Category category = findOwned(id, userId);
        if (transactionRepository.existsByCategoryId(id)) {
            throw new ConflictException("Category is used by transactions and cannot be deleted");
        }
        categoryRepository.delete(category);
        // flush now so a budget that still uses it fails here (409) instead of at commit time
        categoryRepository.flush();
    }

    // ---- helpers ----

    /** A category the user owns. Defaults get a clear message; other people's categories look "not found". */
    private Category findOwned(Long id, Long userId) {
        Optional<Category> own = categoryRepository.findByIdAndUserId(id, userId);
        if (own.isPresent()) {
            return own.get();
        }
        boolean isDefault = categoryRepository.findVisibleById(id, userId)
                .filter(c -> c.getUser() == null)
                .isPresent();
        if (isDefault) {
            throw new BadRequestException("Default categories cannot be edited or deleted");
        }
        throw new ResourceNotFoundException("Category not found");
    }

    /** Same name (ignoring case) and same type among the categories this user can see. */
    private boolean nameTaken(Long userId, String name, TransactionType type, Long excludeId) {
        return categoryRepository.findVisible(userId).stream().anyMatch(c ->
                c.getType() == type
                        && c.getName().equalsIgnoreCase(name)
                        && !Objects.equals(c.getId(), excludeId));
    }
}
