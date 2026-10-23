package com.tesda.ciicc.banking.backend;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tesda.ciicc.banking.backend.repository.AnomalyFlagRepository;
import org.junit.jupiter.api.BeforeEach;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Phase 4 anomaly flags end to end over the real stack (security chain,
 * service, rules, H2). Money movements themselves are the input: the test
 * makes ordinary withdrawals through the API and then reads the review queue
 * the way an ADMIN would.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:anomalyflow;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false",
        "JWT_SECRET=anomaly-integration-test-secret-0123456789abcdef",
        "ADMIN_PASSWORD=anomaly-admin-password"
})
class AnomalyIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AnomalyFlagRepository anomalyFlags;

    private final ObjectMapper json = new ObjectMapper();

    /** Flags are global; each test starts from an empty review queue. */
    @BeforeEach
    void clearFlags() {
        anomalyFlags.deleteAll();
    }

    @Test
    void aWithdrawalFarAboveTheRecentAverageIsFlaggedAndReviewable() throws Exception {
        register("anomaly-big", "anomaly-password-1");
        String token = accessToken("anomaly-big", "anomaly-password-1");
        String account = openAccount(token, "Anomaly Big", "10000.00");

        withdraw(token, account, "100.00", "anomaly-big-1");
        withdraw(token, account, "100.00", "anomaly-big-2");
        withdraw(token, account, "100.00", "anomaly-big-3");
        MvcResult big = withdraw(token, account, "5000.00", "anomaly-big-4");
        String bigReference = json.readTree(big.getResponse().getContentAsString())
                .get("reference").asText();

        String admin = adminToken();
        MvcResult queue = mockMvc.perform(get("/api/anomaly-flags")
                        .param("status", "OPEN")
                        .header("Authorization", bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.flags[0].rule").value("UNUSUAL_AMOUNT"))
                .andExpect(jsonPath("$.flags[0].transactionReference").value(bigReference))
                .andExpect(jsonPath("$.flags[0].accountNumber").value(account))
                .andExpect(jsonPath("$.flags[0].transactionType").value("WITHDRAW"))
                .andExpect(jsonPath("$.flags[0].amount").value("5000.00"))
                .andExpect(jsonPath("$.flags[0].status").value("OPEN"))
                .andExpect(jsonPath("$.flags[0].details", containsString("3x")))
                .andReturn();
        int flagId = json.readTree(queue.getResponse().getContentAsString())
                .path("flags").path(0).path("id").asInt();

        // The customer is not blocked and is not told: only an ADMIN sees flags.
        mockMvc.perform(get("/api/anomaly-flags")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/api/anomaly-flags/" + flagId)
                        .header("Authorization", bearer(admin))
                        .contentType("application/json")
                        .content("{\"status\":\"REVIEWED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REVIEWED"))
                .andExpect(jsonPath("$.reviewedBy").value("admin"))
                .andExpect(jsonPath("$.reviewedAt").isNotEmpty());

        mockMvc.perform(get("/api/anomaly-flags")
                        .param("status", "OPEN")
                        .header("Authorization", bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));

        mockMvc.perform(put("/api/anomaly-flags/" + flagId)
                        .header("Authorization", bearer(admin))
                        .contentType("application/json")
                        .content("{\"status\":\"DISMISSED\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void theSixthOutboundTransactionInsideTenMinutesIsFlagged() throws Exception {
        register("anomaly-fast", "anomaly-password-2");
        String token = accessToken("anomaly-fast", "anomaly-password-2");
        String account = openAccount(token, "Anomaly Fast", "10000.00");

        for (int i = 1; i <= 6; i++) {
            withdraw(token, account, "10.00", "anomaly-fast-" + i);
        }

        mockMvc.perform(get("/api/anomaly-flags")
                        .param("status", "OPEN")
                        .header("Authorization", bearer(adminToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.flags[0].rule").value("VELOCITY"))
                .andExpect(jsonPath("$.flags[0].amount").value("10.00"))
                .andExpect(jsonPath("$.flags[0].details", containsString("6 outbound transactions")));
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

    private MvcResult withdraw(String token, String account, String amount, String idempotencyKey)
            throws Exception {
        return mockMvc.perform(post("/api/accounts/" + account + "/withdrawals")
                        .header("Authorization", bearer(token))
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType("application/json")
                        .content("{\"amount\":\"" + amount + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
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
        return accessToken("admin", "anomaly-admin-password");
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }
}
