package com.expensetracker.controller;

import com.expensetracker.config.RestAuthErrorHandler;
import com.expensetracker.config.SecurityConfig;
import com.expensetracker.dto.PageResponse;
import com.expensetracker.dto.TransactionResponse;
import com.expensetracker.entity.TransactionType;
import com.expensetracker.exception.ResourceNotFoundException;
import com.expensetracker.service.CustomUserDetailsService;
import com.expensetracker.service.JwtService;
import com.expensetracker.service.TransactionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.User;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TransactionController.class)
@Import({SecurityConfig.class, RestAuthErrorHandler.class})
class TransactionControllerTest {

    private static final String BEARER = "Bearer good-token";
    private static final String VALID_BODY = """
            {"categoryId":5,"type":"EXPENSE","amount":250.50,"description":"Lunch","date":"2026-11-03"}""";

    @Autowired MockMvc mockMvc;
    @MockBean TransactionService transactionService;
    @MockBean JwtService jwtService;
    @MockBean CustomUserDetailsService userDetailsService;

    @BeforeEach
    void validToken() {
        when(jwtService.validateAndGetEmail("good-token")).thenReturn(Optional.of("riya@example.com"));
        when(userDetailsService.loadUserByUsername("riya@example.com")).thenReturn(
                User.withUsername("riya@example.com").password("x").roles("USER").build());
    }

    private TransactionResponse sample() {
        return new TransactionResponse(7L, 5L, "Food", TransactionType.EXPENSE,
                new BigDecimal("250.50"), "Lunch", LocalDate.of(2026, 11, 3),
                LocalDateTime.of(2026, 11, 3, 12, 30, 45));
    }

    // ---------- security ----------

    @Test
    void noTokenReturns401() throws Exception {
        mockMvc.perform(get("/api/transactions"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Authentication required"));
    }

    // ---------- list ----------

    @Test
    void listReturnsExactlyThePageShapeFromTheContract() throws Exception {
        when(transactionService.list(any(), any(), any(), any(), any()))
                .thenReturn(new PageResponse<>(List.of(sample()), 0, 10, 42, 5, true, false));

        mockMvc.perform(get("/api/transactions").header("Authorization", BEARER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].id").value(7))
                .andExpect(jsonPath("$.content[0].categoryName").value("Food"))
                .andExpect(jsonPath("$.content[0].type").value("EXPENSE"))
                .andExpect(jsonPath("$.content[0].amount").value(250.5))
                .andExpect(jsonPath("$.content[0].date").value("2026-11-03"))
                .andExpect(jsonPath("$.content[0].createdAt").value("2026-11-03T12:30:45"))
                .andExpect(jsonPath("$.number").value(0))
                .andExpect(jsonPath("$.size").value(10))
                .andExpect(jsonPath("$.totalElements").value(42))
                .andExpect(jsonPath("$.totalPages").value(5))
                .andExpect(jsonPath("$.first").value(true))
                .andExpect(jsonPath("$.last").value(false));
    }

    @Test
    void listBindsEveryQueryParameter() throws Exception {
        when(transactionService.list(any(), any(), any(), any(), any()))
                .thenReturn(new PageResponse<>(List.of(), 1, 20, 0, 0, false, true));

        mockMvc.perform(get("/api/transactions")
                        .param("from", "2026-11-01").param("to", "2026-11-30")
                        .param("categoryId", "5").param("type", "EXPENSE")
                        .param("page", "1").param("size", "20").param("sort", "amount,asc")
                        .header("Authorization", BEARER))
                .andExpect(status().isOk());

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(transactionService).list(eq(LocalDate.of(2026, 11, 1)), eq(LocalDate.of(2026, 11, 30)),
                eq(5L), eq(TransactionType.EXPENSE), pageable.capture());
        assertEquals(1, pageable.getValue().getPageNumber());
        assertEquals(20, pageable.getValue().getPageSize());
        assertEquals(Sort.Direction.ASC, pageable.getValue().getSort().getOrderFor("amount").getDirection());
    }

    @Test
    void listDefaultsToTenPerPageNewestDateFirst() throws Exception {
        when(transactionService.list(any(), any(), any(), any(), any()))
                .thenReturn(new PageResponse<>(List.of(), 0, 10, 0, 0, true, true));

        mockMvc.perform(get("/api/transactions").header("Authorization", BEARER))
                .andExpect(status().isOk());

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(transactionService).list(any(), any(), any(), any(), pageable.capture());
        assertEquals(10, pageable.getValue().getPageSize());
        assertEquals(Sort.Direction.DESC, pageable.getValue().getSort().getOrderFor("date").getDirection());
    }

    @Test
    void aBadDateParameterReturns400() throws Exception {
        mockMvc.perform(get("/api/transactions?from=not-a-date").header("Authorization", BEARER))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void anUnknownTypeParameterReturns400() throws Exception {
        mockMvc.perform(get("/api/transactions?type=BANANA").header("Authorization", BEARER))
                .andExpect(status().isBadRequest());
    }

    // ---------- create / get / update / delete ----------

    @Test
    void createReturns201AndTheTransaction() throws Exception {
        when(transactionService.create(any())).thenReturn(sample());

        mockMvc.perform(post("/api/transactions")
                        .header("Authorization", BEARER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.amount").value(250.5))
                .andExpect(jsonPath("$.date").value("2026-11-03"));
    }

    @Test
    void createWithAZeroAmountAndNoDateReturnsBothFieldErrors() throws Exception {
        mockMvc.perform(post("/api/transactions")
                        .header("Authorization", BEARER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"categoryId":5,"type":"EXPENSE","amount":0,"description":"x"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.fieldErrors", hasSize(2)))
                .andExpect(jsonPath("$.fieldErrors[?(@.field == 'amount')]").exists())
                .andExpect(jsonPath("$.fieldErrors[?(@.field == 'date')]").exists());
    }

    @Test
    void anUnknownTransactionTypeInTheBodyReturns400() throws Exception {
        mockMvc.perform(post("/api/transactions")
                        .header("Authorization", BEARER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"categoryId":5,"type":"BANANA","amount":10,"date":"2026-11-03"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void aMissingTransactionReturns404WithTheContractShape() throws Exception {
        when(transactionService.get(9999L)).thenThrow(new ResourceNotFoundException("Transaction not found"));

        mockMvc.perform(get("/api/transactions/9999").header("Authorization", BEARER))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value("Transaction not found"))
                .andExpect(jsonPath("$.path").value("/api/transactions/9999"));
    }

    @Test
    void updateReturns200() throws Exception {
        when(transactionService.update(eq(7L), any())).thenReturn(sample());

        mockMvc.perform(put("/api/transactions/7")
                        .header("Authorization", BEARER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(7));
    }

    @Test
    void deleteReturns204WithNoBody() throws Exception {
        mockMvc.perform(delete("/api/transactions/7").header("Authorization", BEARER))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        verify(transactionService).delete(7L);
    }

    // ---------- other HTTP errors stay JSON ----------

    @Test
    void anUnknownUrlReturnsJson404NotAnHtmlPage() throws Exception {
        mockMvc.perform(get("/api/nothing-here").header("Authorization", BEARER))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void aWrongHttpMethodReturnsJson405() throws Exception {
        mockMvc.perform(patch("/api/transactions/7").header("Authorization", BEARER))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.status").value(405));
    }
}
