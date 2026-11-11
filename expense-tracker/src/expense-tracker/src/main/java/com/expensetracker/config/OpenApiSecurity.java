package com.expensetracker.config;

import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.context.annotation.Configuration;

/**
 * Adds the "Authorize" button to Swagger UI: paste the token from POST /api/auth/login
 * (just the token, Swagger adds "Bearer " itself) and every secured endpoint is called with it.
 * Annotation-only on purpose, so it does not clash with the OpenAPI bean P3 may define
 * for the title and description.
 */
@Configuration
@SecurityScheme(
        name = "bearerAuth",
        type = SecuritySchemeType.HTTP,
        scheme = "bearer",
        bearerFormat = "JWT",
        description = "JWT from POST /api/auth/login")
public class OpenApiSecurity {
}
