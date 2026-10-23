package com.tesda.ciicc.banking.backend.service;

import com.tesda.ciicc.banking.backend.auth.CurrentUser;
import com.tesda.ciicc.banking.backend.model.Role;
import com.tesda.ciicc.banking.backend.model.TransactionType;
import com.tesda.ciicc.banking.backend.repository.AccountRepository;
import com.tesda.ciicc.banking.backend.repository.TransactionRepository;
import com.tesda.ciicc.banking.backend.service.ReconciliationReport.Discrepancy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * The arithmetic of the integrity check, with the database replaced by stubs.
 * Which transaction types are money in and which are money out is the whole
 * rule, so those two lists are pinned here instead of trusted to a comment.
 */
class ReconciliationServiceTest {

    private static final CurrentUser ADMIN = new CurrentUser(0, "recon-admin", Role.ADMIN);
    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-10-22T10:15:30Z"), ZoneOffset.UTC);
    private static final List<TransactionType> CREDITS =
            List.of(TransactionType.DEPOSIT, TransactionType.TRANSFER_IN);
    private static final List<TransactionType> DEBITS =
            List.of(TransactionType.WITHDRAW, TransactionType.TRANSFER_OUT);

    private AccountRepository accounts;
    private TransactionRepository transactions;
    private ReconciliationService service;

    @BeforeEach
    void setUp() {
        accounts = mock(AccountRepository.class);
        transactions = mock(TransactionRepository.class);
        service = new ReconciliationService(accounts, transactions, FIXED_CLOCK);
    }

    @Test
    void anAccountWithNoLedgerRowsReconcilesAgainstAZeroBalance() {
        stored(new Stored("200001", "Carol Cashless", new BigDecimal("0.00")));
        ledger(List.of(), List.of());

        ReconciliationReport report = service.reconcile(ADMIN);

        assertTrue(report.reconciled());
        assertEquals(1, report.checkedAccounts());
        assertMoney("0.00", report.ledgerTotal());
        assertMoney("0.00", report.storedTotal());
        assertEquals(Instant.parse("2026-10-22T10:15:30Z"), report.checkedAt());
        verify(transactions).sumAmountByAccount(CREDITS);
        verify(transactions).sumAmountByAccount(DEBITS);
    }

    @Test
    void depositsAndTransfersInAreCreditsAndWithdrawalsAndTransfersOutAreDebits() {
        stored(new Stored("200001", "Alice Wallet", new BigDecimal("983.33")),
                new Stored("200002", "Bob Reyes", new BigDecimal("30.00")));
        // Alice opened 1000.00, deposited 33.33, withdrew 20.00 and sent 30.00.
        // Bob received the 30.00 Alice sent.
        ledger(List.of(new Row("200001", new BigDecimal("1033.33")),
                        new Row("200002", new BigDecimal("30.00"))),
                List.of(new Row("200001", new BigDecimal("50.00"))));

        ReconciliationReport report = service.reconcile(ADMIN);

        assertTrue(report.reconciled());
        assertEquals(0, report.discrepancies().size());
        assertMoney("1013.33", report.ledgerTotal());
        assertMoney("1013.33", report.storedTotal());
    }

    @Test
    void aLedgerBiggerThanTheStoredBalanceIsAPositiveDifference() {
        stored(new Stored("200001", "Alice Wallet", new BigDecimal("145.00")));
        ledger(List.of(new Row("200001", new BigDecimal("200.00"))),
                List.of(new Row("200001", new BigDecimal("50.00"))));

        ReconciliationReport report = service.reconcile(ADMIN);

        assertFalse(report.reconciled());
        assertEquals(1, report.discrepancies().size());
        Discrepancy only = report.discrepancies().get(0);
        assertEquals("200001", only.accountNumber());
        assertEquals("Alice Wallet", only.accountName());
        assertMoney("150.00", only.ledgerBalance());
        assertMoney("145.00", only.storedBalance());
        assertMoney("5.00", only.difference());
        assertMoney("150.00", report.ledgerTotal());
        assertMoney("145.00", report.storedTotal());
    }

    @Test
    void balanceWithoutLedgerRowsIsANegativeDifference() {
        // The money is on the account but nothing in the ledger explains it.
        stored(new Stored("200001", "Alice Wallet", new BigDecimal("100.00")));
        ledger(List.of(), List.of());

        ReconciliationReport report = service.reconcile(ADMIN);

        assertFalse(report.reconciled());
        assertMoney("-100.00", report.discrepancies().get(0).difference());
    }

    @Test
    void listsAtMostOneHundredDiscrepanciesAndStillCountsTheRest() {
        List<Stored> every = new ArrayList<>();
        List<Row> credits = new ArrayList<>();
        for (int i = 0; i < 102; i++) {
            String number = String.format("2000%02d", i);
            every.add(new Stored(number, "Account " + i, BigDecimal.ZERO));
            credits.add(new Row(number, new BigDecimal("1.00")));
        }
        stored(every.toArray(Stored[]::new));
        ledger(credits, List.of());

        ReconciliationReport report = service.reconcile(ADMIN);

        assertFalse(report.reconciled());
        assertEquals(102, report.checkedAccounts());
        assertTrue(report.discrepanciesTruncated());
        assertEquals(100, report.discrepancies().size());
    }

    @Test
    void aCustomerIsRefusedBeforeAnythingIsRead() {
        assertThrows(AccessDeniedException.class,
                () -> service.reconcile(new CurrentUser(7, "alice", Role.CUSTOMER)));
        assertThrows(AccessDeniedException.class, () -> service.reconcile(null));
        verifyNoInteractions(accounts, transactions);
    }

    @Test
    void theCheckNeverWrites() {
        stored(new Stored("200001", "Alice Wallet", new BigDecimal("100.00")));
        ledger(List.of(new Row("200001", new BigDecimal("100.00"))), List.of());

        service.reconcile(ADMIN);

        verify(accounts, never()).save(any());
        verify(accounts, never()).saveAndFlush(any());
        verify(accounts, never()).delete(any());
        verify(transactions, never()).save(any());
    }

    // =====================================================================
    //  Stubs and fixtures
    // =====================================================================

    private record Row(String number, BigDecimal amount) implements TransactionRepository.AccountTypeTotal {
        @Override
        public String getAccountNumber() {
            return number;
        }

        @Override
        public BigDecimal getTotal() {
            return amount;
        }
    }

    private record Stored(String number, String name, BigDecimal balance)
            implements AccountRepository.StoredBalance {
        @Override
        public String getAccountNumber() {
            return number;
        }

        @Override
        public String getAccountName() {
            return name;
        }

        @Override
        public BigDecimal getBalance() {
            return balance;
        }
    }

    private void stored(Stored... rows) {
        when(accounts.findAllStoredBalances()).thenReturn(List.of(rows));
    }

    private void ledger(List<Row> credits, List<Row> debits) {
        when(transactions.sumAmountByAccount(CREDITS)).thenReturn(asTotals(credits));
        when(transactions.sumAmountByAccount(DEBITS)).thenReturn(asTotals(debits));
    }

    private static List<TransactionRepository.AccountTypeTotal> asTotals(List<Row> rows) {
        return rows.stream().map(row -> (TransactionRepository.AccountTypeTotal) row).toList();
    }

    /** Money is compared by value: DECIMAL scale is the database's business. */
    private static void assertMoney(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual),
                () -> "expected " + expected + " but was " + actual);
    }
}
