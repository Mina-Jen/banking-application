package com.tesda.ciicc.banking.backend.service;

import com.tesda.ciicc.banking.backend.ai.CategorySuggestionClient;
import com.tesda.ciicc.banking.backend.auth.CurrentUser;
import com.tesda.ciicc.banking.backend.model.Role;
import com.tesda.ciicc.banking.backend.exception.BankingValidationException;
import com.tesda.ciicc.banking.backend.exception.InvalidAmountException;
import com.tesda.ciicc.banking.backend.repository.AccountOwnerRepository;
import com.tesda.ciicc.banking.backend.repository.AccountRepository;
import com.tesda.ciicc.banking.backend.repository.TransactionRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * These rules must be enforced before any SQL runs, so the service is built
 * with repositories that fail loudly if a test reaches the database. The
 * caller is an ADMIN because validation must behave the same for every role;
 * the CUSTOMER ownership rule itself is exercised in AuthFlowIntegrationTest
 * and BankingServiceMySQLTest.
 */
class BankingServiceValidationTest {

    private static final CurrentUser ADMIN = new CurrentUser(0, "validation-test", Role.ADMIN);

    private static BankingService serviceWithoutDatabase() {
        AccountRepository accounts = mock(AccountRepository.class);
        TransactionRepository transactions = mock(TransactionRepository.class);
        AccountOwnerRepository accountOwners = mock(AccountOwnerRepository.class);
        AuditService audit = mock(AuditService.class);
        when(accounts.findByAccountNumber(anyString()))
                .thenThrow(new IllegalStateException("stub database"));
        when(accounts.findByAccountNumberForUpdate(anyString()))
                .thenThrow(new IllegalStateException("stub database"));
        when(accountOwners.existsByUserIdAndAccountNumber(anyInt(), anyString()))
                .thenThrow(new IllegalStateException("stub database"));
        return new BankingService(accounts, transactions, accountOwners, audit,
                mock(CategorySuggestionClient.class), mock(AnomalyService.class));
    }

    @Test
    void depositRejectsFractionOfACentavo() {
        InvalidAmountException e = assertThrows(InvalidAmountException.class,
                () -> serviceWithoutDatabase().deposit(ADMIN, "100001", new BigDecimal("0.001")));
        assertTrue(e.getMessage().contains("two decimal places"));
    }

    @Test
    void depositRejectsZeroAndNegativeAmounts() {
        assertThrows(InvalidAmountException.class,
                () -> serviceWithoutDatabase().deposit(ADMIN, "100001", BigDecimal.ZERO));
        assertThrows(InvalidAmountException.class,
                () -> serviceWithoutDatabase().deposit(ADMIN, "100001", new BigDecimal("-100")));
    }

    @Test
    void withdrawRejectsFractionOfACentavo() {
        assertThrows(InvalidAmountException.class,
                () -> serviceWithoutDatabase().withdraw(ADMIN, "100001", new BigDecimal("10.005")));
    }

    @Test
    void transferRejectsAmountTooLargeToStore() {
        InvalidAmountException e = assertThrows(InvalidAmountException.class,
                () -> serviceWithoutDatabase().transfer(ADMIN, "100001", "100002",
                        new BigDecimal("99999999999999.00")));
        assertTrue(e.getMessage().contains("too large"));
    }

    @Test
    void transferRejectsSameAccountOnBothSides() {
        BankingValidationException e = assertThrows(BankingValidationException.class,
                () -> serviceWithoutDatabase().transfer(ADMIN, "100001", "100001", new BigDecimal("500")));
        assertEquals("Source and destination accounts must be different.", e.getMessage());
    }

    @Test
    void rejectsBlankAndOverlongAccountNumbers() {
        assertThrows(BankingValidationException.class,
                () -> serviceWithoutDatabase().getAccount(ADMIN, "   "));
        assertThrows(BankingValidationException.class,
                () -> serviceWithoutDatabase().getAccount(ADMIN, "123456789012345678901"));
    }

    @Test
    void createAccountRejectsBlankAndOverlongName() {
        assertThrows(BankingValidationException.class,
                () -> serviceWithoutDatabase().createAccount(ADMIN, "100001", "  ", BigDecimal.ONE));
        assertThrows(BankingValidationException.class,
                () -> serviceWithoutDatabase().createAccount(ADMIN, "100001", "x".repeat(101), BigDecimal.ONE));
    }

    @Test
    void createAccountRejectsNegativeInitialDeposit() {
        assertThrows(InvalidAmountException.class,
                () -> serviceWithoutDatabase().createAccount(ADMIN, "100001", "Juan", new BigDecimal("-0.01")));
    }

    @Test
    void rejectsPaginationOutsideTheCap() {
        // Same rule as the transaction history, and rejected before any SQL runs.
        // (The history checks its account first, so an unknown number there is a
        // 404 whatever the page parameter says — BankingServiceMySQLTest covers
        // that path against a real database.)
        assertThrows(BankingValidationException.class,
                () -> serviceWithoutDatabase().listAccounts(ADMIN, 0, 101));
        assertThrows(BankingValidationException.class,
                () -> serviceWithoutDatabase().listAccounts(ADMIN, 0, 0));
        assertThrows(BankingValidationException.class,
                () -> serviceWithoutDatabase().listAccounts(ADMIN, -1, 20));
    }

    @Test
    void rejectsOverlongDescriptionOnEveryMoneyMovement() {
        String tooLong = "x".repeat(256);
        BankingValidationException e = assertThrows(BankingValidationException.class,
                () -> serviceWithoutDatabase().deposit(ADMIN, "100001", new BigDecimal("10.00"), tooLong));
        assertTrue(e.getMessage().contains("at most 255"));
        assertThrows(BankingValidationException.class,
                () -> serviceWithoutDatabase().withdraw(ADMIN, "100001", new BigDecimal("10.00"), tooLong));
        assertThrows(BankingValidationException.class,
                () -> serviceWithoutDatabase().transfer(ADMIN, "100001", "100002",
                        new BigDecimal("10.00"), tooLong));
    }

    @Test
    void validInputIsPassedThroughToTheDatabase() {
        // The mirror image of the tests above: nothing may be rejected once the
        // input is legal, so reaching the stub is the expected outcome here.
        assertThrows(IllegalStateException.class,
                () -> serviceWithoutDatabase().createAccount(ADMIN, "100001", "Juan", new BigDecimal("500.10")));
        assertThrows(IllegalStateException.class,
                () -> serviceWithoutDatabase().deposit(ADMIN, "100001", new BigDecimal("10.00")));
    }
}
