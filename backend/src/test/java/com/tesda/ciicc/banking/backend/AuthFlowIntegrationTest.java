package com.tesda.ciicc.banking.backend;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The Phase 2 requirement, proved end to end against the real stack: real
 * security filter chain, real JWTs, real BCrypt checks, real rate limiter —
 * only the database is an in-memory H2 with a Hibernate-generated schema, so
 * the test runs on machines without Docker. The MySQL container tests cover
 * the same service rules against production-grade SQL.
 *
 * <p>Every test uses its own usernames: the rate limiter's counters live in
 * memory for the whole context, and the H2 data is not rolled back between
 * tests.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:authflow;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false",
        "JWT_SECRET=authflow-integration-test-secret-0123456789abcdef",
        "ADMIN_PASSWORD=integration-admin-password"
})
class AuthFlowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    /** Reads the audit trail straight from the table, the way an operator would. */
    @Autowired
    private JdbcTemplate jdbc;

    private final ObjectMapper json = new ObjectMapper();

    // =====================================================================
    //  THE Phase 2 requirement: user B cannot reach user A's account — and
    //  cannot tell it apart from an account that does not exist
    // =====================================================================

    @Test
    void bobGetsTheSame404OnAlicesAccountAndOnAnUnknownAccount() throws Exception {
        register("alice2", "alice-password-1");
        register("bob2", "bob-password-1");
        String aliceAccount = openAccount("alice2", "alice-password-1", "Alice", "100.00");
        String bobAccount = openAccount("bob2", "bob-password-1", "Bob", "500.00");
        // Nobody can hold this number: the bank only hands out eleven digits.
        String nobody = "200099";

        String aliceToken = accessToken("alice2", "alice-password-1");
        String bobToken = accessToken("bob2", "bob-password-1");

        // Bob can see his own account…
        mockMvc.perform(get("/api/accounts/" + bobAccount).header("Authorization", bearer(bobToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value("500.00"));

        // …but Alice's account answers exactly like a number nobody ever
        // opened: same status, byte-identical body, and no account number in it.
        MvcResult foreign = mockMvc.perform(get("/api/accounts/" + aliceAccount)
                        .header("Authorization", bearer(bobToken)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("Account not found."))
                .andReturn();
        MvcResult unknown = mockMvc.perform(get("/api/accounts/" + nobody)
                        .header("Authorization", bearer(bobToken)))
                .andExpect(status().isNotFound())
                .andReturn();

        String foreignBody = foreign.getResponse().getContentAsString();
        assertEquals(unknown.getResponse().getContentAsString(), foreignBody);
        assertFalse(foreignBody.contains(aliceAccount), "the body leaks the number: " + foreignBody);

        // Every way of touching a foreign account is blocked the same way.
        mockMvc.perform(post("/api/accounts/" + aliceAccount + "/deposits")
                        .header("Authorization", bearer(bobToken))
                        .header("Idempotency-Key", "bob-deposit-1")
                        .contentType("application/json")
                        .content("{\"amount\":\"10.00\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Account not found."));
        mockMvc.perform(post("/api/accounts/" + aliceAccount + "/withdrawals")
                        .header("Authorization", bearer(bobToken))
                        .header("Idempotency-Key", "bob-withdraw-1")
                        .contentType("application/json")
                        .content("{\"amount\":\"10.00\"}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/transfers")
                        .header("Authorization", bearer(bobToken))
                        .header("Idempotency-Key", "bob-transfer-1")
                        .contentType("application/json")
                        .content("{\"sourceAccountNumber\":\"" + aliceAccount
                                + "\",\"destinationAccountNumber\":\"" + bobAccount + "\","
                                + "\"amount\":\"10.00\"}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/accounts/" + aliceAccount + "/transactions")
                        .header("Authorization", bearer(bobToken)))
                .andExpect(status().isNotFound());

        // A destination that does not exist is a 404 too, still without naming it.
        mockMvc.perform(post("/api/transfers")
                        .header("Authorization", bearer(bobToken))
                        .header("Idempotency-Key", "bob-transfer-2")
                        .contentType("application/json")
                        .content("{\"sourceAccountNumber\":\"" + bobAccount
                                + "\",\"destinationAccountNumber\":\"" + nobody + "\","
                                + "\"amount\":\"10.00\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Account not found."));

        // The source ownership rule runs first: denied before any row is locked.
        mockMvc.perform(post("/api/transfers")
                        .header("Authorization", bearer(bobToken))
                        .header("Idempotency-Key", "bob-transfer-3")
                        .contentType("application/json")
                        .content("{\"sourceAccountNumber\":\"" + aliceAccount
                                + "\",\"destinationAccountNumber\":\"" + nobody + "\","
                                + "\"amount\":\"10.00\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Account not found."));

        // Alice's own token still opens her own account, and no money moved
        // through Bob's denied attempts.
        mockMvc.perform(get("/api/accounts/" + aliceAccount).header("Authorization", bearer(aliceToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value("100.00"));
        mockMvc.perform(get("/api/accounts/" + bobAccount).header("Authorization", bearer(aliceToken)))
                .andExpect(status().isNotFound());
    }

    /**
     * Running out of money is answered before a missing destination is, so an
     * unfundable transfer cannot be used to ask "does this account number
     * exist?". Proved over HTTP against the real transaction manager, because
     * the withdraw now happens before the 404 and must be rolled back by it.
     */
    @Test
    void aShortBalanceAnswersBeforeAMissingDestination() throws Exception {
        register("carol4", "carol-password-1");
        String funded = openAccount("carol4", "carol-password-1", "Carol", "100.00");
        String other = openAccount("carol4", "carol-password-1", "Carol Second", "10.00");
        String nobody = "230099";
        String token = accessToken("carol4", "carol-password-1");

        MvcResult toMissing = mockMvc.perform(post("/api/transfers")
                        .header("Authorization", bearer(token))
                        .header("Idempotency-Key", "carol-short-1")
                        .contentType("application/json")
                        .content("{\"sourceAccountNumber\":\"" + funded
                                + "\",\"destinationAccountNumber\":\"" + nobody + "\","
                                + "\"amount\":\"500.00\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andReturn();
        MvcResult toExisting = mockMvc.perform(post("/api/transfers")
                        .header("Authorization", bearer(token))
                        .header("Idempotency-Key", "carol-short-2")
                        .contentType("application/json")
                        .content("{\"sourceAccountNumber\":\"" + funded
                                + "\",\"destinationAccountNumber\":\"" + other + "\","
                                + "\"amount\":\"500.00\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andReturn();

        String missingBody = toMissing.getResponse().getContentAsString();
        assertEquals(toExisting.getResponse().getContentAsString(), missingBody);
        assertFalse(missingBody.contains(nobody), "the body leaks the number: " + missingBody);

        // Enough money this time, so the destination lookup is reached: still a
        // quiet 404.
        mockMvc.perform(post("/api/transfers")
                        .header("Authorization", bearer(token))
                        .header("Idempotency-Key", "carol-funds-1")
                        .contentType("application/json")
                        .content("{\"sourceAccountNumber\":\"" + funded
                                + "\",\"destinationAccountNumber\":\"" + nobody + "\","
                                + "\"amount\":\"100.00\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Account not found."));

        // The withdraw that preceded that 404 was rolled back with it.
        mockMvc.perform(get("/api/accounts/" + funded).header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value("100.00"));
    }

    @Test
    void adminStillSeesWhichAccountNumberIsMissing() throws Exception {
        register("wendy1", "wendy-password-1");
        String wendyAccount = openAccount("wendy1", "wendy-password-1", "Wendy", "10.00");

        // "admin" was created at startup by AdminBootstrap from ADMIN_PASSWORD.
        String adminToken = accessToken("admin", "integration-admin-password");

        mockMvc.perform(get("/api/accounts/" + wendyAccount).header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk());

        // Operators are not filtered by ownership, and they need the number in
        // the message when debugging; that detail stays hidden from customers.
        mockMvc.perform(get("/api/accounts/210099").header("Authorization", bearer(adminToken)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Account 210099 does not exist."));
    }

    // =====================================================================
    //  Listing accounts
    // =====================================================================

    @Test
    void customerListContainsOnlyTheirOwnAccounts() throws Exception {
        register("nina1", "nina-password-1");
        register("omar1", "omar-password-1");
        // The bank assigns the numbers, so the test cannot steer them: the
        // listing rule is compared as strings, whatever the three numbers are.
        String ninaAccountOne = openAccount("nina1", "nina-password-1", "Nina One", "20.00");
        String ninaAccountTwo = openAccount("nina1", "nina-password-1", "Nina Two", "10.00");
        String omarAccount = openAccount("omar1", "omar-password-1", "Omar", "30.00");

        String ninaToken = accessToken("nina1", "nina-password-1");

        MvcResult result = mockMvc.perform(get("/api/accounts").param("size", "100")
                        .header("Authorization", bearer(ninaToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accounts.length()").value(2))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.page").value(0))
                .andReturn();

        List<String> listed = accountNumbersIn(result.getResponse().getContentAsString());
        // Exactly Nina's two numbers, ordered by account number rather than by
        // the order they were opened in.
        assertEquals(List.of(ninaAccountOne, ninaAccountTwo).stream().sorted().toList(), listed);

        // Omar's account is not merely hidden behind a flag: it is not in the
        // response at all.
        assertFalse(result.getResponse().getContentAsString().contains(omarAccount),
                "another customer's account leaked");

        // ADMIN gets the unscoped list, which includes all three.
        String adminToken = accessToken("admin", "integration-admin-password");
        mockMvc.perform(get("/api/accounts").param("size", "100")
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accounts[?(@.accountNumber == '" + omarAccount + "')]").exists());

        mockMvc.perform(get("/api/accounts")).andExpect(status().isUnauthorized());
    }

    @Test
    void listingRejectsOversizedPagesBeforeReadingAnything() throws Exception {
        register("petra1", "petra-password-1");
        String token = accessToken("petra1", "petra-password-1");

        mockMvc.perform(get("/api/accounts").param("size", "101")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value("Page size must be between 1 and 100."));

        mockMvc.perform(get("/api/accounts").param("page", "-1")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isUnprocessableEntity());
    }

    // =====================================================================
    //  The bank, not the customer, chooses the account number
    // =====================================================================

    @Test
    void aCustomerCannotPickTheirOwnAccountNumber() throws Exception {
        register("rosa1", "rosa-password-1");
        String token = accessToken("rosa1", "rosa-password-1");

        // Even a body that still carries the old field gets a number of the
        // bank's own: the field is not part of the request any more.
        MvcResult created = mockMvc.perform(post("/api/accounts")
                        .header("Authorization", bearer(token))
                        .contentType("application/json")
                        .content("{\"accountNumber\":\"100001\",\"accountName\":\"Rosa\",\"initialDeposit\":\"0\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        String assigned = json.readTree(created.getResponse().getContentAsString())
                .get("accountNumber").asText();

        assertFalse("100001".equals(assigned), "the request body still decides the number");
        assertTrue(assigned.matches("[1-9]\\d{10}"), "not an assigned 11-digit number: " + assigned);
        // The number nobody was given does not exist, and Rosa cannot read it.
        mockMvc.perform(get("/api/accounts/100001").header("Authorization", bearer(token)))
                .andExpect(status().isNotFound());

        // Choosing a number is an operator action, so a customer is refused —
        // with a 403 about the role, never with anything about accounts.
        mockMvc.perform(post("/api/accounts/with-number")
                        .header("Authorization", bearer(token))
                        .contentType("application/json")
                        .content("{\"accountNumber\":\"100001\",\"accountName\":\"Rosa\",\"initialDeposit\":\"0\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    void anAdminPicksANumberAndHearsWhenItIsTaken() throws Exception {
        String adminToken = accessToken("admin", "integration-admin-password");

        mockMvc.perform(post("/api/accounts/with-number")
                        .header("Authorization", bearer(adminToken))
                        .contentType("application/json")
                        .content("{\"accountNumber\":\"123456\",\"accountName\":\"Branch Cash Box\","
                                + "\"initialDeposit\":\"100.00\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accountNumber").value("123456"))
                .andExpect(jsonPath("$.balance").value("100.00"));

        // The operator keeps the detailed conflict: they are keying a number
        // they were given, so "already exists" is the answer they need.
        mockMvc.perform(post("/api/accounts/with-number")
                        .header("Authorization", bearer(adminToken))
                        .contentType("application/json")
                        .content("{\"accountNumber\":\"123456\",\"accountName\":\"Someone Else\","
                                + "\"initialDeposit\":\"0\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Account number 123456 already exists."));

        // An operator account belongs to nobody, so it is not in any customer's
        // list — and a customer cannot read it either.
        register("tomas1", "tomas-password-1");
        mockMvc.perform(get("/api/accounts/123456")
                        .header("Authorization", bearer(accessToken("tomas1", "tomas-password-1"))))
                .andExpect(status().isNotFound());
    }

    // =====================================================================
    //  Token basics
    // =====================================================================

    @Test
    void requestsWithoutATokenAre401() throws Exception {
        register("dora1", "dora-password-1");
        String doraAccount = openAccount("dora1", "dora-password-1", "Dora", "10.00");

        mockMvc.perform(get("/api/accounts/" + doraAccount))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));

        // A string that is not a JWT at all is treated the same way.
        mockMvc.perform(get("/api/accounts/" + doraAccount).header("Authorization", "Bearer not-a-jwt"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void refreshExchangesForANewPairAndRefreshTokensCannotBeUsedAsBearer() throws Exception {
        register("fin1", "fin-password-12");
        String finAccount = openAccount("fin1", "fin-password-12", "Fin", "20.00");

        MvcResult login = mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content("{\"username\":\"fin1\",\"password\":\"fin-password-12\"}"))
                .andExpect(status().isOk())
                .andReturn();
        var tokens = json.readTree(login.getResponse().getContentAsString());

        MvcResult refresh = mockMvc.perform(post("/api/auth/refresh")
                        .contentType("application/json")
                        .content("{\"refreshToken\":\"" + tokens.get("refreshToken").asText() + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andReturn();
        String newAccessToken = json.readTree(refresh.getResponse().getContentAsString())
                .get("accessToken").asText();

        // The refreshed access token opens the account…
        mockMvc.perform(get("/api/accounts/" + finAccount).header("Authorization", bearer(newAccessToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value("20.00"));

        // …but a refresh token in the Authorization header does not authenticate…
        mockMvc.perform(get("/api/accounts/" + finAccount)
                        .header("Authorization", bearer(tokens.get("refreshToken").asText())))
                .andExpect(status().isUnauthorized());

        // …and an access token cannot be smuggled into the refresh endpoint.
        mockMvc.perform(post("/api/auth/refresh")
                        .contentType("application/json")
                        .content("{\"refreshToken\":\"" + tokens.get("accessToken").asText() + "\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message", containsString("access token")));
    }

    // =====================================================================
    //  Roles
    // =====================================================================

    @Test
    void adminMayAccessEveryCustomersAccount() throws Exception {
        register("elsa1", "elsa-password-1");
        String elsaAccount = openAccount("elsa1", "elsa-password-1", "Elsa", "75.50");

        // "admin" was created at startup by AdminBootstrap from ADMIN_PASSWORD.
        String adminToken = accessToken("admin", "integration-admin-password");

        mockMvc.perform(get("/api/accounts/" + elsaAccount).header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value("75.50"));
    }

    @Test
    void adminCanReadButNeverMoveACustomersMoney() throws Exception {
        register("nadia1", "nadia-password-1");
        String nadiaAccount = openAccount("nadia1", "nadia-password-1", "Nadia", "200.00");
        register("oscar1", "oscar-password-1");
        String oscarAccount = openAccount("oscar1", "oscar-password-1", "Oscar", "50.00");
        String adminToken = accessToken("admin", "integration-admin-password");

        mockMvc.perform(post("/api/accounts/" + nadiaAccount + "/deposits")
                        .header("Authorization", bearer(adminToken))
                        .header("Idempotency-Key", "admin-deposit-1")
                        .contentType("application/json")
                        .content("{\"amount\":\"10.00\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message", containsString("Administrators cannot deposit")));
        mockMvc.perform(post("/api/accounts/" + nadiaAccount + "/withdrawals")
                        .header("Authorization", bearer(adminToken))
                        .header("Idempotency-Key", "admin-withdraw-1")
                        .contentType("application/json")
                        .content("{\"amount\":\"10.00\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/transfers")
                        .header("Authorization", bearer(adminToken))
                        .header("Idempotency-Key", "admin-transfer-1")
                        .contentType("application/json")
                        .content("{\"sourceAccountNumber\":\"" + nadiaAccount
                                + "\",\"destinationAccountNumber\":\"" + oscarAccount + "\","
                                + "\"amount\":\"10.00\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/accounts/" + nadiaAccount).header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value("200.00"));
        mockMvc.perform(get("/api/accounts/" + oscarAccount).header("Authorization", bearer(adminToken)))
                .andExpect(jsonPath("$.balance").value("50.00"));
    }

    // =====================================================================
    //  Login rate limiting
    // =====================================================================

    @Test
    void sixthFailedLoginIsRateLimitedEvenWithTheCorrectPassword() throws Exception {
        register("gary1", "gary-password-1");

        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/api/auth/login")
                            .contentType("application/json")
                            .content("{\"username\":\"gary1\",\"password\":\"wrong-" + i + "\"}"))
                    .andExpect(status().isUnauthorized());
        }

        // Six attempts from the same username + IP within the window: the
        // limiter answers before the password is even checked.
        mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content("{\"username\":\"gary1\",\"password\":\"gary-password-1\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", org.hamcrest.Matchers.notNullValue()))
                .andExpect(jsonPath("$.status").value(429));
    }

    // =====================================================================
    //  Idempotency keys stay per user
    // =====================================================================

    @Test
    void crossUserIdempotencyKeyCollisionIsAConflictNeverAReplay() throws Exception {
        register("heidi2", "heidi-password-1");
        register("ivan2", "ivan-password-1");
        String heidiAccount = openAccount("heidi2", "heidi-password-1", "Heidi", "10.00");
        String ivanAccount = openAccount("ivan2", "ivan-password-1", "Ivan", "10.00");

        String heidiToken = accessToken("heidi2", "heidi-password-1");
        String ivanToken = accessToken("ivan2", "ivan-password-1");

        // Heidi deposits with key "shared-key"…
        mockMvc.perform(post("/api/accounts/" + heidiAccount + "/deposits")
                        .header("Authorization", bearer(heidiToken))
                        .header("Idempotency-Key", "shared-key")
                        .contentType("application/json")
                        .content("{\"amount\":\"5.00\"}"))
                .andExpect(status().isOk());

        // …Ivan sends the SAME key (identical body, but HIS account). The key
        // is globally unique, so this is a conflict — and because the request
        // hash includes the caller id, Ivan can never receive Heidi's stored
        // response here, whatever account he points the key at.
        mockMvc.perform(post("/api/accounts/" + ivanAccount + "/deposits")
                        .header("Authorization", bearer(ivanToken))
                        .header("Idempotency-Key", "shared-key")
                        .contentType("application/json")
                        .content("{\"amount\":\"5.00\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("different request")));

        mockMvc.perform(post("/api/accounts/" + heidiAccount + "/deposits")
                        .header("Authorization", bearer(ivanToken))
                        .header("Idempotency-Key", "shared-key")
                        .contentType("application/json")
                        .content("{\"amount\":\"5.00\"}"))
                .andExpect(status().isConflict());

        // A key of his own works fine — his account was never the problem.
        mockMvc.perform(post("/api/accounts/" + ivanAccount + "/deposits")
                        .header("Authorization", bearer(ivanToken))
                        .header("Idempotency-Key", "ivans-own-key")
                        .contentType("application/json")
                        .content("{\"amount\":\"5.00\"}"))
                .andExpect(status().isOk());
    }

    // =====================================================================
    //  Registration and error hygiene
    // =====================================================================

    @Test
    void registrationRules() throws Exception {
        register("judy1", "judy-password-1");

        // Same username twice → 409.
        mockMvc.perform(post("/api/auth/register")
                        .contentType("application/json")
                        .content("{\"username\":\"judy1\",\"password\":\"judy-password-1\"}"))
                .andExpect(status().isConflict());

        // Password too short → 422.
        mockMvc.perform(post("/api/auth/register")
                        .contentType("application/json")
                        .content("{\"username\":\"karl1\",\"password\":\"short\"}"))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void loginFailureDoesNotRevealWhetherTheUsernameExists() throws Exception {
        register("lena1", "lena-password-1");

        // Unknown username and existing username with a wrong password must
        // answer identically, so errors cannot enumerate users.
        mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content("{\"username\":\"no-such-user\",\"password\":\"whatever-123\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid username or password."));

        mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content("{\"username\":\"lena1\",\"password\":\"wrong-password\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid username or password."));
    }

    @Test
    void loginWithAnOverlongUsernameIsUnauthorizedAndStillAudited() throws Exception {
        // The attempted username is longer than audit_log.username (VARCHAR(50)).
        // The failure row is written BEFORE the client is answered, so an
        // overflow here would lose the audit entry and replace the 401 with a
        // 500 — the crash is in the logging path, not in the credentials check.
        String overlong = "a".repeat(60);

        MvcResult attempt = mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content("{\"username\":\"" + overlong + "\",\"password\":\"whatever-123\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"))
                .andExpect(jsonPath("$.message").value("Invalid username or password."))
                .andReturn();

        // Byte-identical to an unknown but well-formed username, so the length
        // boundary cannot be probed either.
        MvcResult ordinary = mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content("{\"username\":\"no-such-user-3\",\"password\":\"whatever-123\"}"))
                .andExpect(status().isUnauthorized())
                .andReturn();
        assertEquals(ordinary.getResponse().getContentAsString(), attempt.getResponse().getContentAsString());

        Integer audited = jdbc.queryForObject(
                "select count(*) from audit_log where username = ? and action = 'LOGIN_FAILURE'",
                Integer.class, "a".repeat(50));
        assertTrue(audited != null && audited > 0, "the over-long login attempt was not audited");
    }

    // =====================================================================
    //  Per-user request budget
    // =====================================================================

    @Test
    void theSixtyFirstAccountsCallInAMinuteIs429WithRetryAfter() throws Exception {
        register("quinn1", "quinn-password-1");
        String token = accessToken("quinn1", "quinn-password-1");

        for (int i = 0; i < 60; i++) {
            mockMvc.perform(get("/api/accounts").header("Authorization", bearer(token)))
                    .andExpect(status().isOk());
        }

        MvcResult limited = mockMvc.perform(get("/api/accounts").header("Authorization", bearer(token)))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.status").value(429))
                .andExpect(jsonPath("$.error").value("Too Many Requests"))
                .andExpect(jsonPath("$.message", containsString("Too many requests")))
                .andReturn();

        long retryAfter = Long.parseLong(limited.getResponse().getHeader("Retry-After"));
        assertTrue(retryAfter > 0 && retryAfter <= 60, "Retry-After was " + retryAfter);

        // The budget covers the whole subtree, not just the collection path.
        mockMvc.perform(get("/api/accounts/100001").header("Authorization", bearer(token)))
                .andExpect(status().isTooManyRequests());

        // The budget covers money movement, not just reads: a transfer from the
        // same user is refused before the body is even looked at.
        mockMvc.perform(post("/api/transfers")
                        .header("Authorization", bearer(token))
                        .header("Idempotency-Key", "quinn-transfer-1")
                        .contentType("application/json")
                        .content("{\"sourceAccountNumber\":\"100001\",\"destinationAccountNumber\":\"100002\","
                                + "\"amount\":\"1.00\"}"))
                .andExpect(status().isTooManyRequests());

        // Someone else still has their own minute.
        register("rita1", "rita-password-1");
        mockMvc.perform(get("/api/accounts")
                        .header("Authorization", bearer(accessToken("rita1", "rita-password-1"))))
                .andExpect(status().isOk());

        // Token refresh and login are not part of this budget, so a limited
        // client can still renew its session instead of being locked out.
        accessToken("quinn1", "quinn-password-1");
    }

    // =====================================================================
    //  Helpers
    // =====================================================================

    private void register(String username, String password) throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType("application/json")
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("CUSTOMER"));
    }

    /**
     * Logs in and opens an account for that customer. The number is not an
     * argument any more — the bank assigns it — so the test reads it back from
     * the response and uses that everywhere else.
     */
    private String openAccount(String username, String password, String accountName,
                               String initialDeposit) throws Exception {
        String token = accessToken(username, password);
        MvcResult created = mockMvc.perform(post("/api/accounts")
                        .header("Authorization", bearer(token))
                        .contentType("application/json")
                        .content("{\"accountName\":\"" + accountName
                                + "\",\"initialDeposit\":\"" + initialDeposit + "\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        return json.readTree(created.getResponse().getContentAsString())
                .get("accountNumber").asText();
    }

    /** The account numbers of a listing response, in the order they came back. */
    private List<String> accountNumbersIn(String pageBody) throws Exception {
        List<String> numbers = new ArrayList<>();
        json.readTree(pageBody).path("accounts")
                .forEach(account -> numbers.add(account.path("accountNumber").asText()));
        return numbers;
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
