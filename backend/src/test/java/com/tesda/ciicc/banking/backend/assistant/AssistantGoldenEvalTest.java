package com.tesda.ciicc.banking.backend.assistant;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.tesda.ciicc.banking.backend.ai.AssistantModelClient;
import com.tesda.ciicc.banking.backend.ai.AssistantModelClient.AssistantBlock;
import com.tesda.ciicc.banking.backend.ai.AssistantModelClient.AssistantMessage;
import com.tesda.ciicc.banking.backend.ai.AssistantModelClient.AssistantText;
import com.tesda.ciicc.banking.backend.ai.AssistantModelClient.AssistantToolResult;
import com.tesda.ciicc.banking.backend.ai.AssistantModelClient.AssistantToolUse;
import com.tesda.ciicc.banking.backend.ai.AssistantModelClient.AssistantTurn;
import com.tesda.ciicc.banking.backend.assistant.AssistantGoldenSet.ExpectedCall;
import com.tesda.ciicc.banking.backend.assistant.AssistantGoldenSet.ExpectedProposal;
import com.tesda.ciicc.banking.backend.assistant.AssistantGoldenSet.GoldenCase;
import com.tesda.ciicc.banking.backend.auth.CurrentUser;
import com.tesda.ciicc.banking.backend.model.Account;
import com.tesda.ciicc.banking.backend.model.AccountOwner;
import com.tesda.ciicc.banking.backend.model.Role;
import com.tesda.ciicc.banking.backend.model.Transaction;
import com.tesda.ciicc.banking.backend.model.TransactionCategory;
import com.tesda.ciicc.banking.backend.model.TransactionType;
import com.tesda.ciicc.banking.backend.repository.AccountOwnerRepository;
import com.tesda.ciicc.banking.backend.repository.AccountRepository;
import com.tesda.ciicc.banking.backend.repository.AnomalyFlagRepository;
import com.tesda.ciicc.banking.backend.repository.TransactionRepository;
import com.tesda.ciicc.banking.backend.service.AssistantService;
import com.tesda.ciicc.banking.backend.service.TransferProposal;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Phase 4 AI eval. Every case in {@code assistant-golden.json} is replayed
 * through the real {@link AssistantService}, real JWT-scoped
 * {@code AssistantTools}, and a real H2 database, with only the language model
 * replaced by a scripted client that emits the case's expected tool calls.
 *
 * <p>This is the CI-safe half of the evaluation: it grades the contract around
 * the model — that every question has a valid tool call, that the tools return
 * the expected facts for the signed-in caller, that model-supplied user ids
 * are ignored, and that a proposed transfer never changes a balance or writes
 * a transaction. Running the same golden file against a live model is a
 * separate, key-gated exercise; the questions and expected calls are
 * provider-neutral.</p>
 */
@SpringBootTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:assistanteval;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false",
        "JWT_SECRET=assistant-eval-secret-0123456789abcdef",
        "ADMIN_PASSWORD=assistant-eval-admin"
})
class AssistantGoldenEvalTest {

    private static final CurrentUser CALLER = new CurrentUser(4242, "golden-user", Role.CUSTOMER);
    private static final int OTHER_USER = 9999;

    private static final String CHECKING = "200001";
    private static final String SAVINGS = "200002";
    private static final String FOREIGN = "200009";

    @Autowired
    private AssistantService assistant;

    @Autowired
    private AccountRepository accounts;

    @Autowired
    private AccountOwnerRepository accountOwners;

    @Autowired
    private TransactionRepository transactions;

    @Autowired
    private AnomalyFlagRepository anomalyFlags;

    @Autowired
    private JdbcTemplate jdbc;

    @MockitoBean
    private AssistantModelClient model;

    private final ObjectMapper json = new ObjectMapper();
    private AssistantGoldenSet.GoldenSet golden;

    @BeforeAll
    void seedDatabase() {
        golden = AssistantGoldenSet.load();

        anomalyFlags.deleteAll();
        transactions.deleteAll();
        accountOwners.deleteAll();
        accounts.deleteAll();

        accounts.save(new Account(CHECKING, "Everyday Checking", new BigDecimal("2500.00")));
        accounts.save(new Account(SAVINGS, "Savings", new BigDecimal("10000.00")));
        accounts.save(new Account(FOREIGN, "Foreign Account", new BigDecimal("777.00")));
        accountOwners.save(new AccountOwner(CALLER.id(), CHECKING));
        accountOwners.save(new AccountOwner(CALLER.id(), SAVINGS));
        accountOwners.save(new AccountOwner(OTHER_USER, FOREIGN));

        seedTransaction("TXN-EVAL-T1", CHECKING, TransactionType.DEPOSIT,
                "5000.00", "5000.00", TransactionCategory.SALARY, null, "2026-10-01T09:00:00Z");
        seedTransaction("TXN-EVAL-T2", CHECKING, TransactionType.WITHDRAW,
                "120.00", "4880.00", TransactionCategory.GROCERIES, null, "2026-10-03T12:00:00Z");
        seedTransaction("TXN-EVAL-T3", CHECKING, TransactionType.WITHDRAW,
                "80.00", "4800.00", TransactionCategory.DINING, null, "2026-10-05T18:30:00Z");
        seedTransaction("TXN-EVAL-T4", CHECKING, TransactionType.TRANSFER_OUT,
                "300.00", "4500.00", null, SAVINGS, "2026-10-10T08:00:00Z");
        seedTransaction("TXN-EVAL-T5", CHECKING, TransactionType.WITHDRAW,
                "2000.00", "2500.00", null, null, "2026-10-12T15:00:00Z");
        seedTransaction("TXN-EVAL-T6", SAVINGS, TransactionType.DEPOSIT,
                "10000.00", "10000.00", TransactionCategory.SALARY, null, "2026-09-20T10:00:00Z");
        seedTransaction("TXN-EVAL-T7", FOREIGN, TransactionType.DEPOSIT,
                "777.00", "777.00", null, null, "2026-10-02T10:00:00Z");
    }

