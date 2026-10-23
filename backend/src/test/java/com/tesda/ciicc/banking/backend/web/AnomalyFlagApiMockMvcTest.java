package com.tesda.ciicc.banking.backend.web;

import com.tesda.ciicc.banking.backend.auth.JwtService;
import com.tesda.ciicc.banking.backend.auth.SecurityConfig;
import com.tesda.ciicc.banking.backend.exception.AnomalyFlagAlreadyReviewedException;
import com.tesda.ciicc.banking.backend.exception.AnomalyFlagNotFoundException;
import com.tesda.ciicc.banking.backend.exception.BankingValidationException;
import com.tesda.ciicc.banking.backend.model.AnomalyFlag;
import com.tesda.ciicc.banking.backend.model.AnomalyRule;
import com.tesda.ciicc.banking.backend.model.AnomalyStatus;
import com.tesda.ciicc.banking.backend.model.Role;
import com.tesda.ciicc.banking.backend.model.Transaction;
import com.tesda.ciicc.banking.backend.model.TransactionType;
import com.tesda.ciicc.banking.backend.service.AnomalyService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Web layer of the anomaly-flag review API: paths, response shapes and the
 * error mapping. The service is mocked here; its rules and role checks are
 * covered by AnomalyServiceTest and AnomalyIntegrationTest.
 */
@WebMvcTest(controllers = AnomalyFlagController.class, properties = "app.api.max-requests=1000")
@Import(SecurityConfig.class)
class AnomalyFlagApiMockMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AnomalyService anomalies;

    @MockitoBean
    private JwtService jwt;

    private static AnomalyFlag flag(AnomalyStatus status) {
        Transaction transaction = new Transaction();
        transaction.setId(42);
        transaction.setTransactionReference("TXN-anom01");
        transaction.setAccountNumber("100001");
        transaction.setTransactionType(TransactionType.WITHDRAW);
        transaction.setAmount(new BigDecimal("5000.00"));
        transaction.setBalance(new BigDecimal("5000.00"));

        AnomalyFlag flag = new AnomalyFlag(transaction, AnomalyRule.UNUSUAL_AMOUNT,
                "5000.00 is at least 3x the 100.00 average of the last 3 outbound transactions.");
        ReflectionTestUtils.setField(flag, "id", 12);
        flag.setCreatedAt(Timestamp.valueOf("2026-10-22 10:15:30"));
        if (status != AnomalyStatus.OPEN) {
            flag.resolve(status, "admin", Timestamp.valueOf("2026-10-22 11:00:00"));
        }
        return flag;
    }

    @Test
    void listingWithoutATokenIs401() throws Exception {
        mockMvc.perform(get("/api/anomaly-flags"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockCurrentUser(role = Role.ADMIN)
    void adminListsTheOpenQueue() throws Exception {
        when(anomalies.listFlags(any(), eq("OPEN"), eq(0), eq(20)))
                .thenReturn(new PageImpl<>(List.of(flag(AnomalyStatus.OPEN))));

        mockMvc.perform(get("/api/anomaly-flags").param("status", "OPEN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.flags[0].id").value(12))
                .andExpect(jsonPath("$.flags[0].transactionId").value(42))
                .andExpect(jsonPath("$.flags[0].transactionReference").value("TXN-anom01"))
                .andExpect(jsonPath("$.flags[0].accountNumber").value("100001"))
                .andExpect(jsonPath("$.flags[0].transactionType").value("WITHDRAW"))
                .andExpect(jsonPath("$.flags[0].amount").value("5000.00"))
                .andExpect(jsonPath("$.flags[0].rule").value("UNUSUAL_AMOUNT"))
                .andExpect(jsonPath("$.flags[0].status").value("OPEN"))
                .andExpect(jsonPath("$.flags[0].createdAt").isNotEmpty())
                .andExpect(jsonPath("$.flags[0].reviewedAt").doesNotExist())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    @WithMockCurrentUser(role = Role.ADMIN)
    void noStatusFilterMeansEveryState() throws Exception {
        when(anomalies.listFlags(any(), isNull(), eq(0), eq(20))).thenReturn(Page.empty());

        mockMvc.perform(get("/api/anomaly-flags"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.flags").isEmpty())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    @WithMockCurrentUser
    void aCustomerCannotListFlags() throws Exception {
        when(anomalies.listFlags(any(), any(), anyInt(), anyInt()))
                .thenThrow(new AccessDeniedException("Only an ADMIN may access anomaly flags."));

        mockMvc.perform(get("/api/anomaly-flags"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.message").value("Only an ADMIN may access anomaly flags."));
    }

    @Test
    @WithMockCurrentUser(role = Role.ADMIN)
    void anUnknownStatusFilterIs422() throws Exception {
        when(anomalies.listFlags(any(), eq("PIZZA"), eq(0), eq(20)))
                .thenThrow(new BankingValidationException("Status must be one of: OPEN, REVIEWED, DISMISSED."));

        mockMvc.perform(get("/api/anomaly-flags").param("status", "PIZZA"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message", containsString("Status must be one of")));
    }

    @Test
    @WithMockCurrentUser(role = Role.ADMIN, username = "admin")
    void anAdminClosesAFlag() throws Exception {
        when(anomalies.reviewFlag(any(), eq(12), eq("REVIEWED")))
                .thenReturn(flag(AnomalyStatus.REVIEWED));

        mockMvc.perform(put("/api/anomaly-flags/12")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"REVIEWED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REVIEWED"))
                .andExpect(jsonPath("$.reviewedBy").value("admin"))
                .andExpect(jsonPath("$.reviewedAt").isNotEmpty());
    }

    @Test
    @WithMockCurrentUser(role = Role.ADMIN)
    void anUnknownFlagIs404() throws Exception {
        when(anomalies.reviewFlag(any(), eq(99), eq("DISMISSED")))
                .thenThrow(new AnomalyFlagNotFoundException("Anomaly flag not found."));

        mockMvc.perform(put("/api/anomaly-flags/99")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"DISMISSED\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Anomaly flag not found."));
    }

    @Test
    @WithMockCurrentUser(role = Role.ADMIN)
    void reviewingAClosedFlagIs409() throws Exception {
        when(anomalies.reviewFlag(any(), eq(12), eq("REVIEWED")))
                .thenThrow(new AnomalyFlagAlreadyReviewedException(
                        "Anomaly flag 12 has already been reviewed."));

        mockMvc.perform(put("/api/anomaly-flags/12")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"REVIEWED\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Anomaly flag 12 has already been reviewed."));
    }

    @Test
    @WithMockCurrentUser(role = Role.ADMIN)
    void openIsNotAReviewDecision() throws Exception {
        when(anomalies.reviewFlag(any(), eq(12), eq("OPEN")))
                .thenThrow(new BankingValidationException(
                        "Review status must be one of: REVIEWED, DISMISSED."));

        mockMvc.perform(put("/api/anomaly-flags/12")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"OPEN\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message", containsString("REVIEWED, DISMISSED")));
    }

    @Test
    @WithMockCurrentUser(role = Role.ADMIN)
    void aNonNumericFlagIdIs400() throws Exception {
        mockMvc.perform(put("/api/anomaly-flags/abc")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"REVIEWED\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Parameter 'flagId' has an invalid value."));
    }
}
