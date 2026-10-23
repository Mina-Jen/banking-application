package com.tesda.ciicc.banking.backend.web;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tesda.ciicc.banking.backend.exception.BankingException;
import com.tesda.ciicc.banking.backend.model.IdempotencyKey;
import com.tesda.ciicc.banking.backend.repository.IdempotencyKeyRepository;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Makes a money-moving POST execute at most once per Idempotency-Key.
 *
 * <p>{@link #execute} is deliberately NOT transactional. It delegates to two
 * transactional methods:</p>
 *
 * <ul>
 *   <li>{@link #claimAndRun} inserts the claim in the SAME transaction as the
 *   operation: a concurrent request with the same key blocks on the unique
 *   index until the first transaction ends, then reads the committed response
 *   instead of running the operation again. A failed operation rolls the claim
 *   back, so the key stays usable for a retry.</li>
 *   <li>{@link #replay} reads the stored response in its own fresh
 *   transaction. It must be separate: the failed duplicate-key INSERT marks
 *   the claim transaction rollback-only, and a transaction in that state can
 *   no longer return a successful response.</li>
 * </ul>
 */
@Service
public class IdempotencyService {

    public static final String HEADER_NAME = "Idempotency-Key";
    private static final int MAX_KEY_LENGTH = 100;

    private final IdempotencyKeyRepository keys;
    private final ObjectMapper objectMapper;
    private final IdempotencyService self;

    /**
     * @param self the transactional proxy of this same bean. Calling
     *             {@code claimAndRun}/{@code replay} through it is what makes
     *             their {@code @Transactional} apply — a plain internal call
     *             would bypass the proxy (the classic self-invocation pitfall).
     *             Unit tests pass a plain second instance instead.
     */
    public IdempotencyService(IdempotencyKeyRepository keys, ObjectMapper objectMapper,
                              @Lazy IdempotencyService self) {
        this.keys = keys;
        this.objectMapper = objectMapper;
        this.self = self;
    }

    public void requireValidKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.length() > MAX_KEY_LENGTH) {
            throw new IdempotencyKeyException(
                    "Idempotency-Key header must be 1 to " + MAX_KEY_LENGTH + " characters.");
        }
    }

    /**
     * Runs {@code work} once for this key and returns its result. On a retry
     * with the same key and the same request, the stored response is returned
     * without running the work again; with a different request the key is
     * rejected as a conflict.
     *
     * @param operation    stored for diagnostics, e.g. "DEPOSIT"
     * @param scope        identifies where the request acts, e.g. the account
     *                     number in the URL; two requests that differ only in
     *                     scope must not share a key
     * @param request      request body used for the equality check
     * @param responseType DTO type the stored JSON is deserialized into
     */
    public <T> T execute(String idempotencyKey, String operation, String scope,
                         Object request, Class<T> responseType, BankingCall<T> work)
            throws BankingException {

        String requestHash = requestHash(operation, scope, request);
        try {
            return self.claimAndRun(idempotencyKey, operation, requestHash, responseType, work);
        } catch (KeyAlreadyClaimedException taken) {
            return self.replay(idempotencyKey, requestHash, responseType);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public <T> T claimAndRun(String idempotencyKey, String operation, String requestHash,
                             Class<T> responseType, BankingCall<T> work) throws BankingException {

        IdempotencyKey claim = new IdempotencyKey(idempotencyKey, operation, requestHash);
        try {
            keys.saveAndFlush(claim);
        } catch (DataIntegrityViolationException duplicate) {
            // Another request owns this key. Roll this transaction back (the
            // claim insert is discarded and the key is not consumed) and let
            // execute() read the stored response in a fresh transaction. Our
            // INSERT only failed after the other transaction committed, so
            // the stored response is complete.
            throw new KeyAlreadyClaimedException();
        }

        T result = work.call();
        claim.setResponseStatus(HttpStatus.OK.value());
        claim.setResponseBody(writeJson(result));
        return result;
    }

    // NOT readOnly, even though it only reads: the lookup below is a locking
    // read (SELECT ... FOR UPDATE), and MySQL rejects a locking read inside a
    // read-only transaction with "Cannot execute statement in a READ ONLY
    // transaction". H2 and MariaDB allow it, so only a real MySQL run catches
    // this — see IdempotencyReplayMySQLTest.
    @Transactional
    public <T> T replay(String idempotencyKey, String requestHash, Class<T> responseType) {
        // Locking read: blocks while the request that owns the key is still
        // committing, and reads the latest committed row rather than an older
        // MVCC snapshot.
        IdempotencyKey stored = keys.findByIdempotencyKeyForUpdate(idempotencyKey)
                .orElseThrow(() -> new IdempotencyKeyConflictException(
                        "Idempotency key " + idempotencyKey + " is in use by another request."));
        if (!stored.getRequestHash().equals(requestHash)) {
            throw new IdempotencyKeyConflictException(
                    "Idempotency key " + idempotencyKey + " was already used with a different request.");
        }
        return readJson(stored.getResponseBody(), responseType);
    }

    String requestHash(String operation, String scope, Object request) {
        try {
            String identity = operation + "|" + scope + "|" + objectMapper.writeValueAsString(request);
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(identity.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (JsonProcessingException | NoSuchAlgorithmException e) {
            throw new IllegalStateException("Could not hash idempotent request", e);
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not serialize idempotent response", e);
        }
    }

    private <T> T readJson(String json, Class<T> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not read stored idempotent response", e);
        }
    }

    /** Internal marker: the key is taken, roll back and replay instead. */
    private static final class KeyAlreadyClaimedException extends RuntimeException {

        private static final long serialVersionUID = 1L;
    }
}
