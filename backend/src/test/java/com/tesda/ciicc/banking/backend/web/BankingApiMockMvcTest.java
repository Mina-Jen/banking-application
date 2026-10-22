package com.tesda.ciicc.banking.backend.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tesda.ciicc.banking.backend.exception.AccountAlreadyExistsException;
import com.tesda.ciicc.banking.backend.exception.AccountNotFoundException;
import com.tesda.ciicc.banking.backend.exception.BankingValidationException;
import com.tesda.ciicc.banking.backend.exception.InsufficientFundsException;
import com.tesda.ciicc.banking.backend.exception.InvalidAmountException;
import com.tesda.ciicc.banking.backend.model.Account;
import com.tesda.ciicc.banking.backend.model.Transaction;
import com.tesda.ciicc.banking.backend.model.TransactionType;
import com.tesda.ciicc.banking.backend.service.BankingService;
import com.tesda.ciicc.banking.backend.web.dto.AmountRequest;
import com.tesda.ciicc.banking.backend.web.dto.CreateAccountRequest;
import com.tesda.ciicc.banking.backend.web.dto.TransactionResponse;
import com.tesda.ciicc.banking.backend.web.dto.TransferRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Web-layer tests: the controllers, DTO mapping and error advice run for real,
 * while {@link BankingService} and {@link IdempotencyService} are mocked. The
 * idempotency claim/replay rules themselves are covered in
 * {@link IdempotencyServiceTest} and the database-backed behaviour in the
 * integration tests.
 */
