package com.expensetracker.controller;

import com.expensetracker.config.RestAuthErrorHandler;
import com.expensetracker.config.SecurityConfig;
import com.expensetracker.dto.AuthUserDto;
import com.expensetracker.dto.LoginResponse;
import com.expensetracker.exception.ConflictException;
import com.expensetracker.service.AuthService;
import com.expensetracker.service.CustomUserDetailsService;
import com.expensetracker.service.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Tests the HTTP layer only (routing, security rules, JSON shapes). The service is mocked, no database. */
@WebMvcTest(AuthController.class)
@Import({SecurityConfig.class, RestAuthErrorHandler.class})
class AuthControllerTest {

    @Autowired MockMvc mockMvc;
    @MockBean AuthService authService;
    @MockBean JwtService jwtService;
    @MockBean CustomUserDetailsService userDetailsService;

    @Test
    void registerReturns201AndTheUserWithoutAPassword() throws Exception {
        when(authService.register(any())).thenReturn(new AuthUserDto(1L, "Riya", "riya@example.com"));

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Riya","email":"riya@example.com","password":"secret123"}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("Riya"))
                .andExpect(jsonPath("$.email").value("riya@example.com"))
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    void registerWithBadInputReturnsTheContractErrorShape() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"","email":"not-an-email","password":"123"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.path").value("/api/auth/register"))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.fieldErrors", hasSize(3)))
                .andExpect(jsonPath("$.fieldErrors[?(@.field == 'email')]").exists())
                .andExpect(jsonPath("$.fieldErrors[?(@.field == 'password')]").exists());
    }

    @Test
    void registeringAnExistingEmailReturns409() throws Exception {
        when(authService.register(any())).thenThrow(new ConflictException("Email is already registered"));

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Riya","email":"riya@example.com","password":"secret123"}"""))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value("Email is already registered"))
                .andExpect(jsonPath("$.fieldErrors").doesNotExist());
    }

    @Test
    void loginReturnsTheTokenResponseFromTheContract() throws Exception {
        when(authService.login(any())).thenReturn(new LoginResponse(
                "TOKEN", "Bearer", 3600, new AuthUserDto(1L, "Riya", "riya@example.com")));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"riya@example.com","password":"secret123"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("TOKEN"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(3600))
                .andExpect(jsonPath("$.user.id").value(1))
                .andExpect(jsonPath("$.user.email").value("riya@example.com"));
    }

    @Test
    void loginWithAWrongPasswordReturns401() throws Exception {
        when(authService.login(any())).thenThrow(new BadCredentialsException("bad"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"riya@example.com","password":"wrong"}"""))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value("Invalid email or password"));
    }

    @Test
    void malformedJsonReturns400NotA500() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ this is not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }
}
