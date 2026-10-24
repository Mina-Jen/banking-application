package com.tesda.ciicc.banking.backend.service;

import com.tesda.ciicc.banking.backend.ai.CategorySuggestionClient;
import com.tesda.ciicc.banking.backend.auth.CurrentUser;
import com.tesda.ciicc.banking.backend.exception.AccountNotFoundException;
import com.tesda.ciicc.banking.backend.exception.BankingValidationException;
import com.tesda.ciicc.banking.backend.exception.InsufficientFundsException;
import com.tesda.ciicc.banking.backend.model.Account;
import com.tesda.ciicc.banking.backend.model.Role;
import com.tesda.ciicc.banking.backend.repository.AccountOwnerRepository;
import com.tesda.ciicc.banking.backend.repository.AccountRepository;
import com.tesda.ciicc.banking.backend.repository.TransactionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * The non-enumeration rule: an account a CUSTOMER may not use must be
 * indistinguishable from an account that does not exist. Both answers are the
 * same 404 with the same text, and the account number never appears in it.
 *
 * <p>The repositories are mocks that return whatever each test needs, because
 * the point here is the ORDER of the calls: the ownership lookup is a plain
 * SELECT, so it can and must run before any {@code SELECT ... FOR UPDATE}
 * lock. The database-backed rules stay covered by BankingServiceMySQLTest and
 * AuthFlowIntegrationTest.</p>
 */
class BankingServiceOwnershipTest {

    private static final String GENERIC = "Account not found.";
    private static final String ALICE_ACCOUNT = "100001";   // exists, but not Carol's
    private static final String BOB_ACCOUNT = "100002";     // exists, someone else's
    private static final String STRANGE_ACCOUNT = "999999"; // does not exist at all

    private static final CurrentUser CAROL = new CurrentUser(7, "carol", Role.CUSTOMER);
    private static final CurrentUser ADMIN = new CurrentUser(1, "admin", Role.ADMIN);

    // JUnit creates a fresh test instance per method, so every test starts
    // from clean mocks and a clean service.
    private final AccountRepository accounts = mock(AccountRepository.class);
    private final TransactionRepository transactions = mock(TransactionRepository.class);
    private final AccountOwnerRepository accountOwners = mock(AccountOwnerRepository.class);
    private final AuditService audit = mock(AuditService.class);

    private final BankingService service =
            new BankingService(accounts, transactions, accountOwners, audit,
                    mock(CategorySuggestionClient.class), mock(AnomalyService.class));

    private static Account account(String accountNumber, String balance) {
        return new Account(accountNumber, "Carol", new BigDecimal(balance));
    }

    /**
     * Like {@link #account}, but with a real database id, because transfer()
     * compares ids to catch two spellings of the same account. Rows built by the
     * plain helper all carry id 0 and would look like one account to itself.
     */
    private static Account storedAccount(int id, String accountNumber, String balance) {
        return new Account(id, accountNumber, "Carol", new BigDecimal(balance), null);
    }

    /** Nothing is owned by Carol, so every CUSTOMER call is a denial. */
    private void carolOwnsNothing() {
        when(accountOwners.existsByUserIdAndAccountNumber(anyInt(), anyString())).thenReturn(false);
    }

    // =====================================================================
    //  A foreign account and a missing account answer identically
    // =====================================================================

    @Test
    void readingSomeoneElsesAccountGivesTheGenericNotFound() throws Exception {
        when(accounts.findByAccountNumber(ALICE_ACCOUNT))
                .thenReturn(Optional.of(account(ALICE_ACCOUNT, "100.00")));
        when(accountOwners.existsByUserIdAndAccountNumber(CAROL.id(), ALICE_ACCOUNT))
                .thenReturn(false);

        AccountNotFoundException e = assertThrows(AccountNotFoundException.class,
                () -> service.getAccount(CAROL, ALICE_ACCOUNT));

        assertEquals(GENERIC, e.getMessage());
        verify(audit).recordDeniedAccess(CAROL, ALICE_ACCOUNT);
    }

