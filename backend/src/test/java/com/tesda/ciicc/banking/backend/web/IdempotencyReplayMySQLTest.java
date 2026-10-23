package com.tesda.ciicc.banking.backend.web;

import com.tesda.ciicc.banking.backend.web.dto.AmountRequest;
import com.tesda.ciicc.banking.backend.web.dto.TransactionResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Replays an Idempotency-Key against a real MySQL server.
 *
 * <p>The duplicate-key path ends in {@code IdempotencyService.replay}, which
 * reads the stored response with SELECT ... FOR UPDATE. MySQL refuses a locking
 * read inside a read-only transaction, so if that method is ever annotated
 * {@code @Transactional(readOnly = true)} the retry answers 500 instead of the
 * stored result. H2 and MariaDB both allow the locking read, so no H2-backed
 * test can see the difference — this class is the only guard.
 *
 * <p>Needs Docker; skipped when it is unavailable.
 */
@SpringBootTest(properties = "JWT_SECRET=replay-integration-test-secret-0123456789abcdef")
@Testcontainers(disabledWithoutDocker = true)
class IdempotencyReplayMySQLTest {

    @Container
    @ServiceConnection
    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withUrlParam("sslMode", "DISABLED")
            .withUrlParam("allowPublicKeyRetrieval", "true");

    @Autowired
    private IdempotencyService idempotency;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanKeys() {
        jdbcTemplate.update("DELETE FROM idempotency_keys");
    }

    private static final String ACCOUNT = "87758434440";

    private static TransactionResponse depositRow(String amount, String balanceAfter) {
        return new TransactionResponse(7, "TXN-test-0000000007", ACCOUNT, "DEPOSIT",
                amount, balanceAfter, null, "compose check", "2026-10-23T00:00:00Z", null);
    }

    @Test
    void replayingTheSameKeyReturnsTheStoredResponseAndRunsTheWorkOnce() throws Exception {
        String key = "replay-" + UUID.randomUUID();
        AmountRequest request = new AmountRequest("50.25");
        AtomicInteger workCalls = new AtomicInteger();

        TransactionResponse first = idempotency.execute(key, "DEPOSIT", ACCOUNT, request,
                TransactionResponse.class, () -> {
                    workCalls.incrementAndGet();
                    return depositRow("50.25", "150.25");
                });

        // A retry whose lambda would return a different balance: if the stored
        // response is not the one that comes back, the assertion below shows it.
        TransactionResponse retry = idempotency.execute(key, "DEPOSIT", ACCOUNT, request,
                TransactionResponse.class, () -> {
                    workCalls.incrementAndGet();
                    return depositRow("50.25", "999.99");
                });

        assertEquals(first, retry, "the retry must return the response stored by the first request");
        assertEquals(1, workCalls.get(), "the business operation must run exactly once for one key");
    }

    @Test
    void theSameKeyWithADifferentRequestBodyIsAConflict() throws Exception {
        String key = "conflict-" + UUID.randomUUID();

        idempotency.execute(key, "DEPOSIT", ACCOUNT, new AmountRequest("10.00"),
                TransactionResponse.class, () -> depositRow("10.00", "110.00"));

        IdempotencyKeyConflictException conflict = assertThrows(IdempotencyKeyConflictException.class,
                () -> idempotency.execute(key, "DEPOSIT", ACCOUNT, new AmountRequest("20.00"),
                        TransactionResponse.class, () -> depositRow("20.00", "120.00")));
        assertEquals(true, conflict.getMessage().contains("different request"),
                "the message should name the mismatch: " + conflict.getMessage());
    }
}
