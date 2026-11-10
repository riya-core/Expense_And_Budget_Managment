package com.expensetracker.controller;

import com.expensetracker.dto.PageResponse;
import com.expensetracker.dto.TransactionRequest;
import com.expensetracker.dto.TransactionResponse;
import com.expensetracker.entity.TransactionType;
import com.expensetracker.service.TransactionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/transactions")
@RequiredArgsConstructor
@Tag(name = "Transactions", description = "Income and expense records of the logged-in user.")
@SecurityRequirement(name = "bearerAuth")
public class TransactionController {

    private final TransactionService transactionService;

    @Operation(summary = "List transactions (filtered and paged)",
            description = "Only the logged-in user's transactions. Every filter is optional. Default: 10 per page, "
                    + "newest date first, page size capped at 100. Sortable fields: date, amount, createdAt, type, id.")
    @ApiResponse(responseCode = "200", description = "A page: content, number, size, totalElements, totalPages, first, last")
    @ApiResponse(responseCode = "400", description = "Bad date, type or sort field, or 'from' is after 'to'")
    @ApiResponse(responseCode = "401", description = "Missing, invalid or expired token")
    @GetMapping
    public PageResponse<TransactionResponse> list(
            @Parameter(description = "First day to include (yyyy-MM-dd)", example = "2026-11-01")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @Parameter(description = "Last day to include (yyyy-MM-dd)", example = "2026-11-30")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @Parameter(description = "Only this category id")
            @RequestParam(required = false) Long categoryId,
            @Parameter(description = "Only INCOME or only EXPENSE")
            @RequestParam(required = false) TransactionType type,
            @ParameterObject
            @PageableDefault(size = 10, sort = "date", direction = Sort.Direction.DESC) Pageable pageable) {
        return transactionService.list(from, to, categoryId, type, pageable);
    }

    @Operation(summary = "Get one transaction")
    @ApiResponse(responseCode = "200", description = "OK")
    @ApiResponse(responseCode = "401", description = "Missing, invalid or expired token")
    @ApiResponse(responseCode = "404", description = "Not found (or it belongs to another user)")
    @GetMapping("/{id}")
    public TransactionResponse get(@PathVariable Long id) {
        return transactionService.get(id);
    }

    @Operation(summary = "Add a transaction",
            description = "categoryId is optional; if given it must be a default category or one of your own, "
                    + "and its type must match the transaction type. The date must be between 2000-01-01 and one year from today.")
    @ApiResponse(responseCode = "201", description = "Created")
    @ApiResponse(responseCode = "400", description = "Validation failed, category type mismatch, or date out of range")
    @ApiResponse(responseCode = "401", description = "Missing, invalid or expired token")
    @ApiResponse(responseCode = "404", description = "Category not found")
    @PostMapping
    public ResponseEntity<TransactionResponse> create(@Valid @RequestBody TransactionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(transactionService.create(request));
    }

    @Operation(summary = "Replace a transaction",
            description = "Same rules as creating one. All fields are replaced.")
    @ApiResponse(responseCode = "200", description = "Updated")
    @ApiResponse(responseCode = "400", description = "Validation failed, category type mismatch, or date out of range")
    @ApiResponse(responseCode = "401", description = "Missing, invalid or expired token")
    @ApiResponse(responseCode = "404", description = "Transaction or category not found")
    @PutMapping("/{id}")
    public TransactionResponse update(@PathVariable Long id, @Valid @RequestBody TransactionRequest request) {
        return transactionService.update(id, request);
    }

    @Operation(summary = "Delete a transaction")
    @ApiResponse(responseCode = "204", description = "Deleted")
    @ApiResponse(responseCode = "401", description = "Missing, invalid or expired token")
    @ApiResponse(responseCode = "404", description = "Not found (or it belongs to another user)")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        transactionService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