    Stream<Arguments> goldenCases() {
        return golden.cases().stream().map(testCase -> Arguments.of(testCase.id(), testCase));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("goldenCases")
    void replaysEachGoldenQuestion(String id, GoldenCase testCase) throws Exception {
        AtomicInteger modelCalls = new AtomicInteger();
        List<String> toolResults = new CopyOnWriteArrayList<>();

        when(model.complete(anyString(), anyList(), anyList())).thenAnswer(invocation -> {
            List<AssistantMessage> conversation = invocation.getArgument(1);

            if (modelCalls.getAndIncrement() == 0) {
                if (testCase.expectedToolCalls().isEmpty()) {
                    return new AssistantTurn(List.of(
                            new AssistantText("I can help you understand your own accounts.")));
                }
                List<AssistantBlock> blocks = new ArrayList<>();
                int index = 1;
                for (ExpectedCall call : testCase.expectedToolCalls()) {
                    ObjectNode input = json.valueToTree(call.arguments());
                    if (testCase.modelAddsUserId()) {
                        input.put("userId", OTHER_USER);
                    }
                    blocks.add(new AssistantToolUse("toolu_eval_" + index++, call.tool(), input));
                }
                return new AssistantTurn(blocks);
            }

            AssistantMessage last = conversation.get(conversation.size() - 1);
            for (AssistantBlock block : last.content()) {
                if (block instanceof AssistantToolResult result) {
                    toolResults.add(result.content());
                }
            }
            return new AssistantTurn(List.of(new AssistantText("Here is what I found.")));
        });

        BigDecimal checkingBefore = balanceOf(CHECKING);
        BigDecimal savingsBefore = balanceOf(SAVINGS);
        long transactionCountBefore = transactions.count();

        AssistantService.AssistantReply reply = assistant.chat(CALLER, testCase.question());

        assertExecutedCalls(id, testCase, reply);

        String combinedResults = String.join("\n", toolResults);
        for (String expected : testCase.expectedResultContains()) {
            assertTrue(combinedResults.contains(expected),
                    id + ": tool results did not contain [" + expected + "]; were: " + combinedResults);
        }

        assertEquals(testCase.expectedProposals().size(), reply.transferProposals().size(),
                id + ": wrong number of transfer proposals");
        for (int i = 0; i < testCase.expectedProposals().size(); i++) {
            ExpectedProposal expected = testCase.expectedProposals().get(i);
            TransferProposal actual = reply.transferProposals().get(i);
            assertEquals(expected.sourceAccountNumber(), actual.sourceAccountNumber(), id);
            assertEquals(expected.destinationAccountNumber(), actual.destinationAccountNumber(), id);
            assertEquals(expected.amount(), actual.amount(), id);
            assertEquals("PROPOSED", actual.status(), id);
            assertNotNull(actual.note(), id);
        }

        assertFalse(reply.reply().isBlank(), id + ": empty reply");

        // The assistant is read-only: no case, including every proposal case,
        // may change a balance or write a transaction.
        assertBalanceUnchanged(id, CHECKING, checkingBefore);
        assertBalanceUnchanged(id, SAVINGS, savingsBefore);
        assertEquals(transactionCountBefore, transactions.count(),
                id + ": the assistant wrote a transaction");
    }

    private static void assertExecutedCalls(String id, GoldenCase testCase,
                                            AssistantService.AssistantReply reply) {
        List<AssistantService.ToolCall> actualCalls = reply.toolCalls();
        assertEquals(testCase.expectedToolCalls().size(), actualCalls.size(),
                id + ": wrong number of tool calls");
        for (int i = 0; i < testCase.expectedToolCalls().size(); i++) {
            ExpectedCall expected = testCase.expectedToolCalls().get(i);
            AssistantService.ToolCall actual = actualCalls.get(i);
            assertEquals(expected.tool(), actual.tool(), id + ": wrong tool at position " + i);
            assertEquals(expected.arguments(), actual.arguments(),
                    id + ": wrong canonical arguments at position " + i);
            assertTrue(actual.arguments().keySet().stream()
                            .noneMatch(key -> key.toLowerCase(Locale.ROOT).contains("user")),
                    id + ": a model-supplied identity argument reached the tool");
        }
    }

    private void assertBalanceUnchanged(String id, String accountNumber, BigDecimal before) {
        assertEquals(0, before.compareTo(balanceOf(accountNumber)),
                id + ": the assistant changed the balance of " + accountNumber);
    }

    private BigDecimal balanceOf(String accountNumber) {
        return accounts.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new IllegalStateException("Missing seeded account " + accountNumber))
                .getBalance();
    }

    private void seedTransaction(String reference, String accountNumber, TransactionType type,
                                 String amount, String balanceAfter, TransactionCategory category,
                                 String referenceAccount, String createdAt) {
        Transaction transaction = new Transaction();
        transaction.setTransactionReference(reference);
        transaction.setAccountNumber(accountNumber);
        transaction.setTransactionType(type);
        transaction.setAmount(new BigDecimal(amount));
        transaction.setBalance(new BigDecimal(balanceAfter));
        transaction.setCategory(category);
        transaction.setReferenceAccount(referenceAccount);
        transaction = transactions.save(transaction);

        // @CreationTimestamp would use "now"; the eval needs fixed dates so
        // date-filter cases mean the same thing on every run.
        jdbc.update("update transactions set created_at = ? where id = ?",
                Timestamp.from(Instant.parse(createdAt)), transaction.getId());
    }
}
