package com.tesda.ciicc.banking.backend.service;

import com.tesda.ciicc.banking.backend.ai.CategorySuggestionClient;
import com.tesda.ciicc.banking.backend.auth.CurrentUser;
import com.tesda.ciicc.banking.backend.exception.AccountAlreadyExistsException;
import com.tesda.ciicc.banking.backend.exception.AccountNumberAllocationException;
import com.tesda.ciicc.banking.backend.exception.BankingValidationException;
import com.tesda.ciicc.banking.backend.model.Account;
import com.tesda.ciicc.banking.backend.model.AccountOwner;
import com.tesda.ciicc.banking.backend.model.Role;
import com.tesda.ciicc.banking.backend.repository.AccountOwnerRepository;
import com.tesda.ciicc.banking.backend.repository.AccountRepository;
import com.tesda.ciicc.banking.backend.repository.TransactionRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * The bank, not the customer, chooses the account number. What that has to
 * guarantee: a customer's request always comes back with a usable generated
 * number, "already exists" is never said to a customer (it would confirm that
 * the number they guessed is somebody's account), and the operator keeps the
 * detailed 409 that an operator needs when keying in a paper account.
 *
 * <p>The repositories are mocks, so the collision behaviour can be scripted —
 * a real duplicate of an 11-digit random number is not something a test can
 * arrange. {@code AccountNumberGeneratorTest} covers the numbers themselves and
 * the database rules stay in {@code BankingServiceMySQLTest}.</p>
 */
class BankingServiceAccountNumberTest {

    private static final CurrentUser CAROL = new CurrentUser(7, "carol", Role.CUSTOMER);
    private static final CurrentUser ADMIN = new CurrentUser(1, "admin", Role.ADMIN);
    private static final BigDecimal TEN = new BigDecimal("10.00");

    // JUnit creates a fresh test instance per method: clean mocks every time.
    private final AccountRepository accounts = mock(AccountRepository.class);
    private final TransactionRepository transactions = mock(TransactionRepository.class);
    private final AccountOwnerRepository accountOwners = mock(AccountOwnerRepository.class);
    private final AuditService audit = mock(AuditService.class);

    private final BankingService service =
            new BankingService(accounts, transactions, accountOwners, audit,
                    mock(CategorySuggestionClient.class), mock(AnomalyService.class));

    /** Lets every number through, as an empty table would. */
    private void tableIsFree() {
        when(accounts.findByAccountNumber(anyString())).thenReturn(Optional.empty());
        when(accounts.saveAndFlush(any(Account.class))).thenAnswer(call -> call.getArgument(0));
    }

    /** The account the service actually wrote, taken out of the mock. */
    private Account onlySavedAccount() {
        ArgumentCaptor<Account> saved = ArgumentCaptor.forClass(Account.class);
        verify(accounts).saveAndFlush(saved.capture());
        return saved.getValue();
    }

    // =====================================================================
    //  A customer gets a number, and never hears "already exists"
    // =====================================================================

    @Test
    void customerIsGivenAGeneratedNumber() throws Exception {
        tableIsFree();

        Account account = service.createAccount(CAROL, "Carol", TEN);

        String number = onlySavedAccount().getAccountNumber();
        assertEquals(number, account.getAccountNumber());
        assertTrue(number.matches("[1-9]\\d{10}"), number + " is not 11 digits from the first digit");
        assertTrue(AccountNumberGenerator.isValid(number), number + " has a bad check digit");
    }

    @Test
    void theGeneratedNumberIsTheOneTheCustomerIsGivenOwnershipOf() throws Exception {
        tableIsFree();

        service.createAccount(CAROL, "Carol", TEN);

        ArgumentCaptor<AccountOwner> owner = ArgumentCaptor.forClass(AccountOwner.class);
        verify(accountOwners).save(owner.capture());
        // The link is by account number, so a wrong number here would leave the
        // customer unable to see the account they just opened.
        assertEquals(onlySavedAccount().getAccountNumber(), owner.getValue().getAccountNumber());
        assertEquals(CAROL.id(), owner.getValue().getUserId());
    }

    @Test
    void aCollisionIsDrawnAgainInsteadOfRefused() throws Exception {
        // First draw already taken, second free: the customer must not notice.
        when(accounts.findByAccountNumber(anyString()))
                .thenReturn(Optional.of(new Account("00000000000", "Taken", BigDecimal.ZERO)),
                        Optional.empty());
        when(accounts.saveAndFlush(any(Account.class))).thenAnswer(call -> call.getArgument(0));

        service.createAccount(CAROL, "Carol", TEN);

        verify(accounts, times(2)).findByAccountNumber(anyString());
        verify(accounts).saveAndFlush(any(Account.class));
    }

    @Test
    void fiveCollisionsGiveUpWithoutWritingAnything() {
        when(accounts.findByAccountNumber(anyString()))
                .thenReturn(Optional.of(new Account("00000000000", "Taken", BigDecimal.ZERO)));

        AccountNumberAllocationException e = assertThrows(AccountNumberAllocationException.class,
                () -> service.createAccount(CAROL, "Carol", TEN));

        assertEquals("Could not assign an account number. Please try again.", e.getMessage());
        verify(accounts, times(5)).findByAccountNumber(anyString());
        // Nothing half-created: the give-up happens before the first INSERT.
        verifyNoInteractions(transactions, accountOwners, audit);
    }

    /**
     * The one collision a SELECT cannot see: another session inserts the same
     * number in the same instant, so the UNIQUE index rejects the INSERT. The
     * failed statement cannot be retried inside the same transaction, and the
     * customer still must not be told "already exists".
     */
    @Test
    void losingTheRaceIsARetryNotAnAlreadyExists() {
        tableIsFree();
        when(accounts.saveAndFlush(any(Account.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate entry"));

        assertThrows(AccountNumberAllocationException.class,
                () -> service.createAccount(CAROL, "Carol", TEN));

        verify(accounts, times(1)).saveAndFlush(any(Account.class));
        verifyNoInteractions(audit, transactions);
    }

    @Test
    void argumentsAreCheckedBeforeAnyNumberIsDrawn() {
        assertThrows(BankingValidationException.class,
                () -> service.createAccount(CAROL, "   ", TEN));
        assertThrows(BankingValidationException.class,
                () -> service.createAccount(CAROL, "x".repeat(101), TEN));

        // A rejected request must not consume a draw or touch the table.
        verifyNoInteractions(accounts);
    }

    // =====================================================================
    //  Choosing a number is an operator action
    // =====================================================================

    @Test
    void customerCannotChooseAnAccountNumber() {
        AccessDeniedException e = assertThrows(AccessDeniedException.class,
                () -> service.createAccount(CAROL, "100001", "Carol", TEN));

        assertEquals("Only an ADMIN may choose an account number. The bank assigns it for customers.",
                e.getMessage());
        verifyNoInteractions(accounts, transactions, accountOwners, audit);
    }

    @Test
    void adminKeepsChoosingNumbersAndKeepsTheDetailedConflict() throws Exception {
        tableIsFree();
        when(accounts.findByAccountNumber("100001"))
                .thenReturn(Optional.of(new Account("100001", "Someone", BigDecimal.ZERO)));

        AccountAlreadyExistsException e = assertThrows(AccountAlreadyExistsException.class,
                () -> service.createAccount(ADMIN, "100001", "Juan", TEN));

        assertEquals("Account number 100001 already exists.", e.getMessage());
    }

    @Test
    void adminCreatedAccountBelongsToNobody() throws Exception {
        tableIsFree();

        Account account = service.createAccount(ADMIN, "100001", "Branch Cash Box", TEN);

        assertEquals("100001", account.getAccountNumber());
        // No owner row: an operator account is not somebody's account, and an
        // owner row would hand it to a customer's list.
        verifyNoInteractions(accountOwners);
        verify(audit).record(eq("admin"), eq(AuditService.ACCOUNT_CREATED), eq("100001"), anyString());
    }
}
