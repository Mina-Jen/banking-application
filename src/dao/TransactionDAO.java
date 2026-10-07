package dao;

import model.Transaction;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class TransactionDAO {

    // Creates the next reference: TXN-001, TXN-002, ...
    public String generateReference(Connection conn) throws SQLException {
        String sql = "SELECT COALESCE(MAX(id), 0) + 1 FROM transactions";
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            rs.next();
            int next = rs.getInt(1);
            return String.format("TXN-%03d", next);
        }
    }

    // Saves a transaction record
    public void insert(Connection conn, Transaction t) throws SQLException {
        String sql = "INSERT INTO transactions "
                + "(transaction_reference, account_number, transaction_type, amount, balance, reference_account) "
                + "VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, t.getTransactionReference());
            ps.setString(2, t.getAccountNumber());
            ps.setString(3, t.getTransactionType());
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
                    t.setTransactionType(rs.getString("transaction_type"));
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