package com.tesda.ciicc.banking.backend;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.hamcrest.Matchers.containsString;
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

    private final ObjectMapper json = new ObjectMapper();

    // =====================================================================
    //  THE Phase 2 requirement: user B gets 403 on user A's account
    // =====================================================================

    @Test
    void bobGets403OnAlicesAccount() throws Exception {
        register("alice2", "alice-password-1");
        register("bob2", "bob-password-1");
        createAccount("alice2", "alice-password-1", "200001", "Alice", "100.00");
        createAccount("bob2", "bob-password-1", "200002", "Bob", "500.00");

        String aliceToken = accessToken("alice2", "alice-password-1");
        String bobToken = accessToken("bob2", "bob-password-1");

        // Bob can see his own account…
        mockMvc.perform(get("/api/accounts/200002").header("Authorization", bearer(bobToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value("500.00"));

        // …but is locked out of Alice's account.
        mockMvc.perform(get("/api/accounts/200001").header("Authorization", bearer(bobToken)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));

        // Every way of touching a foreign account is blocked the same way.
        mockMvc.perform(post("/api/accounts/200001/deposits")
                        .header("Authorization", bearer(bobToken))
                        .header("Idempotency-Key", "bob-deposit-1")
                        .contentType("application/json")
                        .content("{\"amount\":\"10.00\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/accounts/200001/withdrawals")
                        .header("Authorization", bearer(bobToken))
                        .header("Idempotency-Key", "bob-withdraw-1")
                        .contentType("application/json")
                        .content("{\"amount\":\"10.00\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/transfers")
                        .header("Authorization", bearer(bobToken))
                        .header("Idempotency-Key", "bob-transfer-1")
                        .contentType("application/json")
                        .content("{\"sourceAccountNumber\":\"200001\",\"destinationAccountNumber\":\"200002\","
                                + "\"amount\":\"10.00\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/accounts/200001/transactions").header("Authorization", bearer(bobToken)))
                .andExpect(status().isForbidden());

        // Alice's own token still opens her own account, and no money moved
        // through Bob's denied attempts.
        mockMvc.perform(get("/api/accounts/200001").header("Authorization", bearer(aliceToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value("100.00"));
    }

    // =====================================================================
    //  Token basics
    // =====================================================================

    @Test
    void requestsWithoutATokenAre401() throws Exception {
        register("dora1", "dora-password-1");
        createAccount("dora1", "dora-password-1", "300001", "Dora", "10.00");

        mockMvc.perform(get("/api/accounts/300001"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));

        // A string that is not a JWT at all is treated the same way.
        mockMvc.perform(get("/api/accounts/300001").header("Authorization", "Bearer not-a-jwt"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void refreshExchangesForANewPairAndRefreshTokensCannotBeUsedAsBearer() throws Exception {
        register("fin1", "fin-password-12");
        createAccount("fin1", "fin-password-12", "500001", "Fin", "20.00");

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
        mockMvc.perform(get("/api/accounts/500001").header("Authorization", bearer(newAccessToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value("20.00"));

        // …but a refresh token in the Authorization header does not authenticate…
        mockMvc.perform(get("/api/accounts/500001")
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
        createAccount("elsa1", "elsa-password-1", "400001", "Elsa", "75.50");

        // "admin" was created at startup by AdminBootstrap from ADMIN_PASSWORD.
        String adminToken = accessToken("admin", "integration-admin-password");

        mockMvc.perform(get("/api/accounts/400001").header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value("75.50"));
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
        createAccount("heidi2", "heidi-password-1", "600001", "Heidi", "10.00");
        createAccount("ivan2", "ivan-password-1", "600002", "Ivan", "10.00");

        String heidiToken = accessToken("heidi2", "heidi-password-1");
        String ivanToken = accessToken("ivan2", "ivan-password-1");

        // Heidi deposits with key "shared-key"…
        mockMvc.perform(post("/api/accounts/600001/deposits")
                        .header("Authorization", bearer(heidiToken))
                        .header("Idempotency-Key", "shared-key")
                        .contentType("application/json")
                        .content("{\"amount\":\"5.00\"}"))
                .andExpect(status().isOk());

        // …Ivan sends the SAME key (identical body, but HIS account). The key
        // is globally unique, so this is a conflict — and because the request
        // hash includes the caller id, Ivan can never receive Heidi's stored
        // response here, whatever account he points the key at.
        mockMvc.perform(post("/api/accounts/600002/deposits")
                        .header("Authorization", bearer(ivanToken))
                        .header("Idempotency-Key", "shared-key")
                        .contentType("application/json")
                        .content("{\"amount\":\"5.00\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("different request")));

        mockMvc.perform(post("/api/accounts/600001/deposits")
                        .header("Authorization", bearer(ivanToken))
                        .header("Idempotency-Key", "shared-key")
                        .contentType("application/json")
                        .content("{\"amount\":\"5.00\"}"))
                .andExpect(status().isConflict());

        // A key of his own works fine — his account was never the problem.
        mockMvc.perform(post("/api/accounts/600002/deposits")
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

    /** Logs in and creates an account owned by that user. */
    private void createAccount(String username, String password, String accountNumber,
                               String accountName, String initialDeposit) throws Exception {
        String token = accessToken(username, password);
        mockMvc.perform(post("/api/accounts")
                        .header("Authorization", bearer(token))
                        .contentType("application/json")
                        .content("{\"accountNumber\":\"" + accountNumber + "\",\"accountName\":\"" + accountName
                                + "\",\"initialDeposit\":\"" + initialDeposit + "\"}"))
                .andExpect(status().isCreated());
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
