package com.tesda.ciicc.banking.backend.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tesda.ciicc.banking.backend.exception.InsufficientFundsException;
import com.tesda.ciicc.banking.backend.model.IdempotencyKey;
import com.tesda.ciicc.banking.backend.repository.IdempotencyKeyRepository;
import com.tesda.ciicc.banking.backend.web.dto.AmountRequest;
import com.tesda.ciicc.banking.backend.web.dto.TransactionResponse;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Covers the claim/replay/conflict rules. Transactional behaviour (a failed
 * operation rolls the claim back) is exercised by the MySQL integration tests
 * and the local API verification, not here.
 */
class IdempotencyServiceTest {

    private final IdempotencyKeyRepository keys = mock(IdempotencyKeyRepository.class);
    private final ObjectMapper mapper = new ObjectMapper();
    // Production wires the bean's transactional proxy as `self`; here a plain
    // second instance sharing the repository mock plays that role.
    private final IdempotencyService self = new IdempotencyService(keys, mapper, null);
    private final IdempotencyService service = new IdempotencyService(keys, mapper, self);

    private static AmountRequest amount(String value) {
        return new AmountRequest(value);
    }

    private static TransactionResponse depositResponse(String amountValue) {
        return new TransactionResponse(1, "TXN-test", "100001", "DEPOSIT",
                amountValue, "5010.00", null, "2026-10-21T17:05:00Z");
    }

    private void claimSucceeds() {
        when(keys.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void requireValidKeyRejectsBlankAndOverlongKeys() {
        assertThrows(IdempotencyKeyException.class, () -> service.requireValidKey(null));
        assertThrows(IdempotencyKeyException.class, () -> service.requireValidKey("   "));
        assertThrows(IdempotencyKeyException.class, () -> service.requireValidKey("x".repeat(101)));
        assertDoesNotThrow(() -> service.requireValidKey("order-42"));
    }

    @Test
    void freshExecutionRunsTheWorkAndFillsTheClaimWithTheResponse() throws Exception {
        claimSucceeds();

        TransactionResponse result = service.execute("key-1", "DEPOSIT", "100001",
                amount("10.00"), TransactionResponse.class, () -> depositResponse("10.00"));

        assertEquals("10.00", result.amount());
        ArgumentCaptor<IdempotencyKey> claim = ArgumentCaptor.forClass(IdempotencyKey.class);
        verify(keys).saveAndFlush(claim.capture());
        assertEquals("DEPOSIT", claim.getValue().getOperation());
        assertEquals(64, claim.getValue().getRequestHash().length());
        assertEquals(200, claim.getValue().getResponseStatus());
        assertTrue(claim.getValue().getResponseBody().contains("\"amount\":\"10.00\""));
    }

    @Test
    void replayReturnsTheStoredResponseWithoutRunningTheWork() throws Exception {
        String hash = service.requestHash("DEPOSIT", "100001", amount("10.00"));
        IdempotencyKey stored = new IdempotencyKey("key-1", "DEPOSIT", hash);
        stored.setResponseStatus(200);
        stored.setResponseBody(mapper.writeValueAsString(depositResponse("10.00")));
        when(keys.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("duplicate"));
        when(keys.findByIdempotencyKeyForUpdate("key-1")).thenReturn(Optional.of(stored));

        AtomicBoolean workRan = new AtomicBoolean(false);
        TransactionResponse replayed = service.execute("key-1", "DEPOSIT", "100001",
                amount("10.00"), TransactionResponse.class,
                () -> {
                    workRan.set(true);
                    return null;
                });

        assertFalse(workRan.get());           // no second deposit
        assertEquals("10.00", replayed.amount());
    }

    @Test
    void sameKeyWithADifferentBodyIsRejected() throws Exception {
        String hash = service.requestHash("DEPOSIT", "100001", amount("10.00"));
        IdempotencyKey stored = new IdempotencyKey("key-1", "DEPOSIT", hash);
        stored.setResponseBody(mapper.writeValueAsString(depositResponse("10.00")));
        when(keys.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("duplicate"));
        when(keys.findByIdempotencyKeyForUpdate("key-1")).thenReturn(Optional.of(stored));

        IdempotencyKeyConflictException e = assertThrows(IdempotencyKeyConflictException.class,
                () -> service.execute("key-1", "DEPOSIT", "100001", amount("20.00"),
                        TransactionResponse.class, () -> depositResponse("20.00")));

        assertTrue(e.getMessage().contains("different request"));
    }

    @Test
    void sameKeyOnADifferentAccountIsRejected() throws Exception {
        // The account number in the URL is part of the request identity: the
        // stored claim was hashed with scope "100001", so the same key and
        // body sent to "100002" hashes differently and must be a conflict.
        String hashOfFirstAccount = service.requestHash("DEPOSIT", "100001", amount("10.00"));
        IdempotencyKey stored = new IdempotencyKey("key-1", "DEPOSIT", hashOfFirstAccount);
        stored.setResponseStatus(200);
        stored.setResponseBody(mapper.writeValueAsString(depositResponse("10.00")));
        when(keys.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("duplicate"));
        when(keys.findByIdempotencyKeyForUpdate("key-1")).thenReturn(Optional.of(stored));

        assertThrows(IdempotencyKeyConflictException.class,
                () -> service.execute("key-1", "DEPOSIT", "100002", amount("10.00"),
                        TransactionResponse.class, () -> depositResponse("10.00")));
    }

    @Test
    void failedWorkLeavesTheClaimUnfilledSoTheKeyCanBeRetried() throws Exception {
        claimSucceeds();

        assertThrows(InsufficientFundsException.class, () -> service.execute("key-1", "WITHDRAW",
                "100001", amount("10.00"), TransactionResponse.class,
                () -> {
                    throw new InsufficientFundsException(
                            "Amount 10.00 exceeds the available balance of 5.00.");
                }));

        ArgumentCaptor<IdempotencyKey> claim = ArgumentCaptor.forClass(IdempotencyKey.class);
        verify(keys).saveAndFlush(claim.capture());
        assertNull(claim.getValue().getResponseBody());   // transaction rolls this insert back in production
    }
}
