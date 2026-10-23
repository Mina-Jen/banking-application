package com.tesda.ciicc.banking.backend.web;

import com.tesda.ciicc.banking.backend.ai.CategorySuggestionUnavailableException;
import com.tesda.ciicc.banking.backend.auth.JwtService;
import com.tesda.ciicc.banking.backend.auth.SecurityConfig;
import com.tesda.ciicc.banking.backend.exception.AccountNotFoundException;
import com.tesda.ciicc.banking.backend.exception.BankingValidationException;
import com.tesda.ciicc.banking.backend.exception.TransactionNotFoundException;
import com.tesda.ciicc.banking.backend.model.Transaction;
import com.tesda.ciicc.banking.backend.model.TransactionCategory;
import com.tesda.ciicc.banking.backend.model.TransactionType;
import com.tesda.ciicc.banking.backend.service.BankingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.sql.Timestamp;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Web layer of the category API: paths, response shapes and the error mapping
 * that turns "AI not configured" into 503 and "bad code" into 422. The
 * {@link BankingService} is a mock here; its ownership and persistence rules
 * are covered by BankingServiceCategoryTest and CategoryIntegrationTest.
 */
@WebMvcTest(controllers = CategoryController.class, properties = "app.api.max-requests=1000")
@Import(SecurityConfig.class)
class CategoryApiMockMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private BankingService banking;

    /** The filter chain only verifies tokens; token checks are not the subject here. */
    @MockitoBean
    private JwtService jwt;

    private static Transaction categorizedTransaction() {
        Transaction transaction = new Transaction();
        transaction.setId(42);
        transaction.setTransactionReference("TXN-cat001");
        transaction.setAccountNumber("100001");
        transaction.setTransactionType(TransactionType.WITHDRAW);
        transaction.setAmount(new BigDecimal("250.00"));
        transaction.setBalance(new BigDecimal("750.00"));
        transaction.setCategory(TransactionCategory.GROCERIES);
        transaction.setCreatedAt(Timestamp.valueOf("2026-10-22 10:00:00"));
        return transaction;
    }

    @Test
    void suggestionWithoutATokenIs401() throws Exception {
        mockMvc.perform(post("/api/accounts/100001/transactions/42/category-suggestion"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockCurrentUser
    void suggestionIsReturnedButNotStored() throws Exception {
        when(banking.suggestCategory(any(), eq("100001"), eq(42), eq("Jollibee lunch")))
                .thenReturn(TransactionCategory.DINING);

        mockMvc.perform(post("/api/accounts/100001/transactions/42/category-suggestion")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\":\"Jollibee lunch\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transactionId").value(42))
                .andExpect(jsonPath("$.suggestedCategory").value("DINING"));

        // Returning a suggestion is a read; only PUT .../category writes it.
        verify(banking, never()).setCategory(any(), any(), eq(42), any());
    }

    @Test
    @WithMockCurrentUser
    void suggestionWorksWithoutADescription() throws Exception {
        when(banking.suggestCategory(any(), eq("100001"), eq(42), isNull()))
                .thenReturn(TransactionCategory.OTHER);

        mockMvc.perform(post("/api/accounts/100001/transactions/42/category-suggestion"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.suggestedCategory").value("OTHER"));
    }

    @Test
    @WithMockCurrentUser
    void aMissingApiKeyIs503() throws Exception {
        when(banking.suggestCategory(any(), eq("100001"), eq(42), any()))
                .thenThrow(new CategorySuggestionUnavailableException(
                        "Category suggestions are not available: the Claude API key is not configured."));

        mockMvc.perform(post("/api/accounts/100001/transactions/42/category-suggestion")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\":\"lunch\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value(503))
                .andExpect(jsonPath("$.message", containsString("API key is not configured")));
    }

    @Test
    @WithMockCurrentUser
    void unknownTransactionIs404() throws Exception {
        when(banking.suggestCategory(any(), eq("100001"), eq(99), any()))
                .thenThrow(new TransactionNotFoundException("Transaction not found."));

        mockMvc.perform(post("/api/accounts/100001/transactions/99/category-suggestion"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Transaction not found."));
    }

    @Test
    @WithMockCurrentUser
    void foreignOrUnknownAccountIs404() throws Exception {
        when(banking.suggestCategory(any(), eq("999999"), eq(42), any()))
                .thenThrow(new AccountNotFoundException("Account not found."));

        mockMvc.perform(post("/api/accounts/999999/transactions/42/category-suggestion"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Account not found."));
    }

    @Test
    @WithMockCurrentUser
    void acceptingACategoryReturnsTheUpdatedTransaction() throws Exception {
        when(banking.setCategory(any(), eq("100001"), eq(42), eq("GROCERIES")))
                .thenReturn(categorizedTransaction());

        mockMvc.perform(put("/api/accounts/100001/transactions/42/category")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"category\":\"GROCERIES\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(42))
                .andExpect(jsonPath("$.type").value("WITHDRAW"))
                .andExpect(jsonPath("$.category").value("GROCERIES"));
    }

    @Test
    @WithMockCurrentUser
    void anUnknownCategoryIs422() throws Exception {
        when(banking.setCategory(any(), eq("100001"), eq(42), eq("PIZZA")))
                .thenThrow(new BankingValidationException(
                        "Category must be one of: " + TransactionCategory.allowedCodes() + "."));

        mockMvc.perform(put("/api/accounts/100001/transactions/42/category")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"category\":\"PIZZA\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message", containsString("Category must be one of")));
    }

    @Test
    @WithMockCurrentUser
    void aNonNumericTransactionIdIs400() throws Exception {
        mockMvc.perform(post("/api/accounts/100001/transactions/abc/category-suggestion"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Parameter 'transactionId' has an invalid value."));
    }
}
