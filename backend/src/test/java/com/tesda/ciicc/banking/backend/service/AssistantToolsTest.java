package com.tesda.ciicc.banking.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tesda.ciicc.banking.backend.ai.AssistantModelClient.AssistantToolSpec;
import com.tesda.ciicc.banking.backend.auth.CurrentUser;
import com.tesda.ciicc.banking.backend.exception.AccountNotFoundException;
import com.tesda.ciicc.banking.backend.model.Account;
import com.tesda.ciicc.banking.backend.model.Role;
import com.tesda.ciicc.banking.backend.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * The security boundary of the assistant tools: account scoping comes from the
 * {@link CurrentUser} parameter, model-supplied user ids are dropped, and
 * {@code propose_transfer} never calls a mutating service method.
 */
@ExtendWith(MockitoExtension.class)
class AssistantToolsTest {

    @Mock
    private BankingService banking;

    @Mock
    private TransactionRepository transactions;

    private final ObjectMapper json = new ObjectMapper();
    private final CurrentUser caller = new CurrentUser(7, "juan", Role.CUSTOMER);

    private AssistantTools tools;

    @BeforeEach
    void setUp() {
        tools = new AssistantTools(banking, transactions, json);
    }

    @Test
    void getBalanceUsesTheAuthenticatedCallerAndDropsAnInjectedUserId() throws Exception {
        when(banking.getAccount(caller, "200001"))
                .thenReturn(account("200001", "Everyday Checking", "2500.00"));
        JsonNode input = json.readTree("{\"accountNumber\":\"200001\",\"userId\":9999}");

        AssistantTools.ToolExecution execution =
                tools.execute(caller, AssistantTools.GET_BALANCE, input);

        assertTrue(execution.resultJson().contains("\"balance\":\"2500.00\""));
        assertEquals(Map.of("accountNumber", "200001"), execution.arguments());
        assertNull(execution.proposal());
        verify(banking).getAccount(caller, "200001");
        verifyNoMoreInteractions(banking);
    }

    @Test
    void getBalanceWithoutAnAccountListsOnlyTheCallersAccounts() throws Exception {
        when(banking.listAccounts(caller, 0, 100)).thenReturn(new PageImpl<>(List.of(
                account("200001", "Everyday Checking", "2500.00"),
                account("200002", "Savings", "10000.00"))));

        AssistantTools.ToolExecution execution =
                tools.execute(caller, AssistantTools.GET_BALANCE, json.readTree("{}"));

        assertTrue(execution.resultJson().contains("\"accountNumber\":\"200001\""));
        assertTrue(execution.resultJson().contains("\"accountNumber\":\"200002\""));
        verify(banking).listAccounts(caller, 0, 100);
        verifyNoMoreInteractions(banking);
    }

    @Test
    void aForeignAccountIsRejectedByTheCallersOwnershipCheck() throws Exception {
        when(banking.getAccount(caller, "200009"))
                .thenThrow(new AccountNotFoundException("Account not found."));
        JsonNode input = json.readTree("{\"accountNumber\":\"200009\",\"userId\":9999}");

        AssistantTools.ToolExecution execution =
                tools.execute(caller, AssistantTools.GET_BALANCE, input);

        assertTrue(execution.resultJson().contains("Account not found."));
        assertEquals(Map.of("accountNumber", "200009"), execution.arguments());
        verify(banking).getAccount(caller, "200009");
    }

    @Test
    void proposeTransferReturnsAProposalAndNeverMovesMoney() throws Exception {
        when(banking.getAccount(caller, "200001"))
                .thenReturn(account("200001", "Everyday Checking", "2500.00"));
        JsonNode input = json.readTree("""
                {"sourceAccountNumber":"200001","destinationAccountNumber":"200002","amount":"100.00"}
                """);

        AssistantTools.ToolExecution execution =
                tools.execute(caller, AssistantTools.PROPOSE_TRANSFER, input);

        assertNotNull(execution.proposal());
        assertEquals("PROPOSED", execution.proposal().status());
        assertEquals("100.00", execution.proposal().amount());
        assertTrue(execution.resultJson().contains("\"executed\":false"));
        assertTrue(execution.resultJson().contains("\"availableBalance\":\"2500.00\""));

        // Only the source lookup happened: no destination probe, and no
        // mutating BankingService call of any kind.
        verify(banking).getAccount(caller, "200001");
        verify(banking, never()).getAccount(caller, "200002");
        verify(banking, never()).transfer(any(), anyString(), anyString(), any());
        verify(banking, never()).deposit(any(), anyString(), any());
        verify(banking, never()).withdraw(any(), anyString(), any());
        verifyNoMoreInteractions(banking);
    }

    @Test
    void proposeTransferRejectsAnAmountAboveTheBalance() throws Exception {
        when(banking.getAccount(caller, "200001"))
                .thenReturn(account("200001", "Everyday Checking", "2500.00"));
        JsonNode input = json.readTree("""
                {"sourceAccountNumber":"200001","destinationAccountNumber":"200002","amount":"3000.00"}
                """);

        AssistantTools.ToolExecution execution =
                tools.execute(caller, AssistantTools.PROPOSE_TRANSFER, input);

        assertNull(execution.proposal());
        assertTrue(execution.resultJson().contains("exceeds the available balance of 2500.00"));
        verify(banking, never()).transfer(any(), anyString(), anyString(), any());
    }

    @Test
    void anUnknownToolIsAnErrorResult() throws Exception {
        AssistantTools.ToolExecution execution =
                tools.execute(caller, "drop_tables", json.readTree("{}"));

        assertTrue(execution.resultJson().contains("Unknown tool: drop_tables."));
        verifyNoInteractions(banking, transactions);
    }

    @Test
    void anInvalidSearchCategoryNeverReachesTheDatabase() throws Exception {
        when(banking.getAccount(caller, "200001"))
                .thenReturn(account("200001", "Everyday Checking", "2500.00"));
        JsonNode input = json.readTree("{\"accountNumber\":\"200001\",\"category\":\"TRAVEL\"}");

        AssistantTools.ToolExecution execution =
                tools.execute(caller, AssistantTools.SEARCH_TRANSACTIONS, input);

        assertTrue(execution.resultJson().contains("category must be one of"));
        verifyNoInteractions(transactions);
    }

    @Test
    void everyToolSchemaUsesOnlyServerScopedArguments() {
        List<AssistantToolSpec> specifications = tools.specifications();
        assertEquals(4, specifications.size());

        for (AssistantToolSpec specification : specifications) {
            assertEquals("object", specification.inputSchema().get("type"));
            assertFalse(specification.inputSchema().toString().toLowerCase(Locale.ROOT)
                    .contains("userid"));
            assertFalse(specification.inputSchema().toString().toLowerCase(Locale.ROOT)
                    .contains("password"));
        }
    }

    private static Account account(String number, String name, String balance) {
        return new Account(number, name, new BigDecimal(balance));
    }
}
