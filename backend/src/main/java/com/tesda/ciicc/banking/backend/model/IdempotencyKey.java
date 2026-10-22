package com.tesda.ciicc.banking.backend.model;

import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.sql.Timestamp;

/**
 * One stored response per {@code Idempotency-Key} of the REST API's
 * money-moving endpoints. The claim row is inserted before the operation runs,
 * so the response columns are still empty at that point; they are filled in
 * before the same transaction commits. A failed operation rolls the claim
 * back, which keeps the key usable for a retry.
 */
@Entity
@Table(name = "idempotency_keys")
public class IdempotencyKey {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    @Column(name = "idempotency_key", nullable = false, unique = true, length = 100)
    private String idempotencyKey;

    @Column(nullable = false, length = 20)
    private String operation;           // DEPOSIT / WITHDRAW / TRANSFER

    @Column(name = "request_hash", nullable = false, length = 64)
    private String requestHash;         // SHA-256 of operation + scope + request body

    @Column(name = "response_status")
    private int responseStatus;

    @Column(name = "response_body", columnDefinition = "text")
    private String responseBody;        // JSON response returned on replay

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Timestamp createdAt;

    /** For JPA only. */
    protected IdempotencyKey() {
    }

    public IdempotencyKey(String idempotencyKey, String operation, String requestHash) {
        this.idempotencyKey = idempotencyKey;
        this.operation = operation;
        this.requestHash = requestHash;
    }

    public int getId() { return id; }

    public String getIdempotencyKey() { return idempotencyKey; }

    public String getOperation() { return operation; }

    public String getRequestHash() { return requestHash; }

    public int getResponseStatus() { return responseStatus; }
    public void setResponseStatus(int responseStatus) { this.responseStatus = responseStatus; }

    public String getResponseBody() { return responseBody; }
    public void setResponseBody(String responseBody) { this.responseBody = responseBody; }

    public Timestamp getCreatedAt() { return createdAt; }
}