@WebMvcTest(controllers = {AccountController.class, TransferController.class})
class BankingApiMockMvcTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private BankingService banking;

    @MockitoBean
    private IdempotencyService idempotency;

    private static Account account(String accountNumber, String balance) {
        return new Account(1, accountNumber, "Juan Dela Cruz", new BigDecimal(balance),
                Timestamp.valueOf("2026-10-21 17:00:00"));
    }

    private static Transaction depositTransaction() {
        Transaction transaction = new Transaction();
        transaction.setId(7);
        transaction.setTransactionReference("TXN-abc123");
        transaction.setAccountNumber("100001");
        transaction.setTransactionType(TransactionType.DEPOSIT);
        transaction.setAmount(new BigDecimal("10.00"));
        transaction.setBalance(new BigDecimal("5010.00"));
        transaction.setCreatedAt(Timestamp.valueOf("2026-10-21 17:05:00"));
        return transaction;
    }

    // =====================================================================
    //  Create account
    // =====================================================================

    @Test
    void createAccountReturns201WithLocationAndBody() throws Exception {
        when(banking.createAccount(eq("100001"), eq("Juan Dela Cruz"), eq(new BigDecimal("5000.00"))))
                .thenReturn(account("100001", "5000.00"));

        mockMvc.perform(post("/api/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JSON.writeValueAsString(
                                new CreateAccountRequest("100001", "Juan Dela Cruz", "5000.00"))))
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.LOCATION, "/api/accounts/100001"))
                .andExpect(jsonPath("$.accountNumber").value("100001"))
                .andExpect(jsonPath("$.balance").value("5000.00"));
    }

    @Test
    void duplicateAccountNumberIs409() throws Exception {
        when(banking.createAccount(anyString(), anyString(), any()))
                .thenThrow(new AccountAlreadyExistsException("Account number 100001 already exists."));

        mockMvc.perform(post("/api/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JSON.writeValueAsString(
                                new CreateAccountRequest("100001", "Juan Dela Cruz", "0"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value("Account number 100001 already exists."));
    }

    @Test
    void invalidInitialDepositIs422() throws Exception {
        when(banking.createAccount(anyString(), anyString(), any()))
                .thenThrow(new InvalidAmountException("Initial deposit must be zero or greater."));

        mockMvc.perform(post("/api/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JSON.writeValueAsString(
                                new CreateAccountRequest("100001", "Juan Dela Cruz", "-5"))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value("Initial deposit must be zero or greater."));
    }

    // =====================================================================
    //  Get account
    // =====================================================================

    @Test
    void getAccountReturnsTheAccount() throws Exception {
        when(banking.getAccount("100001")).thenReturn(account("100001", "5000.00"));

        mockMvc.perform(get("/api/accounts/100001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountNumber").value("100001"))
                .andExpect(jsonPath("$.accountName").value("Juan Dela Cruz"));
    }

    @Test
    void getUnknownAccountIs404() throws Exception {
        when(banking.getAccount("999999"))
                .thenThrow(new AccountNotFoundException("Account 999999 does not exist."));

        mockMvc.perform(get("/api/accounts/999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Account 999999 does not exist."));
    }

    @Test
    void overlongAccountNumberIs422() throws Exception {
        when(banking.getAccount("123456789012345678901"))
                .thenThrow(new BankingValidationException("Account number must be at most 20 characters."));

        mockMvc.perform(get("/api/accounts/123456789012345678901"))
                .andExpect(status().isUnprocessableEntity());
    }

    // =====================================================================
    //  Deposit / withdraw (Idempotency-Key header)
    // =====================================================================

    @Test
    void depositWithoutIdempotencyKeyIs400() throws Exception {
        mockMvc.perform(post("/api/accounts/100001/deposits")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JSON.writeValueAsString(new AmountRequest("10.00"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Idempotency-Key")));

        verify(idempotency, never()).execute(any(), any(), any(), any(), any(), any());
    }

    @Test
    void depositWithBlankIdempotencyKeyIs400() throws Exception {
        doThrow(new IdempotencyKeyException("Idempotency-Key header must be 1 to 100 characters."))
                .when(idempotency).requireValidKey(" ");

        mockMvc.perform(post("/api/accounts/100001/deposits")
                        .header(IdempotencyService.HEADER_NAME, " ")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JSON.writeValueAsString(new AmountRequest("10.00"))))
                .andExpect(status().isBadRequest());

        verify(idempotency, never()).execute(any(), any(), any(), any(), any(), any());
    }

    @Test
    void depositReturnsTheRecordedTransaction() throws Exception {
        when(idempotency.execute(eq("key-1"), eq("DEPOSIT"), eq("100001"), any(AmountRequest.class),
                eq(TransactionResponse.class), any()))
                .thenReturn(new TransactionResponse(7, "TXN-abc123", "100001", "DEPOSIT",
                        "10.00", "5010.00", null, "2026-10-21T09:05:00Z"));

        mockMvc.perform(post("/api/accounts/100001/deposits")
                        .header(IdempotencyService.HEADER_NAME, "key-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JSON.writeValueAsString(new AmountRequest("10.00"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("DEPOSIT"))
                .andExpect(jsonPath("$.amount").value("10.00"))
                .andExpect(jsonPath("$.balanceAfter").value("5010.00"))
                .andExpect(jsonPath("$.referenceAccount").doesNotExist());
    }

    @Test
    void rejectedDepositIs422() throws Exception {
        when(idempotency.execute(any(), any(), any(), any(), any(), any()))
                .thenThrow(new InvalidAmountException("Deposit amount must be greater than zero."));

        mockMvc.perform(post("/api/accounts/100001/deposits")
                        .header(IdempotencyService.HEADER_NAME, "key-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JSON.writeValueAsString(new AmountRequest("0"))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value("Deposit amount must be greater than zero."));
    }

    @Test
    void nonNumericAmountIs422WithoutTouchingTheKey() throws Exception {
        // Money.parse runs in the controller, before the idempotent execution:
        // a malformed amount must not consume or conflict with the key.
        mockMvc.perform(post("/api/accounts/100001/deposits")
                        .header(IdempotencyService.HEADER_NAME, "key-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JSON.writeValueAsString(new AmountRequest("abc"))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value("Deposit amount must be a number."));

        verify(idempotency, never()).execute(any(), any(), any(), any(), any(), any());
    }

    @Test
    void usedIdempotencyKeyIs409() throws Exception {
        when(idempotency.execute(any(), any(), any(), any(), any(), any()))
                .thenThrow(new IdempotencyKeyConflictException(
                        "Idempotency key key-1 was already used with a different request."));

        mockMvc.perform(post("/api/accounts/100001/deposits")
                        .header(IdempotencyService.HEADER_NAME, "key-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JSON.writeValueAsString(new AmountRequest("20.00"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("different request")));
    }

    @Test
    void withdrawWithInsufficientFundsIs422() throws Exception {
        when(idempotency.execute(any(), any(), any(), any(), any(), any()))
                .thenThrow(new InsufficientFundsException(
                        "Amount 100.00 exceeds the available balance of 50.00."));

        mockMvc.perform(post("/api/accounts/100001/withdrawals")
                        .header(IdempotencyService.HEADER_NAME, "key-3")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JSON.writeValueAsString(new AmountRequest("100.00"))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message", containsString("exceeds the available balance")));
    }

    // =====================================================================
    //  Transfer
    // =====================================================================

    @Test
    void transferReturnsTheSourceTransferOut() throws Exception {
        when(idempotency.execute(eq("key-2"), eq("TRANSFER"), eq(""), any(TransferRequest.class),
                eq(TransactionResponse.class), any()))
                .thenReturn(new TransactionResponse(8, "TXN-def456", "100001", "TRANSFER_OUT",
                        "2000.00", "3500.00", "100002", "2026-10-21T09:06:00Z"));

        mockMvc.perform(post("/api/transfers")
                        .header(IdempotencyService.HEADER_NAME, "key-2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JSON.writeValueAsString(new TransferRequest("100001", "100002", "2000.00"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("TRANSFER_OUT"))
                .andExpect(jsonPath("$.accountNumber").value("100001"))
                .andExpect(jsonPath("$.referenceAccount").value("100002"));
    }

    @Test
    void transferBetweenTheSameAccountIs422() throws Exception {
        when(idempotency.execute(any(), any(), any(), any(), any(), any()))
                .thenThrow(new BankingValidationException(
                        "Source and destination accounts must be different."));

        mockMvc.perform(post("/api/transfers")
                        .header(IdempotencyService.HEADER_NAME, "key-2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JSON.writeValueAsString(new TransferRequest("100001", "100001", "2000.00"))))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void transferFromUnknownAccountIs404() throws Exception {
        when(idempotency.execute(any(), any(), any(), any(), any(), any()))
                .thenThrow(new AccountNotFoundException("Account 999999 does not exist."));

        mockMvc.perform(post("/api/transfers")
                        .header(IdempotencyService.HEADER_NAME, "key-2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JSON.writeValueAsString(new TransferRequest("999999", "100002", "2000.00"))))
                .andExpect(status().isNotFound());
    }

    // =====================================================================
    //  Transaction history
    // =====================================================================

    @Test
    void transactionHistoryReturnsThePageWithDefaults() throws Exception {
        Timestamp createdAt = Timestamp.valueOf("2026-10-21 17:05:00");
        Transaction transaction = depositTransaction();
        transaction.setCreatedAt(createdAt);
        // PageImpl clamps the given total when it fits inside this page's
        // window (offset 0 + size 20), so use a total beyond the page size.
        Page<Transaction> result = new PageImpl<>(List.of(transaction), PageRequest.of(0, 20), 23);
        when(banking.getTransactionHistoryPage("100001", 0, 20)).thenReturn(result);

        mockMvc.perform(get("/api/accounts/100001/transactions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(23))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.transactions[0].reference").value("TXN-abc123"))
                .andExpect(jsonPath("$.transactions[0].type").value("DEPOSIT"))
                .andExpect(jsonPath("$.transactions[0].createdAt").value(createdAt.toInstant().toString()));
    }

    @Test
    void transactionHistoryPassesPageAndSizeParameters() throws Exception {
        when(banking.getTransactionHistoryPage("100001", 1, 5)).thenReturn(Page.empty());

        mockMvc.perform(get("/api/accounts/100001/transactions?page=1&size=5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transactions").isEmpty());

        verify(banking).getTransactionHistoryPage("100001", 1, 5);
    }

    @Test
    void invalidPageSizeIs422() throws Exception {
        when(banking.getTransactionHistoryPage("100001", 0, 0))
                .thenThrow(new BankingValidationException("Page size must be between 1 and 100."));

        mockMvc.perform(get("/api/accounts/100001/transactions?size=0"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value("Page size must be between 1 and 100."));
    }

    @Test
    void nonNumericPageIs400() throws Exception {
        mockMvc.perform(get("/api/accounts/100001/transactions?page=abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Parameter 'page' has an invalid value."));
    }

    @Test
    void historyOfUnknownAccountIs404() throws Exception {
        when(banking.getTransactionHistoryPage(anyString(), anyInt(), anyInt()))
                .thenThrow(new AccountNotFoundException("Account 999999 does not exist."));

        mockMvc.perform(get("/api/accounts/999999/transactions"))
                .andExpect(status().isNotFound());
    }

    // =====================================================================
    //  Malformed requests
    // =====================================================================

    @Test
    void malformedJsonBodyIs400() throws Exception {
        mockMvc.perform(post("/api/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Request body is not valid JSON."));
    }
}
