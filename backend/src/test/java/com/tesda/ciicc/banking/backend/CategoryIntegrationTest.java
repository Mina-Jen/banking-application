package com.tesda.ciicc.banking.backend;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tesda.ciicc.banking.backend.ai.CategorySuggestionClient;
import com.tesda.ciicc.banking.backend.ai.CategorySuggestionUnavailableException;
import com.tesda.ciicc.banking.backend.model.TransactionCategory;
import com.tesda.ciicc.banking.backend.model.TransactionType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Phase 4 end to end over the real stack (security chain, service, H2), with
 * only the AI call replaced by a mock. Proves three things the slices cannot:
 * the category column round-trips, a suggestion is not saved until accepted,
 * and ownership is enforced before the model is ever asked.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:categoryflow;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false",
        "JWT_SECRET=category-integration-test-secret-0123456789abcdef",
        "ADMIN_PASSWORD=category-admin-password"
})
class CategoryIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CategorySuggestionClient suggestions;

    private final ObjectMapper json = new ObjectMapper();

    @Test
    void aSuggestionIsNotStoredUntilTheOwnerAcceptsIt() throws Exception {
        register("cat-owner", "category-password-1");
        String token = accessToken("cat-owner", "category-password-1");
        String account = openAccount(token, "Cat Owner", "100.00");
        int transactionId = firstTransactionId(account, token);

        when(suggestions.suggestCategory(any())).thenReturn(TransactionCategory.GROCERIES);

        mockMvc.perform(post("/api/accounts/" + account + "/transactions/" + transactionId
                                + "/category-suggestion")
                        .header("Authorization", bearer(token))
                        .contentType("application/json")
                        .content("{\"description\":\"SM supermarket\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transactionId").value(transactionId))
                .andExpect(jsonPath("$.suggestedCategory").value("GROCERIES"));

        verify(suggestions).suggestCategory(new CategorySuggestionClient.CategorySuggestionInput(
                TransactionType.DEPOSIT, new BigDecimal("100.00"), "SM supermarket"));

        // The suggestion is advice: history still has no category.
        mockMvc.perform(get("/api/accounts/" + account + "/transactions")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transactions[0].category").doesNotExist());

        mockMvc.perform(put("/api/accounts/" + account + "/transactions/" + transactionId + "/category")
                        .header("Authorization", bearer(token))
                        .contentType("application/json")
                        .content("{\"category\":\"GROCERIES\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.category").value("GROCERIES"));

        mockMvc.perform(get("/api/accounts/" + account + "/transactions")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transactions[0].category").value("GROCERIES"));
    }

    @Test
    void anotherCustomerCannotAskTheModelAboutSomeonesTransaction() throws Exception {
        register("cat-alice", "category-password-1");
        register("cat-bob", "category-password-2");
        String aliceToken = accessToken("cat-alice", "category-password-1");
        String bobToken = accessToken("cat-bob", "category-password-2");
        String aliceAccount = openAccount(aliceToken, "Alice", "50.00");
        int aliceTransaction = firstTransactionId(aliceAccount, aliceToken);

        mockMvc.perform(post("/api/accounts/" + aliceAccount + "/transactions/" + aliceTransaction
                                + "/category-suggestion")
                        .header("Authorization", bearer(bobToken)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Account not found."));

        verifyNoInteractions(suggestions);
    }

    @Test
    void upstreamFailureIs503AndLeavesTheCategoryEmpty() throws Exception {
        register("cat-down", "category-password-1");
        String token = accessToken("cat-down", "category-password-1");
        String account = openAccount(token, "Cat Down", "10.00");
        int transactionId = firstTransactionId(account, token);

        when(suggestions.suggestCategory(any())).thenThrow(new CategorySuggestionUnavailableException(
                "Category suggestions are temporarily unavailable. Please try again later."));

        mockMvc.perform(post("/api/accounts/" + account + "/transactions/" + transactionId
                                + "/category-suggestion")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value(503));

        mockMvc.perform(get("/api/accounts/" + account + "/transactions")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transactions[0].category").doesNotExist());
    }

    @Test
    void aTransactionIdFromAnotherAccountIs404ForTheOwner() throws Exception {
        register("cat-ids", "category-password-1");
        String token = accessToken("cat-ids", "category-password-1");
        String first = openAccount(token, "First", "10.00");
        String second = openAccount(token, "Second", "10.00");
        int firstTransaction = firstTransactionId(first, token);

        // Both accounts belong to the same caller, but the id is looked up
        // inside the account named in the path, not across the bank.
        mockMvc.perform(post("/api/accounts/" + second + "/transactions/" + firstTransaction
                                + "/category-suggestion")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Transaction not found."));

        verifyNoInteractions(suggestions);
    }

    // =====================================================================
    //  Helpers
    // =====================================================================

    private void register(String username, String password) throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType("application/json")
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isCreated());
    }

    private String openAccount(String token, String name, String initialDeposit) throws Exception {
        MvcResult created = mockMvc.perform(post("/api/accounts")
                        .header("Authorization", bearer(token))
                        .contentType("application/json")
                        .content("{\"accountName\":\"" + name + "\",\"initialDeposit\":\""
                                + initialDeposit + "\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        return json.readTree(created.getResponse().getContentAsString())
                .get("accountNumber").asText();
    }

    private int firstTransactionId(String accountNumber, String token) throws Exception {
        MvcResult history = mockMvc.perform(get("/api/accounts/" + accountNumber + "/transactions")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andReturn();
        return json.readTree(history.getResponse().getContentAsString())
                .path("transactions").path(0).path("id").asInt();
    }

    private String accessToken(String username, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return json.readTree(result.getResponse().getContentAsString()).get("accessToken").asText();
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }
}
