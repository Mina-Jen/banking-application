# Banking Application

A simple console-based banking application built with **Java**, **JDBC**, and **MySQL (XAMPP)**.
Final project for TESDA - Java Programming NCIII.

## Features
1. Create Account
2. Balance Inquiry
3. List Accounts
4. Deposit
5. Withdraw
6. Transfer (single database transaction with commit/rollback, recorded on both accounts)
7. Transaction History

## Technologies
- Java (JDK 17+)
- Apache Maven
- MySQL / MariaDB via XAMPP
- MySQL Connector/J (JDBC driver)
- JUnit 5 (business-rule tests)
- H2 (in-memory database for tests only)
- IntelliJ IDEA

## Project Structure

```
pom.xml           Maven build; the JDBC driver is resolved from Maven Central
src/main/java/
├── model/        Account, Transaction
├── dao/          AccountDAO, TransactionDAO
├── service/      BankingService (business logic and validation)
├── exception/    Custom exceptions
├── util/         DatabaseConnection
└── Main.java     Console menu
src/test/java/    Validation and H2 integration tests
database/         SQL script
```

## Setup
1. Start MySQL in XAMPP.
2. Run the script in `database/banking_db.sql` using phpMyAdmin (SQL tab).
3. Database settings default to the XAMPP values (`root`, empty password,
   `jdbc:mysql://localhost:3306/banking_db`). To use different ones, set the
   `BANKING_DB_URL`, `BANKING_DB_USER` and `BANKING_DB_PASSWORD` environment
   variables instead of editing `src/main/java/util/DatabaseConnection.java` —
   that keeps real credentials out of the repository.
4. Run the application:
   - From a terminal: `mvn compile exec:java`
   - From IntelliJ: open the project's `pom.xml`; IntelliJ imports it as a Maven project,
     then run `Main`.

The first build downloads MySQL Connector/J from Maven Central, so an internet connection
is needed once. After that everything is cached in `~/.m2`.

## Tests
`mvn test` runs two groups of tests in `src/test/java`. Neither needs MySQL, so they pass
on a machine without it.

- **Validation tests** (`BankingServiceValidationTest`) cover the input rules: amount
  precision and size, blank or over-long account number and name, and transfer between
  identical accounts. They never open a database connection.
- **Integration tests** (`BankingServiceH2Test`) run the real service and DAOs against an
  in-memory H2 database. They cover the paths that write balances: deposit, withdraw,
  transfer (recorded on both accounts), duplicate accounts, and that a failed operation
  leaves balances unchanged.

Row locking and concurrent access are not covered by the automated tests, because H2
behaves differently from MySQL there. The demonstration below checks the application
against real MySQL.

## Demonstration Script
Covers every required feature and a validation failure:
1. Create `100001` Juan Dela Cruz with an initial deposit of `5000`.
2. Create `100002` Pedro Santos with `8500`.
3. Balance Inquiry for `100001`.
4. List Accounts (shows both accounts and the total).
5. Deposit `2000` into `100001` → balance `7,000.00`.
6. Withdraw `1500` from `100001` → balance `5,500.00`.
7. Transfer `2000` from `100001` to `100002` → `3,500.00` and `10,500.00`.
8. Transaction History of `100001`.
9. Error scenario: create `100001` again (duplicate account number), withdraw more than
   the balance (insufficient funds), and deposit `0.001` (rejected — centavo precision).
10. Transaction History of `100002` as well: the transfer appears on both accounts.

## Author
- Name: Jen Otehlie Mina
- Qualification: Java Programming NCIII
- Batch: 13
- Training Center: Center for International Industries Competence Corp. (CIICC)
- Trainer: Mr. Michael Ampo
