package com.tesda.ciicc.banking.service;

import com.tesda.ciicc.banking.exception.AccountAlreadyExistsException;
import com.tesda.ciicc.banking.exception.AccountNotFoundException;
import com.tesda.ciicc.banking.exception.BankingValidationException;
import com.tesda.ciicc.banking.exception.InsufficientFundsException;
import com.tesda.ciicc.banking.model.Transaction;
import com.tesda.ciicc.banking.service.BankingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BankingServiceH2Test {

    private static final String URL =
            "jdbc:h2:mem:banking_test;MODE=MySQL;DB_CLOSE_DELAY=-1";

    private BankingService service;

    @BeforeEach
    void freshDatabase() throws Exception {
        try (Connection c = DriverManager.getConnection(URL);
             Statement st = c.createStatement()) {
            st.execute("RUNSCRIPT FROM 'classpath:schema-h2.sql'");
        }
        service = new BankingService(() -> DriverManager.getConnection(URL));
    }

    // BigDecimal("7000") and BigDecimal("7000.00") are NOT equal() (different scale),
    // so always compare money with compareTo.
    private static void assertMoney(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual),
                "expected " + expected + " but was " + actual);
    }

    @Test
    void depositIncreasesBalanceAndIsRecorded() throws Exception {
        service.createAccount("100001", "Juan", new BigDecimal("5000"));

        Transaction t = service.deposit("100001", new BigDecimal("2000"));

        assertMoney("7000", service.getAccount("100001").getBalance());
        assertMoney("7000", t.getBalance());
        assertEquals(2, service.getTransactionHistory("100001").size()); // opening deposit + deposit
    }

    @Test
    void withdrawMoreThanBalanceIsRejectedAndBalanceUnchanged() throws Exception {
        service.createAccount("100001", "Juan", new BigDecimal("100"));

        assertThrows(InsufficientFundsException.class,
                () -> service.withdraw("100001", new BigDecimal("100.01")));

        assertMoney("100", service.getAccount("100001").getBalance());
        assertEquals(1, service.getTransactionHistory("100001").size());
    }

    @Test
    void transferMovesMoneyAndIsRecordedOnBothAccounts() throws Exception {
        service.createAccount("100001", "Juan", new BigDecimal("5500"));
        service.createAccount("100002", "Pedro", new BigDecimal("8500"));

        service.transfer("100001", "100002", new BigDecimal("2000"));

        assertMoney("3500",  service.getAccount("100001").getBalance());
        assertMoney("10500", service.getAccount("100002").getBalance());
        assertEquals(2, service.getTransactionHistory("100001").size());
        assertEquals(2, service.getTransactionHistory("100002").size());
    }

    @Test
    void transferToMissingAccountChangesNothing() throws Exception {
        service.createAccount("100001", "Juan", new BigDecimal("1000"));

        assertThrows(AccountNotFoundException.class,
                () -> service.transfer("100001", "999999", new BigDecimal("100")));

        assertMoney("1000", service.getAccount("100001").getBalance());
    }

    @Test
    void transferBetweenSameAccountInDifferentCaseIsRejected() throws Exception {
        // MySQL treats "ABC123" and "abc123" as the same row; the service must too.
        service.createAccount("ABC123", "Juan", new BigDecimal("1000"));

        BankingValidationException e = assertThrows(BankingValidationException.class,
                () -> service.transfer("ABC123", "abc123", new BigDecimal("100")));

        assertEquals("Source and destination accounts must be different.", e.getMessage());
        assertMoney("1000", service.getAccount("ABC123").getBalance());   // no money created
        assertEquals(1, service.getTransactionHistory("ABC123").size());  // only the opening deposit
    }

    @Test
    void duplicateAccountNumberIsRejected() throws Exception {
        service.createAccount("100001", "Juan", BigDecimal.ZERO);

        assertThrows(AccountAlreadyExistsException.class,
                () -> service.createAccount("100001", "Someone Else", BigDecimal.TEN));
    }

    @Test
    void zeroInitialDepositCreatesNoHistory() throws Exception {
        service.createAccount("100001", "Juan", BigDecimal.ZERO);
        assertEquals(List.of(), service.getTransactionHistory("100001"));
    }
}