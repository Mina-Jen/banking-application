package com.tesda.ciicc.banking.backend.service;

import com.tesda.ciicc.banking.backend.ai.CategorySuggestionClient;
import com.tesda.ciicc.banking.backend.auth.CurrentUser;
import com.tesda.ciicc.banking.backend.model.Account;
import com.tesda.ciicc.banking.backend.model.Role;
import com.tesda.ciicc.banking.backend.model.Transaction;
import com.tesda.ciicc.banking.backend.model.TransactionType;
import com.tesda.ciicc.banking.backend.repository.AccountOwnerRepository;
import com.tesda.ciicc.banking.backend.repository.AccountRepository;
import com.tesda.ciicc.banking.backend.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Where anomaly evaluation is wired in: after the outbound movement is
 * recorded, in the same service call. Deposits and incoming transfers must
 * not trigger it.
 */
class BankingServiceAnomalyEvaluationTest {

    private static final CurrentUser CAROL = new CurrentUser(7, "carol", Role.CUSTOMER);

    private final AccountRepository accounts = mock(AccountRepository.class);
    private final TransactionRepository transactions = mock(TransactionRepository.class);
    private final AccountOwnerRepository accountOwners = mock(AccountOwnerRepository.class);
    private final AuditService audit = mock(AuditService.class);
    private final AnomalyService anomalies = mock(AnomalyService.class);

    private final BankingService service = new BankingService(accounts, transactions, accountOwners,
            audit, mock(CategorySuggestionClient.class), anomalies);

    @BeforeEach
    void savingReturnsTheSameTransaction() {
        when(transactions.save(any(Transaction.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private void carolOwnsTheAccount(String accountNumber) {
        when(accountOwners.existsByUserIdAndAccountNumber(CAROL.id(), accountNumber)).thenReturn(true);
    }

    @Test
    void withdrawalEvaluatesTheRecordedTransaction() throws Exception {
        carolOwnsTheAccount("100001");
        when(accounts.findByAccountNumberForUpdate("100001"))
                .thenReturn(Optional.of(new Account("100001", "Carol", new BigDecimal("1000.00"))));

        Transaction result = service.withdraw(CAROL, "100001", new BigDecimal("100.00"));

        assertThat(result.getTransactionType()).isEqualTo(TransactionType.WITHDRAW);
        verify(anomalies).evaluate(result);
    }

    @Test
    void depositDoesNotRunAnomalyRules() throws Exception {
        carolOwnsTheAccount("100001");
        when(accounts.findByAccountNumberForUpdate("100001"))
                .thenReturn(Optional.of(new Account("100001", "Carol", new BigDecimal("1000.00"))));

        service.deposit(CAROL, "100001", new BigDecimal("100.00"));

        verify(anomalies, never()).evaluate(any());
    }

    @Test
    void transferEvaluatesTheSourceOutboundEntry() throws Exception {
        carolOwnsTheAccount("100001");
        when(accounts.findByAccountNumberForUpdate("100001"))
                .thenReturn(Optional.of(new Account(1, "100001", "Carol", new BigDecimal("1000.00"), null)));
        when(accounts.findByAccountNumberForUpdate("100002"))
                .thenReturn(Optional.of(new Account(2, "100002", "Bob", new BigDecimal("500.00"), null)));

        Transaction result = service.transfer(CAROL, "100001", "100002", new BigDecimal("100.00"));

        assertThat(result.getTransactionType()).isEqualTo(TransactionType.TRANSFER_OUT);
        verify(anomalies).evaluate(result);
    }

    @Test
    void openingDepositDoesNotRunAnomalyRules() throws Exception {
        when(accounts.saveAndFlush(any(Account.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.createAccount(CAROL, "Carol", new BigDecimal("100.00"));

        verify(anomalies, never()).evaluate(any());
    }
}
