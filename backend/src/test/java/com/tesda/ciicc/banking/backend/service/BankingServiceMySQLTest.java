package com.tesda.ciicc.banking.backend.service;

import com.tesda.ciicc.banking.backend.auth.CurrentUser;
import com.tesda.ciicc.banking.backend.exception.AccountAlreadyExistsException;
import com.tesda.ciicc.banking.backend.exception.AccountNotFoundException;
import com.tesda.ciicc.banking.backend.exception.BankingException;
import com.tesda.ciicc.banking.backend.exception.BankingValidationException;
import com.tesda.ciicc.banking.backend.exception.InsufficientFundsException;
import com.tesda.ciicc.banking.backend.exception.InvalidAmountException;
import com.tesda.ciicc.banking.backend.model.Account;
import com.tesda.ciicc.banking.backend.model.Role;
import com.tesda.ciicc.banking.backend.model.Transaction;
import com.tesda.ciicc.banking.backend.model.TransactionType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Runs the service against a real MySQL server, so the native ENUM column,
 * case-insensitive collation, pessimistic locks, and transaction rollback are
 * all exercised the way production uses them.
 *
 * <p>These tests need Docker and are skipped when it is unavailable.
 */
@SpringBootTest(properties = {
        "spring.datasource.hikari.maximum-pool-size=20",
        "JWT_SECRET=mysql-integration-test-secret-0123456789abcdef"
})
@Testcontainers(disabledWithoutDocker = true)
class BankingServiceMySQLTest {

    @Container
    @ServiceConnection
    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withUrlParam("sslMode", "DISABLED")
            .withUrlParam("allowPublicKeyRetrieval", "true");

    private static final BigDecimal INITIAL_BALANCE = new BigDecimal("1000");

    /** Opens accounts with chosen numbers and reads them; ADMIN may not move money. */
    private static final CurrentUser ADMIN = new CurrentUser(0, "it-admin", Role.ADMIN);

    /** Owns every account createAccount() opens and makes the classic tests' money moves. */
    private CurrentUser holder;

    @Autowired
    private BankingService service;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanDatabase() {
        // Reverse dependency order: anomaly_flags -> transactions -> accounts,
        // and account_owners -> both accounts and users. MySQL rejects a DELETE
        // that orphans a child row, so every referencing table must be emptied
        // before the table it points at. H2 tolerated the wrong order, which is
        // why only the MySQL run caught it.
        jdbcTemplate.update("DELETE FROM anomaly_flags");
        jdbcTemplate.update("DELETE FROM idempotency_keys");
        jdbcTemplate.update("DELETE FROM transactions");
        jdbcTemplate.update("DELETE FROM account_owners");
        jdbcTemplate.update("DELETE FROM accounts");
        jdbcTemplate.update("DELETE FROM users");
        jdbcTemplate.update("DELETE FROM audit_log");
        holder = registerCustomer("it-holder");
    }

