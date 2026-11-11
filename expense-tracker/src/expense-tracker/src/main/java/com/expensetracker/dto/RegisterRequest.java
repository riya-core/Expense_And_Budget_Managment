package com.expensetracker.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @Schema(example = "Riya") @NotBlank @Size(max = 100) String name,
        @Schema(example = "riya@example.com") @NotBlank @Email @Size(max = 150) String email,
        @Schema(example = "secret123", description = "6 to 72 characters") @NotBlank @Size(min = 6, max = 72) String password) {}
