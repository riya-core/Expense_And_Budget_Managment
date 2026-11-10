package com.expensetracker.dto;

public record LoginResponse(String token, String tokenType, long expiresIn, AuthUserDto user) {}
