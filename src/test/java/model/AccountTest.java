package model;

import exception.InsufficientFundsException;
import exception.InvalidAmountException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AccountTest {

    private static void assertMoney(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual),
                "expected " + expected + " but was " + actual);
    }

    private static Account accountWith(String balance) {
        return new Account("100001", "Juan", new BigDecimal(balance));
    }

    @Test
    void depositAddsToBalance() throws Exception {
        Account a = accountWith("100.00");
        a.deposit(new BigDecimal("50.25"));
        assertMoney("150.25", a.getBalance());
    }

    @Test
    void withdrawSubtractsFromBalance() throws Exception {
        Account a = accountWith("100.00");
        a.withdraw(new BigDecimal("40.00"));
        assertMoney("60.00", a.getBalance());
    }

    @Test
    void withdrawingTheExactBalanceIsAllowed() throws Exception {
        Account a = accountWith("100.00");
        a.withdraw(new BigDecimal("100.00"));
        assertMoney("0", a.getBalance());
    }

    @Test
    void withdrawMoreThanBalanceIsRejectedAndBalanceUnchanged() {
        Account a = accountWith("100.00");
        assertThrows(InsufficientFundsException.class,
                () -> a.withdraw(new BigDecimal("100.01")));
        assertMoney("100.00", a.getBalance());
    }

    @Test
    void zeroNegativeAndNullAmountsAreRejected() {
        Account a = accountWith("100.00");
        assertThrows(InvalidAmountException.class, () -> a.deposit(BigDecimal.ZERO));
        assertThrows(InvalidAmountException.class, () -> a.deposit(new BigDecimal("-5")));
        assertThrows(InvalidAmountException.class, () -> a.withdraw(null));
        assertMoney("100.00", a.getBalance());
    }
}