    @Test
    void foreignAndUnknownAccountsAreIndistinguishable() throws Exception {
        when(accounts.findByAccountNumber(ALICE_ACCOUNT))
                .thenReturn(Optional.of(account(ALICE_ACCOUNT, "100.00")));
        when(accounts.findByAccountNumber(STRANGE_ACCOUNT)).thenReturn(Optional.empty());
        carolOwnsNothing();

        String foreign = assertThrows(AccountNotFoundException.class,
                () -> service.getAccount(CAROL, ALICE_ACCOUNT)).getMessage();
        String unknown = assertThrows(AccountNotFoundException.class,
                () -> service.getAccount(CAROL, STRANGE_ACCOUNT)).getMessage();

        assertEquals(foreign, unknown);
        assertEquals(GENERIC, unknown);
    }

    // =====================================================================
    //  Ownership is checked BEFORE any lookup that could leak or lock
    // =====================================================================

    @Test
    void readDoesNotEvenLookUpTheAccountWhenOwnershipFails() throws Exception {
        carolOwnsNothing();

        assertThrows(AccountNotFoundException.class, () -> service.getAccount(CAROL, ALICE_ACCOUNT));

        verify(accountOwners).existsByUserIdAndAccountNumber(CAROL.id(), ALICE_ACCOUNT);
        verify(accounts, never()).findByAccountNumber(anyString());
    }

    @Test
    void depositChecksOwnershipBeforeLockingTheRow() throws Exception {
        carolOwnsNothing();

        AccountNotFoundException e = assertThrows(AccountNotFoundException.class,
                () -> service.deposit(CAROL, ALICE_ACCOUNT, new BigDecimal("10.00")));

        assertEquals(GENERIC, e.getMessage());
        verify(accounts, never()).findByAccountNumberForUpdate(anyString());
        verify(audit).recordDeniedAccess(CAROL, ALICE_ACCOUNT);
    }

    @Test
    void withdrawalChecksOwnershipBeforeLockingTheRow() throws Exception {
        carolOwnsNothing();

        assertThrows(AccountNotFoundException.class,
                () -> service.withdraw(CAROL, ALICE_ACCOUNT, new BigDecimal("10.00")));

        verify(accounts, never()).findByAccountNumberForUpdate(anyString());
        verify(audit).recordDeniedAccess(CAROL, ALICE_ACCOUNT);
    }

    @Test
    void transferChecksSourceOwnershipBeforeLockingEitherAccount() throws Exception {
        carolOwnsNothing();

        assertThrows(AccountNotFoundException.class,
                () -> service.transfer(CAROL, ALICE_ACCOUNT, STRANGE_ACCOUNT, new BigDecimal("10.00")));

        // Not one row is locked, and the destination is not even examined: a
        // denied transfer cannot reserve rows for an operation that will fail.
        verifyNoInteractions(accounts, transactions);
        verify(accountOwners).existsByUserIdAndAccountNumber(CAROL.id(), ALICE_ACCOUNT);
        verify(accountOwners, never()).existsByUserIdAndAccountNumber(CAROL.id(), STRANGE_ACCOUNT);
    }

    @Test
    void historyChecksOwnershipBeforeReading() throws Exception {
        carolOwnsNothing();

        assertThrows(AccountNotFoundException.class,
                () -> service.getTransactionHistory(CAROL, ALICE_ACCOUNT));
        assertThrows(AccountNotFoundException.class,
                () -> service.getTransactionHistoryPage(CAROL, ALICE_ACCOUNT, 0, 10));

        verifyNoInteractions(accounts, transactions);
    }

    // =====================================================================
    //  Transfer destination
    // =====================================================================

    @Test
    void missingDestinationIsStill404ForACustomerWithoutNamingIt() throws Exception {
        when(accountOwners.existsByUserIdAndAccountNumber(CAROL.id(), ALICE_ACCOUNT)).thenReturn(true);
        when(accounts.findByAccountNumberForUpdate(ALICE_ACCOUNT))
                .thenReturn(Optional.of(account(ALICE_ACCOUNT, "100.00")));
        when(accounts.findByAccountNumberForUpdate(STRANGE_ACCOUNT)).thenReturn(Optional.empty());

        AccountNotFoundException e = assertThrows(AccountNotFoundException.class,
                () -> service.transfer(CAROL, ALICE_ACCOUNT, STRANGE_ACCOUNT, new BigDecimal("10.00")));

        assertEquals(GENERIC, e.getMessage());
        assertFalse(e.getMessage().contains(STRANGE_ACCOUNT));
        // The source was locked, then rolled back; the denial audit is only
        // for ownership failures, not for a destination that simply is not there.
        verify(audit, never()).recordDeniedAccess(any(), anyString());
    }