    // BigDecimal("7000") and BigDecimal("7000.00") are NOT equal() (different scale),
    // so always compare money with compareTo.
    private static void assertMoney(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual),
                "expected " + expected + " but was " + actual);
    }

    private static void assertTransferEntry(Transaction t, TransactionType expectedType,
                                            String expectedReferenceAccount, String expectedBalance) {
        assertEquals(expectedType, t.getTransactionType());
        assertEquals(expectedReferenceAccount, t.getReferenceAccount());
        assertMoney(expectedBalance, t.getBalance());
    }

    @Test
    void depositIncreasesBalanceAndIsRecorded() throws Exception {
        createAccount("100001", "Juan", new BigDecimal("5000"));

        Transaction t = deposit("100001", new BigDecimal("2000"));

        assertMoney("7000", getAccount("100001").getBalance());
        assertMoney("7000", t.getBalance());
        assertEquals(2, getTransactionHistory("100001").size()); // opening deposit + deposit
    }

    @Test
    void withdrawMoreThanBalanceIsRejectedAndBalanceUnchanged() throws Exception {
        createAccount("100001", "Juan", new BigDecimal("100"));

        assertThrows(InsufficientFundsException.class,
                () -> withdraw("100001", new BigDecimal("100.01")));

        assertMoney("100", getAccount("100001").getBalance());
        assertEquals(1, getTransactionHistory("100001").size());
    }

    @Test
    void transferMovesMoneyAndIsRecordedOnBothAccounts() throws Exception {
        createAccount("100001", "Juan", new BigDecimal("5500"));
        createAccount("100002", "Pedro", new BigDecimal("8500"));

        Transaction returned = transfer("100001", "100002", new BigDecimal("2000"));

        assertMoney("3500", getAccount("100001").getBalance());
        assertMoney("10500", getAccount("100002").getBalance());
        List<Transaction> sourceHistory = getTransactionHistory("100001");
        List<Transaction> destinationHistory = getTransactionHistory("100002");
        assertEquals(2, sourceHistory.size());
        assertEquals(2, destinationHistory.size());

        assertEquals(TransactionType.TRANSFER_OUT, returned.getTransactionType());
        assertTransferEntry(sourceHistory.get(1), TransactionType.TRANSFER_OUT, "100002", "3500");
        assertTransferEntry(destinationHistory.get(1), TransactionType.TRANSFER_IN, "100001", "10500");
    }

    @Test
    void transferTypeFollowsDirectionNotLockOrder() throws Exception {
        // "100002" -> "100001": the destination is locked first, but the source
        // must still be TRANSFER_OUT and the destination TRANSFER_IN.
        createAccount("100001", "Juan", new BigDecimal("1000"));
        createAccount("100002", "Pedro", new BigDecimal("1000"));

        transfer("100002", "100001", new BigDecimal("300"));

        assertTransferEntry(getTransactionHistory("100002").get(1),
                TransactionType.TRANSFER_OUT, "100001", "700");
        assertTransferEntry(getTransactionHistory("100001").get(1),
                TransactionType.TRANSFER_IN, "100002", "1300");
    }

    @Test
    void transferToMissingAccountChangesNothing() throws Exception {
        createAccount("100001", "Juan", new BigDecimal("1000"));

        assertThrows(AccountNotFoundException.class,
                () -> transfer("100001", "999999", new BigDecimal("100")));

        assertMoney("1000", getAccount("100001").getBalance());
        assertEquals(1, getTransactionHistory("100001").size());
    }

    /**
     * The funds check comes first, so an unfundable transfer says the same
     * thing about a destination that exists and one that does not: the account
     * number cannot be looked up with a transfer that cannot succeed.
     */
    @Test
    void shortFundsAnswerIdenticallyForAnyDestination() throws Exception {
        createAccount("100001", "Juan", new BigDecimal("100"));
        createAccount("100002", "Pedro", new BigDecimal("100"));

        InsufficientFundsException missing = assertThrows(InsufficientFundsException.class,
                () -> transfer("100001", "999999", new BigDecimal("100.01")));
        InsufficientFundsException existing = assertThrows(InsufficientFundsException.class,
                () -> transfer("100001", "100002", new BigDecimal("100.01")));

        assertEquals("Amount 100.01 exceeds the available balance of 100.00.", missing.getMessage());
        assertEquals(existing.getMessage(), missing.getMessage());

        // No lock, no withdraw, no partial write survives the rejected transfer.
        assertMoney("100", getAccount("100001").getBalance());
        assertMoney("100", getAccount("100002").getBalance());
        assertEquals(1, getTransactionHistory("100001").size());
        assertEquals(1, getTransactionHistory("100002").size());
    }

    @Test
    void transferBetweenSameAccountInDifferentCaseIsRejected() throws Exception {
        // MySQL treats "ABC123" and "abc123" as the same row; the service must too.
        createAccount("ABC123", "Juan", new BigDecimal("1000"));

        BankingValidationException e = assertThrows(BankingValidationException.class,
                () -> transfer("ABC123", "abc123", new BigDecimal("100")));

        assertEquals("Source and destination accounts must be different.", e.getMessage());
        assertMoney("1000", getAccount("ABC123").getBalance());   // no money created
        assertEquals(1, getTransactionHistory("ABC123").size());  // only the opening deposit
    }

    @Test
    void duplicateAccountNumberIsRejected() throws Exception {
        createAccount("100001", "Juan", BigDecimal.ZERO);

        assertThrows(AccountAlreadyExistsException.class,
                () -> createAccount("100001", "Someone Else", BigDecimal.TEN));
    }

    @Test
    void duplicateAccountNumberInDifferentCaseIsRejected() throws Exception {
        createAccount("ABC123", "Juan", BigDecimal.ZERO);

        assertThrows(AccountAlreadyExistsException.class,
                () -> createAccount("abc123", "Someone Else", BigDecimal.TEN));
    }

    @Test
    void zeroInitialDepositCreatesNoHistory() throws Exception {
        createAccount("100001", "Juan", BigDecimal.ZERO);
        assertEquals(List.of(), getTransactionHistory("100001"));
    }

    @Test
    void accountNameIsTrimmedAndInitialDepositIsRecorded() throws Exception {
        Account account = createAccount("100001", "  Juan Dela Cruz  ", new BigDecimal("500.10"));

        assertEquals("Juan Dela Cruz", account.getAccountName());
        assertMoney("500.10", account.getBalance());
        assertNotNull(account.getCreatedAt());
        Transaction opening = getTransactionHistory("100001").get(0);
        assertEquals(TransactionType.DEPOSIT, opening.getTransactionType());
        assertMoney("500.10", opening.getAmount());
        assertTrue(opening.getId() > 0);
        assertTrue(opening.getTransactionReference().startsWith("TXN-"));
        assertTrue(opening.getTransactionReference().length() <= 20);
    }

    @Test
    void historyIsOldestFirstAndAccountsAreListedByNumber() throws Exception {
        createAccount("100002", "Pedro", new BigDecimal("100"));
        createAccount("100001", "Juan", new BigDecimal("100"));
        deposit("100001", new BigDecimal("10"));
        withdraw("100001", new BigDecimal("5.50"));

        List<Transaction> history = getTransactionHistory("100001");
        assertEquals(List.of(TransactionType.DEPOSIT, TransactionType.DEPOSIT, TransactionType.WITHDRAW),
                history.stream().map(Transaction::getTransactionType).toList());

        // ADMIN sees every account; the order is the account number, not the
        // insertion order (100002 was created first).
        List<Account> accounts = service.listAccounts(ADMIN, 0, 20).getContent();
        assertEquals(List.of("100001", "100002"),
                accounts.stream().map(Account::getAccountNumber).toList());
    }

    @Test
    void rejectedOperationRollsBackEveryWrite() throws Exception {
        // The destination cannot hold the new balance. The source has already
        // been debited in memory, so only a rollback keeps it unchanged.
        createAccount("100001", "Juan", new BigDecimal("100"));
        createAccount("100002", "Pedro", new BigDecimal("9999999999999.99"));

        assertThrows(InvalidAmountException.class,
                () -> transfer("100001", "100002", new BigDecimal("0.01")));

        assertMoney("100", getAccount("100001").getBalance());
        assertMoney("9999999999999.99", getAccount("100002").getBalance());
        assertEquals(1, getTransactionHistory("100001").size());
        assertEquals(1, getTransactionHistory("100002").size());
    }

    @Test
    void operationsOnMissingAccountsAreRejected() throws Exception {
        assertThrows(AccountNotFoundException.class, () -> getAccount("999999"));
        assertThrows(AccountNotFoundException.class, () -> deposit("999999", BigDecimal.TEN));
        assertThrows(AccountNotFoundException.class,
                () -> getTransactionHistory("999999"));
    }

    /**
     * 50 threads hammer the service with random deposits and transfers.
     * Deposits create money, so total money must grow by exactly the sum of the
     * deposits; transfers only move money, so they must never change the total
     * and must never leave an account negative.
     */
    @Test
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void concurrentDepositsAndTransfersConserveMoneyAndKeepBalancesNonNegative() throws Exception {
        int threads = 50;
        int opsPerThread = 20;
        int accountCount = 10;

        for (int i = 1; i <= accountCount; i++) {
            createAccount(accountNumber(i), "Owner " + i, INITIAL_BALANCE);
        }
        BigDecimal initialTotal = totalMoney(accountCount);
        ConcurrentLinkedQueue<BigDecimal> deposits = new ConcurrentLinkedQueue<>();

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (int t = 0; t < threads; t++) {
                Random random = new Random(t);   // fixed seed per thread: a failure is reproducible
                futures.add(pool.submit(() -> {
                    for (int op = 0; op < opsPerThread; op++) {
                        if (random.nextBoolean()) {
                            BigDecimal amount = new BigDecimal(1 + random.nextInt(100));
                            deposit(accountNumber(1 + random.nextInt(accountCount)), amount);
                            deposits.add(amount);
                        } else {
                            int from = 1 + random.nextInt(accountCount);
                            int to = 1 + random.nextInt(accountCount);
                            if (from == to) {
                                continue;
                            }
                            BigDecimal amount = new BigDecimal(1 + random.nextInt(100));
                            try {
                                transfer(accountNumber(from), accountNumber(to), amount);
                            } catch (BankingException allowed) {
                                // insufficient funds etc.: a rejected transfer is a valid outcome
                            }
                        }
                    }
                    return null;
                }));
            }
            for (Future<?> future : futures) {
                future.get();   // rethrows any failure from a worker thread
            }
        } finally {
            pool.shutdown();
        }

        BigDecimal depositsTotal = deposits.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal expectedTotal = initialTotal.add(depositsTotal);
        BigDecimal finalTotal = totalMoney(accountCount);
        assertEquals(0, expectedTotal.compareTo(finalTotal),
                "total money changed: expected " + expectedTotal + " but was " + finalTotal);

        for (int i = 1; i <= accountCount; i++) {
            BigDecimal balance = getAccount(accountNumber(i)).getBalance();
            assertTrue(balance.compareTo(BigDecimal.ZERO) >= 0,
                    accountNumber(i) + " went negative: " + balance);
        }
    }

    private static String accountNumber(int index) {
        return String.format("ACC-%04d", index);
    }

    private BigDecimal totalMoney(int accountCount) throws Exception {
        BigDecimal total = BigDecimal.ZERO;
        for (int i = 1; i <= accountCount; i++) {
            total = total.add(getAccount(accountNumber(i)).getBalance());
        }
        return total;
    }

    // =====================================================================
    //  Phase 2: ownership and audit trail
    // =====================================================================

    @Test
    void customerCannotOperateOnAnAccountTheyDoNotOwn() throws Exception {
        createAccount("100001", "Juan", new BigDecimal("1000"));
        createAccount("100002", "Pedro", new BigDecimal("1000"));
        CurrentUser carol = registerCustomer("carol");

        assertThrows(AccountNotFoundException.class, () -> getAccount(carol, "100001"));
        assertThrows(AccountNotFoundException.class,
                () -> deposit(carol, "100001", new BigDecimal("50")));
        assertThrows(AccountNotFoundException.class,
                () -> withdraw(carol, "100001", new BigDecimal("50")));
        assertThrows(AccountNotFoundException.class,
                () -> transfer(carol, "100001", "100002", new BigDecimal("50")));

        // Nothing moved: the denial happens before any balance changes.
        assertMoney("1000", getAccount("100001").getBalance());
        assertMoney("1000", getAccount("100002").getBalance());
    }

    @Test
    void deniedCustomerCannotTellForeignAccountsFromMissingOnes() throws Exception {
        createAccount("100001", "Juan", new BigDecimal("1000"));
        CurrentUser carol = registerCustomer("carol");

        AccountNotFoundException foreign = assertThrows(AccountNotFoundException.class,
                () -> getAccount(carol, "100001"));
        AccountNotFoundException unknown = assertThrows(AccountNotFoundException.class,
                () -> getAccount(carol, "100099"));

        // Same text, no account number in it: walking a range of numbers gives
        // byte-identical errors whether the account exists or not.
        assertEquals("Account not found.", foreign.getMessage());
        assertEquals(unknown.getMessage(), foreign.getMessage());

        // ADMIN, who needs to know, keeps the detailed wording.
        AccountNotFoundException adminView = assertThrows(AccountNotFoundException.class,
                () -> getAccount("100099"));
        assertEquals("Account 100099 does not exist.", adminView.getMessage());
    }

    @Test
    void customerOwnsTheAccountTheBankGaveThem() throws Exception {
        CurrentUser carol = registerCustomer("carol");
        Account opened = service.createAccount(carol, "Carol", new BigDecimal("300"));

        // Eleven digits assigned by the service, and Carol can read the account
        // straight back with the number she was handed.
        assertTrue(opened.getAccountNumber().matches("[1-9]\\d{10}"), opened.getAccountNumber());
        assertMoney("300", getAccount(carol, opened.getAccountNumber()).getBalance());

        CurrentUser miguel = registerCustomer("miguel");
        assertThrows(AccountNotFoundException.class,
                () -> getAccount(miguel, opened.getAccountNumber()));

        // Miguel picking a number is a refusal about his role, not about any
        // account: it happens before the table is read.
        assertThrows(AccessDeniedException.class,
                () -> service.createAccount(miguel, "100003", "Miguel", BigDecimal.TEN));
    }

    /**
     * The customer path never answers "this number already exists", because the
     * number it reports is one the bank just drew. Tested against the real
     * UNIQUE index: the row is inserted first, then read back by number.
     */
    @Test
    void twoCustomersGetDifferentNumbers() throws Exception {
        Account first = service.createAccount(registerCustomer("carol"), "Carol", BigDecimal.TEN);
        Account second = service.createAccount(registerCustomer("miguel"), "Miguel", BigDecimal.TEN);

        assertFalse(first.getAccountNumber().equals(second.getAccountNumber()));
        assertMoney("10.00", getAccount(first.getAccountNumber()).getBalance());
        assertMoney("10.00", getAccount(second.getAccountNumber()).getBalance());
    }

    @Test
    void auditTrailRecordsWhoMovedMoneyAndDeniedAttempts() throws Exception {
        createAccount("100001", "Juan", new BigDecimal("1000"));
        CurrentUser carol = registerCustomerWithAccount("carol", "100001");

        deposit(carol, "100001", new BigDecimal("200"));
        Integer carolDeposits = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_log WHERE username = 'carol' AND action = 'DEPOSIT'",
                Integer.class);
        assertEquals(1, carolDeposits);

        CurrentUser miguel = registerCustomer("miguel");
        assertThrows(AccountNotFoundException.class, () -> getAccount(miguel, "100001"));
        Integer denied = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_log WHERE username = 'miguel' AND action = 'ACCESS_DENIED'",
                Integer.class);
        assertEquals(1, denied);
    }

    @Test
    void adminCannotMoveMoneyAndTheRefusalIsAudited() throws Exception {
        createAccount("100001", "Juan", new BigDecimal("1000"));
        createAccount("100002", "Pedro", new BigDecimal("1000"));

        assertThrows(AccessDeniedException.class,
                () -> service.deposit(ADMIN, "100001", new BigDecimal("50")));
        assertThrows(AccessDeniedException.class,
                () -> service.withdraw(ADMIN, "100001", new BigDecimal("50")));
        assertThrows(AccessDeniedException.class,
                () -> service.transfer(ADMIN, "100001", "100002", new BigDecimal("50")));

        assertMoney("1000", getAccount("100001").getBalance());
        assertMoney("1000", getAccount("100002").getBalance());
        assertEquals(1, getTransactionHistory("100001").size());
        // Each refusal commits on its own, although the operation rolled back.
        Integer denied = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_log WHERE username = 'it-admin' AND action = 'ACCESS_DENIED'",
                Integer.class);
        assertEquals(3, denied);
    }

    // =====================================================================
    //  Call helpers: the classic tests above move money as the holder of
    //  every account they open; the caller-aware overloads drive the
    //  ownership tests.
    // =====================================================================

    private Account createAccount(String accountNumber, String name, BigDecimal deposit) throws Exception {
        Account account = service.createAccount(ADMIN, accountNumber, name, deposit);
        jdbcTemplate.update("INSERT INTO account_owners (user_id, account_number) VALUES (?, ?)",
                holder.id(), accountNumber);
        return account;
    }

    private Account getAccount(String accountNumber) throws Exception {
        return service.getAccount(ADMIN, accountNumber);
    }

    private Account getAccount(CurrentUser caller, String accountNumber) throws Exception {
        return service.getAccount(caller, accountNumber);
    }

    private Transaction deposit(String accountNumber, BigDecimal amount) throws Exception {
        return service.deposit(holder, accountNumber, amount);
    }

    private Transaction deposit(CurrentUser caller, String accountNumber, BigDecimal amount) throws Exception {
        return service.deposit(caller, accountNumber, amount);
    }

    private Transaction withdraw(String accountNumber, BigDecimal amount) throws Exception {
        return service.withdraw(holder, accountNumber, amount);
    }

    private Transaction withdraw(CurrentUser caller, String accountNumber, BigDecimal amount) throws Exception {
        return service.withdraw(caller, accountNumber, amount);
    }

    private Transaction transfer(String sourceAccount, String destinationAccount,
                                 BigDecimal amount) throws Exception {
        return service.transfer(holder, sourceAccount, destinationAccount, amount);
    }

    private Transaction transfer(CurrentUser caller, String sourceAccount, String destinationAccount,
                                 BigDecimal amount) throws Exception {
        return service.transfer(caller, sourceAccount, destinationAccount, amount);
    }

    private List<Transaction> getTransactionHistory(String accountNumber) throws Exception {
        return service.getTransactionHistory(ADMIN, accountNumber);
    }

    /** Inserts a CUSTOMER login directly, bypassing AuthService (which would BCrypt-hash a password). */
    private CurrentUser registerCustomer(String username) {
        jdbcTemplate.update(
                "INSERT INTO users (username, password_hash, role) VALUES (?, 'not-a-real-bcrypt-hash', 'CUSTOMER')",
                username);
        Integer id = jdbcTemplate.queryForObject(
                "SELECT id FROM users WHERE username = ?", Integer.class, username);
        return new CurrentUser(id, username, Role.CUSTOMER);
    }

    /** An account has exactly one owner, so this hands it over from the default holder. */
    private CurrentUser registerCustomerWithAccount(String username, String accountNumber) {
        CurrentUser user = registerCustomer(username);
        jdbcTemplate.update("UPDATE account_owners SET user_id = ? WHERE account_number = ?",
                user.id(), accountNumber);
        return user;
    }
}
