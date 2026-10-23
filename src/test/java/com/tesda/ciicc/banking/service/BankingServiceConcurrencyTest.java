package com.tesda.ciicc.banking.service;

import com.tesda.ciicc.banking.exception.BankingException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 50 threads hammer the service with random deposits and transfers.
 * Deposits create money, so total money must grow by exactly the sum of the
 * deposits; transfers only move money, so they must never change the total
 * and must never leave an account negative.
 */
class BankingServiceConcurrencyTest {

    // LOCK_TIMEOUT raised so hot rows under contention fail only after 10 s,
    // not on H2's default 1 s. Separate DB name keeps this test isolated
    // from the other test classes.
    private static final String URL =
            "jdbc:h2:mem:banking_concurrency;MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000";
    private static final int THREADS = 50;
    private static final int OPS_PER_THREAD = 20;
    private static final int ACCOUNTS = 10;
    private static final BigDecimal INITIAL_BALANCE = new BigDecimal("1000");

    private BankingService service;

    @BeforeEach
    void freshDatabase() throws Exception {
        try (Connection c = DriverManager.getConnection(URL);
             Statement st = c.createStatement()) {
            st.execute("RUNSCRIPT FROM 'classpath:schema-h2.sql'");
        }
        service = new BankingService(() -> DriverManager.getConnection(URL));
        for (int i = 1; i <= ACCOUNTS; i++) {
            service.createAccount(accountNumber(i), "Owner " + i, INITIAL_BALANCE);
        }
    }

    @Test
    void concurrentDepositsAndTransfersConserveMoneyAndKeepBalancesNonNegative() throws Exception {
        BigDecimal initialTotal = totalMoney();
        ConcurrentLinkedQueue<BigDecimal> deposits = new ConcurrentLinkedQueue<>();

        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        List<Future<?>> futures = new ArrayList<>();
        for (int t = 0; t < THREADS; t++) {
            Random random = new Random(t);   // fixed seed per thread: a failure is reproducible
            futures.add(pool.submit(() -> {
                for (int op = 0; op < OPS_PER_THREAD; op++) {
                    if (random.nextBoolean()) {
                        BigDecimal amount = new BigDecimal(1 + random.nextInt(100));
                        service.deposit(randomAccount(random), amount);
                        deposits.add(amount);
                    } else {
                        int from = 1 + random.nextInt(ACCOUNTS);
                        int to = 1 + random.nextInt(ACCOUNTS);
                        if (from == to) {
                            continue;
                        }
                        BigDecimal amount = new BigDecimal(1 + random.nextInt(100));
                        try {
                            service.transfer(accountNumber(from), accountNumber(to), amount);
                        } catch (BankingException allowed) {
                            // insufficient funds etc.: a rejected transfer is a valid outcome
                        }
                    }
                }
                return null;
            }));
        }
        for (Future<?> future : futures) {
            future.get();   // rethrows any SQLException from a worker thread
        }
        pool.shutdown();

        BigDecimal depositsTotal = deposits.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal expectedTotal = initialTotal.add(depositsTotal);
        BigDecimal finalTotal = totalMoney();
        assertEquals(0, expectedTotal.compareTo(finalTotal),
                "total money changed: expected " + expectedTotal + " but was " + finalTotal);

        for (int i = 1; i <= ACCOUNTS; i++) {
            BigDecimal balance = service.getAccount(accountNumber(i)).getBalance();
            assertTrue(balance.compareTo(BigDecimal.ZERO) >= 0,
                    accountNumber(i) + " went negative: " + balance);
        }
    }

    private static String randomAccount(Random random) {
        return accountNumber(1 + random.nextInt(ACCOUNTS));
    }

    private static String accountNumber(int index) {
        return String.format("ACC-%04d", index);
    }

    private BigDecimal totalMoney() throws Exception {
        BigDecimal total = BigDecimal.ZERO;
        for (int i = 1; i <= ACCOUNTS; i++) {
            total = total.add(service.getAccount(accountNumber(i)).getBalance());
        }
        return total;
    }
}
