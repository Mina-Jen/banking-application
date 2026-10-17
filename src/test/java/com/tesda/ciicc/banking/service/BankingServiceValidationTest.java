package com.tesda.ciicc.banking.service;

import com.tesda.ciicc.banking.exception.BankingValidationException;
import com.tesda.ciicc.banking.exception.InvalidAmountException;
import com.tesda.ciicc.banking.service.BankingService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * These rules must be enforced before any SQL runs, so the service is built
 * with a provider that fails loudly if a test reaches for the database.
 */
class BankingServiceValidationTest {

    private static BankingService serviceWithoutDatabase() {
        return new BankingService(() -> {
            throw new IllegalStateException("stub database");
        });
    }

    @Test
    void depositRejectsFractionOfACentavo() {
        InvalidAmountException e = assertThrows(InvalidAmountException.class,
                () -> serviceWithoutDatabase().deposit("100001", new BigDecimal("0.001")));
        assertTrue(e.getMessage().contains("two decimal places"));
    }

    @Test
    void depositRejectsZeroAndNegativeAmounts() {
        assertThrows(InvalidAmountException.class,
                () -> serviceWithoutDatabase().deposit("100001", BigDecimal.ZERO));
        assertThrows(InvalidAmountException.class,
                () -> serviceWithoutDatabase().deposit("100001", new BigDecimal("-100")));
    }

    @Test
    void withdrawRejectsFractionOfACentavo() {
        assertThrows(InvalidAmountException.class,
                () -> serviceWithoutDatabase().withdraw("100001", new BigDecimal("10.005")));
    }

    @Test
    void transferRejectsAmountTooLargeToStore() {
        InvalidAmountException e = assertThrows(InvalidAmountException.class,
                () -> serviceWithoutDatabase().transfer("100001", "100002", new BigDecimal("99999999999999.00")));
        assertTrue(e.getMessage().contains("too large"));
    }

    @Test
    void transferRejectsSameAccountOnBothSides() {
        BankingValidationException e = assertThrows(BankingValidationException.class,
                () -> serviceWithoutDatabase().transfer("100001", "100001", new BigDecimal("500")));
        assertEquals("Source and destination accounts must be different.", e.getMessage());
    }

    @Test
    void rejectsBlankAndOverlongAccountNumbers() {
        assertThrows(BankingValidationException.class,
                () -> serviceWithoutDatabase().getAccount("   "));
        assertThrows(BankingValidationException.class,
                () -> serviceWithoutDatabase().getAccount("123456789012345678901"));
    }

    @Test
    void createAccountRejectsBlankAndOverlongName() {
        assertThrows(BankingValidationException.class,
                () -> serviceWithoutDatabase().createAccount("100001", "  ", BigDecimal.ONE));
        assertThrows(BankingValidationException.class,
                () -> serviceWithoutDatabase().createAccount("100001", "x".repeat(101), BigDecimal.ONE));
    }

    @Test
    void createAccountRejectsNegativeInitialDeposit() {
        assertThrows(InvalidAmountException.class,
                () -> serviceWithoutDatabase().createAccount("100001", "Juan", new BigDecimal("-0.01")));
    }

    @Test
    void validInputIsPassedThroughToTheDatabase() {
        // The mirror image of the tests above: nothing may be rejected once the
        // input is legal, so reaching the stub is the expected outcome here.
        assertThrows(IllegalStateException.class,
                () -> serviceWithoutDatabase().createAccount("100001", "Juan", new BigDecimal("500.10")));
        assertThrows(IllegalStateException.class,
                () -> serviceWithoutDatabase().deposit("100001", new BigDecimal("10.00")));
    }
}
