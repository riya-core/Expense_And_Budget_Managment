package com.expensetracker.controller;

import com.expensetracker.config.RestAuthErrorHandler;
import com.expensetracker.config.SecurityConfig;
import com.expensetracker.dto.CategoryResponse;
import com.expensetracker.entity.TransactionType;
import com.expensetracker.exception.BadRequestException;
import com.expensetracker.exception.ConflictException;
import com.expensetracker.service.CategoryService;
import com.expensetracker.service.CustomUserDetailsService;
import com.expensetracker.service.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.User;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CategoryController.class)
@Import({SecurityConfig.class, RestAuthErrorHandler.class})
class CategoryControllerTest {

    private static final String BEARER = "Bearer good-token";

    @Autowired MockMvc mockMvc;
    @MockBean CategoryService categoryService;
    @MockBean JwtService jwtService;
    @MockBean CustomUserDetailsService userDetailsService;

    /** Makes "good-token" a valid login for riya@example.com, so the real JwtAuthFilter is exercised. */
    @BeforeEach
    void validToken() {
        when(jwtService.validateAndGetEmail("good-token")).thenReturn(Optional.of("riya@example.com"));
        when(userDetailsService.loadUserByUsername("riya@example.com")).thenReturn(
                User.withUsername("riya@example.com").password("x").roles("USER").build());
    }

    // ---------- security ----------

    @Test
    void noTokenReturns401WithTheContractShape() throws Exception {
        mockMvc.perform(get("/api/categories"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"))
                .andExpect(jsonPath("$.message").value("Authentication required"))
                .andExpect(jsonPath("$.path").value("/api/categories"));
    }

    @Test
    void invalidTokenReturns401() throws Exception {
        // jwtService returns Optional.empty() for any token we did not stub
        mockMvc.perform(get("/api/categories").header("Authorization", "Bearer forged-token"))
                .andExpect(status().isUnauthorized());
    }

    // ---------- list ----------

    @Test
    void listReturnsCategoriesWithTheIsDefaultField() throws Exception {
        when(categoryService.list(null)).thenReturn(List.of(
                new CategoryResponse(1L, "Food", TransactionType.EXPENSE, true),
                new CategoryResponse(14L, "Coffee", TransactionType.EXPENSE, false)));

        mockMvc.perform(get("/api/categories").header("Authorization", BEARER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].name").value("Food"))
                .andExpect(jsonPath("$[0].type").value("EXPENSE"))
                .andExpect(jsonPath("$[0].isDefault").value(true))
                .andExpect(jsonPath("$[1].isDefault").value(false));
    }

    @Test
    void listPassesTheTypeFilterToTheService() throws Exception {
        when(categoryService.list(TransactionType.INCOME)).thenReturn(List.of(
                new CategoryResponse(2L, "Salary", TransactionType.INCOME, true)));

        mockMvc.perform(get("/api/categories?type=INCOME").header("Authorization", BEARER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Salary"));
    }

    @Test
    void anUnknownTypeValueReturns400() throws Exception {
        mockMvc.perform(get("/api/categories?type=BANANA").header("Authorization", BEARER))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    // ---------- create / update / delete ----------

    @Test
    void createReturns201() throws Exception {
        when(categoryService.create(any())).thenReturn(
                new CategoryResponse(14L, "Coffee", TransactionType.EXPENSE, false));

        mockMvc.perform(post("/api/categories")
                        .header("Authorization", BEARER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Coffee","type":"EXPENSE"}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(14))
                .andExpect(jsonPath("$.isDefault").value(false));
    }

    @Test
    void createWithABlankNameReturnsFieldErrors() throws Exception {
        mockMvc.perform(post("/api/categories")
                        .header("Authorization", BEARER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"  ","type":"EXPENSE"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("name"));
    }

    @Test
    void editingADefaultCategoryReturns400WithTheServiceMessage() throws Exception {
        when(categoryService.update(eq(1L), any())).thenThrow(
                new BadRequestException("Default categories cannot be edited or deleted"));

        mockMvc.perform(put("/api/categories/1")
                        .header("Authorization", BEARER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Meals","type":"EXPENSE"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Default categories cannot be edited or deleted"));
    }

    @Test
    void deleteReturns204WithNoBody() throws Exception {
        mockMvc.perform(delete("/api/categories/14").header("Authorization", BEARER))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        verify(categoryService).delete(14L);
    }

    @Test
    void deletingACategoryThatIsInUseReturns409() throws Exception {
        doThrow(new ConflictException("Category is used by transactions and cannot be deleted"))
                .when(categoryService).delete(14L);

        mockMvc.perform(delete("/api/categories/14").header("Authorization", BEARER))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }
}
