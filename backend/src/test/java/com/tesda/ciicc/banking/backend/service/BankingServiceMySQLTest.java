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
import org.springframework.security.access.AccessDeniedException;
import org.springframework.jdbc.core.JdbcTemplate;
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

    /** The existing tests act as the operator; the ownership rule has its own tests below. */
    private static final CurrentUser ADMIN = new CurrentUser(0, "it-admin", Role.ADMIN);

    @Autowired
    private BankingService service;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanDatabase() {
        jdbcTemplate.update("DELETE FROM transactions");
        jdbcTemplate.update("DELETE FROM accounts");
        jdbcTemplate.update("DELETE FROM audit_log");
        jdbcTemplate.update("DELETE FROM account_owners");
        jdbcTemplate.update("DELETE FROM users");
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

        List<Account> accounts = service.listAccounts();
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

        assertThrows(AccessDeniedException.class, () -> getAccount(carol, "100001"));
        assertThrows(AccessDeniedException.class,
                () -> deposit(carol, "100001", new BigDecimal("50")));
        assertThrows(AccessDeniedException.class,
                () -> transfer(carol, "100001", "100002", new BigDecimal("50")));

        // Nothing moved: the denial happens before any balance changes.
        assertMoney("1000", getAccount("100001").getBalance());
        assertMoney("1000", getAccount("100002").getBalance());
    }

    @Test
    void customerOwnsTheAccountsTheyCreate() throws Exception {
        CurrentUser carol = registerCustomer("carol");
        createAccount(carol, "100003", "Carol", new BigDecimal("300"));

        assertMoney("300", getAccount(carol, "100003").getBalance());

        CurrentUser miguel = registerCustomer("miguel");
        assertThrows(AccessDeniedException.class, () -> getAccount(miguel, "100003"));
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
        assertThrows(AccessDeniedException.class, () -> getAccount(miguel, "100001"));
        Integer denied = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_log WHERE username = 'miguel' AND action = 'ACCESS_DENIED'",
                Integer.class);
        assertEquals(1, denied);
    }

    // =====================================================================
    //  Call helpers: the classic tests above run as ADMIN, so they keep their
    //  original shape; the caller-aware overloads drive the ownership tests.
    // =====================================================================

    private Account createAccount(String accountNumber, String name, BigDecimal deposit) throws Exception {
        return service.createAccount(ADMIN, accountNumber, name, deposit);
    }

    private Account createAccount(CurrentUser caller, String accountNumber, String name,
                                  BigDecimal deposit) throws Exception {
        return service.createAccount(caller, accountNumber, name, deposit);
    }

    private Account getAccount(String accountNumber) throws Exception {
        return service.getAccount(ADMIN, accountNumber);
    }

    private Account getAccount(CurrentUser caller, String accountNumber) throws Exception {
        return service.getAccount(caller, accountNumber);
    }

    private Transaction deposit(String accountNumber, BigDecimal amount) throws Exception {
        return service.deposit(ADMIN, accountNumber, amount);
    }

    private Transaction deposit(CurrentUser caller, String accountNumber, BigDecimal amount) throws Exception {
        return service.deposit(caller, accountNumber, amount);
    }

    private Transaction withdraw(String accountNumber, BigDecimal amount) throws Exception {
        return service.withdraw(ADMIN, accountNumber, amount);
    }

    private Transaction transfer(String sourceAccount, String destinationAccount,
                                 BigDecimal amount) throws Exception {
        return service.transfer(ADMIN, sourceAccount, destinationAccount, amount);
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

    private CurrentUser registerCustomerWithAccount(String username, String accountNumber) {
        CurrentUser user = registerCustomer(username);
        jdbcTemplate.update("INSERT INTO account_owners (user_id, account_number) VALUES (?, ?)",
                user.id(), accountNumber);
        return user;
    }
}
