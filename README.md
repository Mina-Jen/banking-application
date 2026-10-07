# Banking Application

A simple console-based banking application built with **Java**, **JDBC**, and **MySQL (XAMPP)**.
Final project for TESDA / CIICC.

## Features
1. Create Account
2. Balance Inquiry
3. List Accounts
4. Deposit
5. Withdraw
6. Transfer (single database transaction with commit/rollback)
7. Transaction History

## Technologies
- Java (JDK 17+)
- MySQL / MariaDB via XAMPP
- MySQL Connector/J (JDBC driver)
- IntelliJ IDEA

## Project Structure

```
src/
├── model/        Account, Transaction
├── dao/          AccountDAO, TransactionDAO
├── service/      BankingService (business logic and validation)
├── exception/    Custom exceptions
├── util/         DatabaseConnection
└── Main.java     Console menu
lib/              MySQL JDBC driver
database/         SQL script
```

## Setup
1. Start MySQL in XAMPP.
2. Run the script in `database/banking_db.sql` using phpMyAdmin (SQL tab).
3. Open the project in IntelliJ and add `lib/mysql-connector-j-*.jar` as a library
   (right-click the jar → Add as Library).
4. If your MySQL root user has a password, update it in `util/DatabaseConnection.java`.
5. Run `Main.java`.

## Author
- Name: Jen Othlie Mina
- Qualification: Java Programming NCIII
- Batch: 13
- Training Center: Center for International Industries Competence Corp. (CIICC)
- Trainer: Mr. Michael Ampo
