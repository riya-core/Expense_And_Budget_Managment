package com.expensetracker.controller;

import com.expensetracker.dto.AuthUserDto;
import com.expensetracker.dto.LoginRequest;
import com.expensetracker.dto.LoginResponse;
import com.expensetracker.dto.RegisterRequest;
import com.expensetracker.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Auth", description = "Register and log in. These two endpoints need no token.")
public class AuthController {

    private final AuthService authService;

    @Operation(summary = "Register a new user",
            description = "Emails are trimmed and lower-cased. Limited to 10 requests per minute per IP.")
    @ApiResponse(responseCode = "201", description = "User created (the password is never returned)")
    @ApiResponse(responseCode = "400", description = "Validation failed, see fieldErrors")
    @ApiResponse(responseCode = "409", description = "Email is already registered")
    @ApiResponse(responseCode = "429", description = "Too many attempts, see the Retry-After header")
    @PostMapping("/register")
    public ResponseEntity<AuthUserDto> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request));
    }

    @Operation(summary = "Log in and get a JWT",
            description = "Send the returned token as 'Authorization: Bearer <token>'. It expires after expiresIn seconds. "
                    + "Limited to 10 requests per minute per IP.")
    @ApiResponse(responseCode = "200", description = "Login succeeded")
    @ApiResponse(responseCode = "400", description = "Validation failed, see fieldErrors")
    @ApiResponse(responseCode = "401", description = "Invalid email or password")
    @ApiResponse(responseCode = "429", description = "Too many attempts, see the Retry-After header")
    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }
}
