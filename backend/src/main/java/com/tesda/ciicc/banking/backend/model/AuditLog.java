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
 * One audit trail entry. Rows are only ever INSERTed; no code updates or
 * deletes them. See V4__audit_log.sql for the meaning of each column.
 */
@Entity
@Table(name = "audit_log")
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    @CreationTimestamp
    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Timestamp occurredAt;

    @Column(length = 50)
    private String username;

    @Column(nullable = false, length = 40)
    private String action;

    @Column(name = "account_number", length = 20)
    private String accountNumber;

    @Column(length = 500)
    private String details;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    /** For JPA only. */
    protected AuditLog() {
    }

    public AuditLog(String username, String action, String accountNumber, String details, String ipAddress) {
        this.username = username;
        this.action = action;
        this.accountNumber = accountNumber;
        this.details = details;
        this.ipAddress = ipAddress;
    }

    public int getId() { return id; }

    public Timestamp getOccurredAt() { return occurredAt; }

    public String getUsername() { return username; }

    public String getAction() { return action; }

    public String getAccountNumber() { return accountNumber; }

    public String getDetails() { return details; }

    public String getIpAddress() { return ipAddress; }
}