    // =====================================================================
    //  Too little money answers before a missing destination does
    // =====================================================================

    @Test
    void anUnfundableTransferToAMissingDestinationReportsTheFundsError() throws Exception {
        when(accountOwners.existsByUserIdAndAccountNumber(CAROL.id(), ALICE_ACCOUNT)).thenReturn(true);
        when(accounts.findByAccountNumberForUpdate(ALICE_ACCOUNT))
                .thenReturn(Optional.of(account(ALICE_ACCOUNT, "100.00")));
        when(accounts.findByAccountNumberForUpdate(STRANGE_ACCOUNT)).thenReturn(Optional.empty());

        InsufficientFundsException e = assertThrows(InsufficientFundsException.class,
                () -> service.transfer(CAROL, ALICE_ACCOUNT, STRANGE_ACCOUNT, new BigDecimal("500.00")));

        assertEquals("Amount 500.00 exceeds the available balance of 100.00.", e.getMessage());
        verifyNoInteractions(transactions, audit);
    }

    /**
     * The whole point of the ordering: with the same short balance, an existing
     * destination and a destination that does not exist produce byte-identical
     * answers, so an unfundable transfer cannot be used to look accounts up.
     */
    @Test
    void existingAndMissingDestinationsAnswerIdenticallyWhenFundsAreShort() throws Exception {
        when(accountOwners.existsByUserIdAndAccountNumber(CAROL.id(), ALICE_ACCOUNT)).thenReturn(true);
        when(accounts.findByAccountNumberForUpdate(ALICE_ACCOUNT))
                .thenReturn(Optional.of(storedAccount(1, ALICE_ACCOUNT, "100.00")));
        when(accounts.findByAccountNumberForUpdate(BOB_ACCOUNT))
                .thenReturn(Optional.of(storedAccount(2, BOB_ACCOUNT, "100.00")));
        when(accounts.findByAccountNumberForUpdate(STRANGE_ACCOUNT)).thenReturn(Optional.empty());

        String missing = assertThrows(InsufficientFundsException.class,
                () -> service.transfer(CAROL, ALICE_ACCOUNT, STRANGE_ACCOUNT, new BigDecimal("500.00")))
                .getMessage();
        String existing = assertThrows(InsufficientFundsException.class,
                () -> service.transfer(CAROL, ALICE_ACCOUNT, BOB_ACCOUNT, new BigDecimal("500.00")))
                .getMessage();

        assertEquals(missing, existing);
    }

    /**
     * Moving the funds check earlier must not overtake the same-account check.
     * MySQL compares account numbers case-insensitively, so two spellings of one
     * number come back as the same row; id equality catches that before the
     * balance is read.
     */
    @Test
    void aSelfTransferIsRejectedBeforeTheBalanceIsChecked() throws Exception {
        when(accountOwners.existsByUserIdAndAccountNumber(CAROL.id(), ALICE_ACCOUNT)).thenReturn(true);
        Account oneRow = storedAccount(1, ALICE_ACCOUNT, "100.00");
        when(accounts.findByAccountNumberForUpdate(ALICE_ACCOUNT)).thenReturn(Optional.of(oneRow));
        when(accounts.findByAccountNumberForUpdate(BOB_ACCOUNT)).thenReturn(Optional.of(oneRow));

        BankingValidationException e = assertThrows(BankingValidationException.class,
                () -> service.transfer(CAROL, ALICE_ACCOUNT, BOB_ACCOUNT, new BigDecimal("500.00")));

        assertEquals("Source and destination accounts must be different.", e.getMessage());
        verifyNoInteractions(transactions, audit);
    }

