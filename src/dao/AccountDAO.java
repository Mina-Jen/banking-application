package dao;

import model.Account;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class AccountDAO {

    // Saves a new account to the database
    public void insert(Connection conn, Account account) throws SQLException {
        String sql = "INSERT INTO accounts (account_number, account_name, balance) VALUES (?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, account.getAccountNumber());
            ps.setString(2, account.getAccountName());
            ps.setBigDecimal(3, account.getBalance());
            ps.executeUpdate();
        }
    }

    // Returns the account, or null if it does not exist
    public Account findByAccountNumber(Connection conn, String accountNumber) throws SQLException {
        String sql = "SELECT * FROM accounts WHERE account_number = ?";
        return queryOne(conn, sql, accountNumber);
    }

    // Same as above, but locks the row until commit/rollback.
    // Used in deposit, withdraw, and transfer so two operations
    // cannot change the same balance at the same time.
    public Account findByAccountNumberForUpdate(Connection conn, String accountNumber) throws SQLException {
        String sql = "SELECT * FROM accounts WHERE account_number = ? FOR UPDATE";
        return queryOne(conn, sql, accountNumber);
    }

    // Returns all accounts, ordered by account number
    public List<Account> findAll(Connection conn) throws SQLException {
        String sql = "SELECT * FROM accounts ORDER BY account_number";
        List<Account> accounts = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                accounts.add(mapRow(rs));
            }
        }
        return accounts;
    }

    // Sets the account to a new balance
    public void updateBalance(Connection conn, String accountNumber, BigDecimal newBalance) throws SQLException {
        String sql = "UPDATE accounts SET balance = ? WHERE account_number = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setBigDecimal(1, newBalance);
            ps.setString(2, accountNumber);
            ps.executeUpdate();
        }
    }

    // ---------- helpers ----------

    private Account queryOne(Connection conn, String sql, String accountNumber) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, accountNumber);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
                return null;
            }
        }
    }

    // Converts one database row into an Account object
    private Account mapRow(ResultSet rs) throws SQLException {
        Account a = new Account();
        a.setId(rs.getInt("id"));
        a.setAccountNumber(rs.getString("account_number"));
        a.setAccountName(rs.getString("account_name"));
        a.setBalance(rs.getBigDecimal("balance"));
        a.setCreatedAt(rs.getTimestamp("created_at"));
        return a;
    }
}