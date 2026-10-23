package com.tesda.ciicc.banking.backend;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tesda.ciicc.banking.backend.repository.AccountOwnerRepository;
import com.tesda.ciicc.banking.backend.repository.AccountRepository;
import com.tesda.ciicc.banking.backend.repository.AnomalyFlagRepository;
import com.tesda.ciicc.banking.backend.repository.TransactionRepository;
import com.tesda.ciicc.banking.backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Reconciliation over the real stack (security chain, service, JPA, H2). The
 * ledger is first built by ordinary money movements through the API, so a
 * passing check proves the signed sums agree with what the movements stored —
 * not merely that two queries agree with each other. The second half then
 * breaks a balance behind the ledger's back and proves the check reports it
 * without repairing anything.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:reconciliationflow;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false",
        "JWT_SECRET=reconciliation-integration-secret-0123456789abcdef",
        "ADMIN_PASSWORD=reconciliation-admin-password"
})
class ReconciliationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TransactionRepository transactions;

    @Autowired
    private AccountOwnerRepository accountOwners;

    @Autowired
    private AccountRepository accounts;

    @Autowired
    private UserRepository users;

    @Autowired
    private AnomalyFlagRepository anomalyFlags;

    private final ObjectMapper json = new ObjectMapper();

    /** The report covers every account in the bank, so each test starts from an empty one. */
    @BeforeEach
    void emptyBankExceptTheBootstrapAdmin() {
        anomalyFlags.deleteAllInBatch();
        transactions.deleteAllInBatch();
        accountOwners.deleteAllInBatch();
        accounts.deleteAllInBatch();
        users.findAll().stream()
                .filter(user -> !user.getUsername().equals("admin"))
                .forEach(users::delete);
    }

    @Test
    void everyMovementLeavesTheLedgerAndTheBalancesInAgreement() throws Exception {
        String aliceToken = registerAndLogin("recon-alice");
        String bobToken = registerAndLogin("recon-bob");
        String carolToken = registerAndLogin("recon-carol");

        String alice = openAccount(aliceToken, "Alice Wallet", "1000.00");
        String bob = openAccount(bobToken, "Bob Reyes", "0.00");
        // An account with no opening deposit has no ledger rows at all.
        openAccount(carolToken, "Carol Cashless", "0.00");

        deposit(aliceToken, alice, "33.33", "recon-alice-deposit");
        withdraw(aliceToken, alice, "20.00", "recon-alice-withdraw");
        transfer(aliceToken, alice, bob, "30.00", "recon-alice-transfer");

        mockMvc.perform(get("/api/reconciliation").header("Authorization", bearer(adminToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reconciled").value(true))
                .andExpect(jsonPath("$.checkedAccounts").value(3))
                .andExpect(jsonPath("$.ledgerTotal").value("1013.33"))
                .andExpect(jsonPath("$.storedTotal").value("1013.33"))
                .andExpect(jsonPath("$.discrepancies").isEmpty())
                .andExpect(jsonPath("$.discrepanciesTruncated").value(false))
                .andExpect(jsonPath("$.checkedAt").isNotEmpty());

        // The numbers above are only meaningful if the movements really happened.
        assertEquals("983.33", balanceOf(alice));
        assertEquals("30.00", balanceOf(bob));

        mockMvc.perform(get("/api/reconciliation").header("Authorization", bearer(aliceToken)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message", containsString("ADMIN")));

        mockMvc.perform(get("/api/reconciliation"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void aBalanceTheLedgerDoesNotExplainIsReportedAndLeftAlone() throws Exception {
        String token = registerAndLogin("recon-victim");
        String account = openAccount(token, "Unexplained Balance", "100.00");
        deposit(token, account, "5.00", "recon-victim-deposit");

        // Simulate the bug the check exists to catch: a balance written without
        // a matching ledger row. Nothing in the API can do this.
        jdbcTemplate.update("UPDATE accounts SET balance = balance - 5.00 WHERE account_number = ?", account);

        mockMvc.perform(get("/api/reconciliation")
                        .header("Authorization", bearer(adminToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reconciled").value(false))
                .andExpect(jsonPath("$.ledgerTotal").value("105.00"))
                .andExpect(jsonPath("$.storedTotal").value("100.00"))
                .andExpect(jsonPath("$.discrepancies.length()").value(1))
                .andExpect(jsonPath("$.discrepancies[0].accountNumber").value(account))
                .andExpect(jsonPath("$.discrepancies[0].accountName").value("Unexplained Balance"))
                .andExpect(jsonPath("$.discrepancies[0].ledgerBalance").value("105.00"))
                .andExpect(jsonPath("$.discrepancies[0].storedBalance").value("100.00"))
                .andExpect(jsonPath("$.discrepancies[0].difference").value("5.00"));

        // Reconciling is a diagnosis, not a cure: the wrong balance is still wrong.
        assertEquals("100.00", balanceOf(account));
        assertEquals(2, transactionCount(account));
    }

    // =====================================================================
    //  Helpers
    // =====================================================================

    private String registerAndLogin(String username) throws Exception {
        String password = username + "-password-1";
        mockMvc.perform(post("/api/auth/register")
                        .contentType("application/json")
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isCreated());
        return accessToken(username, password);
    }

    private String accessToken(String username, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return json.readTree(result.getResponse().getContentAsString()).get("accessToken").asText();
    }

    private String adminToken() throws Exception {
        return accessToken("admin", "reconciliation-admin-password");
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

    private void deposit(String token, String account, String amount, String key) throws Exception {
        mockMvc.perform(post("/api/accounts/" + account + "/deposits")
                        .header("Authorization", bearer(token))
                        .header("Idempotency-Key", key)
                        .contentType("application/json")
                        .content("{\"amount\":\"" + amount + "\"}"))
                .andExpect(status().isOk());
    }

    private void withdraw(String token, String account, String amount, String key) throws Exception {
        mockMvc.perform(post("/api/accounts/" + account + "/withdrawals")
                        .header("Authorization", bearer(token))
                        .header("Idempotency-Key", key)
                        .contentType("application/json")
                        .content("{\"amount\":\"" + amount + "\"}"))
                .andExpect(status().isOk());
    }

    private void transfer(String token, String source, String destination, String amount, String key)
            throws Exception {
        mockMvc.perform(post("/api/transfers")
                        .header("Authorization", bearer(token))
                        .header("Idempotency-Key", key)
                        .contentType("application/json")
                        .content("{\"sourceAccountNumber\":\"" + source + "\",\"destinationAccountNumber\":\""
                                + destination + "\",\"amount\":\"" + amount + "\"}"))
                .andExpect(status().isOk());
    }

    private String balanceOf(String accountNumber) {
        return jdbcTemplate.queryForObject(
                "SELECT CAST(balance AS VARCHAR) FROM accounts WHERE account_number = ?",
                String.class, accountNumber);
    }

    private int transactionCount(String accountNumber) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM transactions WHERE account_number = ?", Integer.class, accountNumber);
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }
}
