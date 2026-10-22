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
 * Says which user owns which account. Plain columns on purpose (no JPA
 * relations), matching how Account and Transaction are mapped; the database
 * foreign keys added in V3__auth_users.sql are the real referential guard.
 */
@Entity
@Table(name = "account_owners")
public class AccountOwner {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    @Column(name = "user_id", nullable = false)
    private int userId;

    @Column(name = "account_number", nullable = false, unique = true, length = 20)
    private String accountNumber;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Timestamp createdAt;

    /** For JPA only. */
    protected AccountOwner() {
    }

    public AccountOwner(int userId, String accountNumber) {
        this.userId = userId;
        this.accountNumber = accountNumber;
    }

    public int getId() { return id; }

    public int getUserId() { return userId; }

    public String getAccountNumber() { return accountNumber; }

    public Timestamp getCreatedAt() { return createdAt; }
}
