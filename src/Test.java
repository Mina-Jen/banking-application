import dao.AccountDAO;
import model.Account;
import util.DatabaseConnection;

import java.math.BigDecimal;
import java.sql.Connection;

public class Test {
    public static void main(String[] args) {
        AccountDAO dao = new AccountDAO();

        try (Connection conn = DatabaseConnection.getConnection()) {
            // 1. Insert a test account
            dao.insert(conn, new Account("999999", "Test User", new BigDecimal("100.00")));

            // 2. Read it back
            Account a = dao.findByAccountNumber(conn, "999999");
            System.out.println(a.getAccountNumber() + " | " + a.getAccountName() + " | " + a.getBalance());

            // 3. Check a missing account returns null
            System.out.println("Missing account: " + dao.findByAccountNumber(conn, "000000"));

            // 4. List all
            System.out.println("Total accounts: " + dao.findAll(conn).size());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}