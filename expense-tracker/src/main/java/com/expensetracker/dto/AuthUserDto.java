package com.expensetracker.dto;

/** Response of POST /api/auth/register and the "user" object inside the login response. */
public record AuthUserDto(Long id, String name, String email) {}
