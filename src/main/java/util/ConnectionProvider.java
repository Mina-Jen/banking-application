package util;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * Supplies database connections. BankingService depends on this instead of
 * DatabaseConnection directly so the business rules can be tested without MySQL.
 */
public interface ConnectionProvider {
    Connection getConnection() throws SQLException;
}
