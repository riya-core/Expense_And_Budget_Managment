package com.expensetracker.controller;

import com.expensetracker.dto.CategoryRequest;
import com.expensetracker.dto.CategoryResponse;
import com.expensetracker.entity.TransactionType;
import com.expensetracker.service.CategoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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

import java.util.List;

@RestController
@RequestMapping("/api/categories")
@RequiredArgsConstructor
@Tag(name = "Categories", description = "Default categories are shared and read-only; custom ones belong to the logged-in user.")
@SecurityRequirement(name = "bearerAuth")
public class CategoryController {

    private final CategoryService categoryService;

    @Operation(summary = "List categories",
            description = "The default categories plus the user's own. isDefault tells them apart.")
    @ApiResponse(responseCode = "200", description = "OK")
    @ApiResponse(responseCode = "400", description = "Unknown type value")
    @ApiResponse(responseCode = "401", description = "Missing, invalid or expired token")
    @GetMapping
    public List<CategoryResponse> list(
            @Parameter(description = "Only INCOME or only EXPENSE categories (optional)")
            @RequestParam(required = false) TransactionType type) {
        return categoryService.list(type);
    }

    @Operation(summary = "Create a custom category")
    @ApiResponse(responseCode = "201", description = "Created")
    @ApiResponse(responseCode = "400", description = "Validation failed, see fieldErrors")
    @ApiResponse(responseCode = "401", description = "Missing, invalid or expired token")
    @ApiResponse(responseCode = "409", description = "A category with this name and type already exists")
    @PostMapping
    public ResponseEntity<CategoryResponse> create(@Valid @RequestBody CategoryRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(categoryService.create(request));
    }

    @Operation(summary = "Rename or retype a custom category",
            description = "The type cannot change once the category has transactions.")
    @ApiResponse(responseCode = "200", description = "Updated")
    @ApiResponse(responseCode = "400", description = "Validation failed, or the category is a default one")
    @ApiResponse(responseCode = "401", description = "Missing, invalid or expired token")
    @ApiResponse(responseCode = "404", description = "Not found (or it belongs to another user)")
    @ApiResponse(responseCode = "409", description = "Duplicate name, or the type is locked by existing transactions")
    @PutMapping("/{id}")
    public CategoryResponse update(@PathVariable Long id, @Valid @RequestBody CategoryRequest request) {
        return categoryService.update(id, request);
    }

    @Operation(summary = "Delete a custom category")
    @ApiResponse(responseCode = "204", description = "Deleted")
    @ApiResponse(responseCode = "400", description = "Default categories cannot be deleted")
    @ApiResponse(responseCode = "401", description = "Missing, invalid or expired token")
    @ApiResponse(responseCode = "404", description = "Not found (or it belongs to another user)")
    @ApiResponse(responseCode = "409", description = "Still used by transactions or budgets")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        categoryService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