    @Test
    void aFundableTransferToAMissingDestinationIsStillTheGenericNotFound() throws Exception {
        when(accountOwners.existsByUserIdAndAccountNumber(CAROL.id(), ALICE_ACCOUNT)).thenReturn(true);
        // Withdrawing exactly the balance is allowed, so this is the boundary
        // where the funds check passes and the destination 404 is reached.
        when(accounts.findByAccountNumberForUpdate(ALICE_ACCOUNT))
                .thenReturn(Optional.of(account(ALICE_ACCOUNT, "500.00")));
        when(accounts.findByAccountNumberForUpdate(STRANGE_ACCOUNT)).thenReturn(Optional.empty());

        AccountNotFoundException e = assertThrows(AccountNotFoundException.class,
                () -> service.transfer(CAROL, ALICE_ACCOUNT, STRANGE_ACCOUNT, new BigDecimal("500.00")));

        assertEquals(GENERIC, e.getMessage());
        verifyNoInteractions(transactions);
    }

    // =====================================================================
    //  Listing accounts: the query itself is scoped, not a per-row filter
    // =====================================================================

    @Test
    void customerListComesFromTheOwnershipScopedQuery() throws Exception {
        service.listAccounts(CAROL, 0, 20);

        verify(accounts).findOwnedByUserId(eq(CAROL.id()), any(Pageable.class));
        // The unscoped ADMIN query must never run for a customer: it would hand
        // the whole table to anyone with a login.
        verify(accounts, never()).findAllByOrderByAccountNumberAsc(any(Pageable.class));
        verifyNoInteractions(audit);
    }

    @Test
    void adminListIsTheUnscopedQuery() throws Exception {
        service.listAccounts(ADMIN, 2, 5);

        verify(accounts).findAllByOrderByAccountNumberAsc(any(Pageable.class));
        verify(accounts, never()).findOwnedByUserId(anyInt(), any(Pageable.class));
        verifyNoInteractions(accountOwners, audit);
    }

    // =====================================================================
    //  ADMIN keeps the detailed wording an operator needs
    // =====================================================================

    @Test
    void adminKeepsTheDetailedNotFound() throws Exception {
        when(accounts.findByAccountNumber(STRANGE_ACCOUNT)).thenReturn(Optional.empty());

        AccountNotFoundException e = assertThrows(AccountNotFoundException.class,
                () -> service.getAccount(ADMIN, STRANGE_ACCOUNT));

        assertEquals("Account " + STRANGE_ACCOUNT + " does not exist.", e.getMessage());
        verifyNoInteractions(accountOwners, audit);
    }

    // =====================================================================
    //  ADMIN oversees money but never moves it
    // =====================================================================

    @Test
    void adminCannotDepositWithdrawOrTransferAndNothingIsLocked() {
        BigDecimal ten = new BigDecimal("10.00");

        AccessDeniedException deposit = assertThrows(AccessDeniedException.class,
                () -> service.deposit(ADMIN, ALICE_ACCOUNT, ten));
        assertThrows(AccessDeniedException.class, () -> service.withdraw(ADMIN, ALICE_ACCOUNT, ten));
        assertThrows(AccessDeniedException.class,
                () -> service.transfer(ADMIN, ALICE_ACCOUNT, STRANGE_ACCOUNT, ten));

        assertEquals("Administrators cannot deposit. Only the account holder can move money.",
                deposit.getMessage());
        verify(audit).recordDeniedMoneyMovement(ADMIN, ALICE_ACCOUNT, "deposit");
        verify(audit).recordDeniedMoneyMovement(ADMIN, ALICE_ACCOUNT, "withdraw");
        verify(audit).recordDeniedMoneyMovement(ADMIN, ALICE_ACCOUNT, "transfer");
        verifyNoInteractions(accounts, transactions, accountOwners);
    }

    @Test
    void adminIsRefusedEvenForAMissingAccount() {
        // The refusal is about the role, so it does not depend on (or reveal)
        // whether the account exists.
        assertThrows(AccessDeniedException.class,
                () -> service.deposit(ADMIN, STRANGE_ACCOUNT, new BigDecimal("10.00")));
        verifyNoInteractions(accounts, transactions);
    }

    @Test
    void adminBypassesTheOwnershipLookup() throws Exception {
        when(accounts.findByAccountNumber(ALICE_ACCOUNT))
                .thenReturn(Optional.of(account(ALICE_ACCOUNT, "100.00")));

        Account account = service.getAccount(ADMIN, ALICE_ACCOUNT);

        assertEquals(ALICE_ACCOUNT, account.getAccountNumber());
        verifyNoInteractions(accountOwners, audit);
    }
}
