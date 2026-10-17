package com.tesda.ciicc.banking.util;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class DatabaseConnection {

    private static final String URL =
            env("BANKING_DB_URL", "jdbc:mysql://localhost:3306/banking_db");
    private static final String USER = env("BANKING_DB_USER", "root");
    private static final String PASSWORD = env("BANKING_DB_PASSWORD", "");

    // Returns a NEW connection each time it is called.
    // The caller is responsible for closing it (use try-with-resources).
    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(URL, USER, PASSWORD);
    }

    // The XAMPP defaults are kept as fallbacks; set BANKING_DB_PASSWORD in the
    // environment so real credentials never appear in source or in git.
    private static String env(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isEmpty() ? fallback : value;
    }
}
