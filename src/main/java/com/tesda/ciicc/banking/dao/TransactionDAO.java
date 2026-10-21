package com.tesda.ciicc.banking.dao;

import com.tesda.ciicc.banking.model.Transaction;
import com.tesda.ciicc.banking.model.TransactionType;

import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class TransactionDAO {

    private static final SecureRandom RANDOM = new SecureRandom();

    // Builds a reference without reading the transactions table, so no row or
    // table lock is needed: "TXN-" + current time + random suffix, both in
    // base 36 (4 + 8 + 6 = 18 chars, fits VARCHAR(20); still <= 20 even when
    // the time part grows to 10 chars around the year 5187).
    // The UNIQUE index on transaction_reference is the final safety net.
    public String generateReference(Connection conn) throws SQLException {
        String suffix = Long.toString(System.currentTimeMillis(), 36) + randomBase36(6);
        return "TXN-" + suffix;
    }

    private static String randomBase36(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(Character.forDigit(RANDOM.nextInt(36), 36));
        }
        return sb.toString();
    }

    // Saves a transaction record
    public void insert(Connection conn, Transaction t) throws SQLException {
        String sql = "INSERT INTO transactions "
                + "(transaction_reference, account_number, transaction_type, amount, balance, reference_account) "
                + "VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, t.getTransactionReference());
            ps.setString(2, t.getAccountNumber());
            ps.setString(3, t.getTransactionType().name());
            ps.setBigDecimal(4, t.getAmount());
            ps.setBigDecimal(5, t.getBalance());
            ps.setString(6, t.getReferenceAccount());   // null is fine for deposit/withdraw
            ps.executeUpdate();
        }
    }

    // Returns all transactions of one account, oldest first
    public List<Transaction> findByAccountNumber(Connection conn, String accountNumber) throws SQLException {
        String sql = "SELECT * FROM transactions WHERE account_number = ? ORDER BY id";
        List<Transaction> list = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, accountNumber);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Transaction t = new Transaction();
                    t.setId(rs.getInt("id"));
                    t.setTransactionReference(rs.getString("transaction_reference"));
                    t.setAccountNumber(rs.getString("account_number"));
                    t.setTransactionType(TransactionType.valueOf(rs.getString("transaction_type")));
                    t.setAmount(rs.getBigDecimal("amount"));
                    t.setBalance(rs.getBigDecimal("balance"));
                    t.setReferenceAccount(rs.getString("reference_account"));
                    t.setCreatedAt(rs.getTimestamp("created_at"));
                    list.add(t);
                }
            }
        }
        return list;
    }
}