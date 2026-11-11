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
import com.expensetracker.repository.TransactionSpecs;
import com.expensetracker.util.CurrentUser;
import com.expensetracker.util.Mappers;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Set;

/** Transaction CRUD. Every query is scoped to the logged-in user. */
@Service
@RequiredArgsConstructor
public class TransactionService {

    static final int MAX_PAGE_SIZE = 100;
    private static final Set<String> SORTABLE = Set.of("date", "amount", "createdAt", "type", "id");

    private final TransactionRepository transactionRepository;
    private final CategoryRepository categoryRepository;
    private final BudgetAlertService budgetAlertService;   // P2's hook (stub until P2 replaces it)
    private final CurrentUser currentUser;

    @Transactional(readOnly = true)
    public PageResponse<TransactionResponse> list(LocalDate from, LocalDate to, Long categoryId,
                                                  TransactionType type, Pageable pageable) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new BadRequestException("'from' must not be after 'to'");
        }
        Long userId = currentUser.id();
        Specification<Transaction> spec = Specification.where(TransactionSpecs.belongsTo(userId))
                .and(TransactionSpecs.from(from))
                .and(TransactionSpecs.to(to))
                .and(TransactionSpecs.category(categoryId))
                .and(TransactionSpecs.type(type))
                .and(TransactionSpecs.withCategory());
        Page<Transaction> page = transactionRepository.findAll(spec, safePageable(pageable));
        return PageResponse.of(page.map(Mappers::toResponse));
    }

    @Transactional(readOnly = true)
    public TransactionResponse get(Long id) {
        return Mappers.toResponse(findOwned(id, currentUser.id()));
    }

    @Transactional
    public TransactionResponse create(TransactionRequest request) {
        User user = currentUser.get();
        Category category = resolveCategory(request.categoryId(), request.type(), user.getId());

        Transaction transaction = Transaction.builder()
                .user(user)
                .category(category)
                .type(request.type())
                .amount(scale(request.amount()))
                .description(clean(request.description()))
                .date(request.date())
                .build();
        Transaction saved = transactionRepository.save(transaction);
        budgetAlertService.check(saved);               // contract: after create and update, never delete
        return Mappers.toResponse(saved);
    }

    @Transactional
    public TransactionResponse update(Long id, TransactionRequest request) {
        Long userId = currentUser.id();
        Transaction transaction = findOwned(id, userId);
        Category category = resolveCategory(request.categoryId(), request.type(), userId);

        transaction.setCategory(category);
        transaction.setType(request.type());
        transaction.setAmount(scale(request.amount()));
        transaction.setDescription(clean(request.description()));
        transaction.setDate(request.date());
        Transaction saved = transactionRepository.save(transaction);
        budgetAlertService.check(saved);
        return Mappers.toResponse(saved);
    }

    @Transactional
    public void delete(Long id) {
        Transaction transaction = findOwned(id, currentUser.id());
        transactionRepository.delete(transaction);     // no alert check on delete
    }

    // ---- helpers ----

    /** Other people's transactions look exactly like missing ones (404), as the contract requires. */
    private Transaction findOwned(Long id, Long userId) {
        return transactionRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction not found"));
    }

    /** null category is allowed; otherwise it must be visible to this user and match the type. */
    private Category resolveCategory(Long categoryId, TransactionType type, Long userId) {
        if (categoryId == null) {
            return null;
        }
        Category category = categoryRepository.findVisibleById(categoryId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found"));
        if (category.getType() != type) {
            throw new BadRequestException("Category type (" + category.getType()
                    + ") does not match transaction type (" + type + ")");
        }
        return category;
    }

    /** Whitelist sort fields (a bad one would be a 500), cap the page size, keep paging stable. */
    private Pageable safePageable(Pageable pageable) {
        for (Sort.Order order : pageable.getSort()) {
            if (!SORTABLE.contains(order.getProperty())) {
                throw new BadRequestException("Cannot sort by '" + order.getProperty() + "'");
            }
        }
        Sort sort = pageable.getSort().isSorted() ? pageable.getSort() : Sort.by(Sort.Direction.DESC, "date");
        boolean sortsById = sort.stream().anyMatch(o -> o.getProperty().equals("id"));
        if (!sortsById) {
            sort = sort.and(Sort.by(Sort.Direction.DESC, "id"));   // tie-breaker so pages never overlap
        }
        return PageRequest.of(pageable.getPageNumber(),
                Math.min(pageable.getPageSize(), MAX_PAGE_SIZE), sort);
    }

    private static BigDecimal scale(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.HALF_UP);
    }

    private static String clean(String text) {
        if (text == null) return null;
        String trimmed = text.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
