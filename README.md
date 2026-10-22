# Banking Application

A banking application built with **Java**, **MySQL (XAMPP)**, **JDBC**, and
**Spring Boot**.
Final project for TESDA - Java Programming NCIII.

The project started as a console application (Phase 0, `src/`) and is being
extended with a Spring Boot backend (`backend/`). Both are runnable.

## Features (Console)
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
- Spring Boot 3.5, Spring Data JPA, Flyway (backend)
- springdoc-openapi, Actuator, Testcontainers (backend)
- IntelliJ IDEA

## Project Structure

```
banking-application/
├── pom.xml                      Console app Maven build
├── backend/                     Spring Boot 3 backend
│   ├── pom.xml                  Backend Maven build
│   ├── mvnw / mvnw.cmd          Maven wrapper
│   └── src/
│       ├── main/java/com/tesda/ciicc/banking/backend/
│       │   ├── BankingBackendApplication.java
│       │   ├── model/           Account, Transaction, TransactionType, IdempotencyKey entities
│       │   ├── repository/      Spring Data JPA repositories
│       │   ├── service/         BankingService (business logic and validation)
│       │   ├── exception/       BankingException and its subclasses
│       │   └── web/             Controllers, DTOs, IdempotencyService, error advice
│       ├── main/resources/
│       │   ├── application.yml
│       │   └── db/migration/    V1__init.sql, V2__idempotency_keys.sql
│       └── test/java/...        Unit, MockMvc, MySQL integration, and migration tests
├── database/
│   ├── banking_db.sql           Frozen Phase 0 bootstrap script
│   └── migrations/              One-off data migrations from Phase 0
└── src/
    ├── main/java/com/tesda/ciicc/banking/
    │   ├── Main.java            Console menu
    │   ├── service/             BankingService (business logic and validation)
    │   ├── dao/                 AccountDAO, TransactionDAO (SQL only)
    │   ├── model/               Account, Transaction, TransactionType
    │   ├── exception/           BankingException and its subclasses
    │   └── util/                ConnectionProvider, DatabaseConnection
    └── test/
        ├── java/com/tesda/ciicc/banking/   Validation and H2 integration tests
        └── resources/schema-h2.sql         Test-only database schema
```

## Spring Boot Backend

`backend/` is a Spring Boot 3.5 Maven project. It provides the application
skeleton, database migration, Actuator health, OpenAPI documentation, the JPA
business layer ported from the console app, and the REST API below.

`BankingService` keeps the console app's money rules:

- Every balance change runs in one database transaction. Methods that change
  data use `@Transactional(rollbackFor = Exception.class)` because the domain
  exceptions are checked and Spring would otherwise commit on them.
- Deposit, withdraw, and transfer lock rows with `PESSIMISTIC_WRITE`
  (`SELECT ... FOR UPDATE`). Transfer locks two separate rows in account-number
  order, which prevents opposite transfers from deadlocking.
- A transfer is written as `TRANSFER_OUT` on the source and `TRANSFER_IN` on the
  destination. Same-account detection also compares persisted IDs, so spellings
  that differ only in case are rejected as the same MySQL row.
- Amounts stay `BigDecimal` end to end. The service rejects fractions of a
  centavo and values that would not fit `DECIMAL(15,2)`.

### Configuration

The datasource is configured entirely through environment variables:

| Variable | Default |
| --- | --- |
| `BANKING_DB_URL` | `jdbc:mariadb://localhost:3306/banking_db?createDatabaseIfNotExist=true` |
| `BANKING_DB_USER` | `root` |
| `BANKING_DB_PASSWORD` | *(empty)* |

The defaults match a local XAMPP installation. XAMPP runs MariaDB, so the local
default uses the MariaDB driver; this lets Flyway and Hibernate detect the
server version correctly. The MySQL driver (`mysql-connector-j`) is also on the
classpath. For a MySQL server, set `BANKING_DB_URL` to a `jdbc:mysql://...` URL.

### Database migrations

Flyway owns the schema and runs migrations from
`backend/src/main/resources/db/migration` at startup:

- `V1__init.sql` creates `accounts` and `transactions`, including the split
  `TRANSFER_OUT` / `TRANSFER_IN` transaction types.
- `V2__idempotency_keys.sql` creates `idempotency_keys`, which stores the
  responses replayed for repeated `Idempotency-Key` headers (see REST API).
- An empty or new database gets `V1` and `V2` applied. A database created by
  the Phase 0 console app is baselined at `V1` instead of re-running
  `CREATE TABLE`.
- Add future schema changes as `V3__...sql`, `V4__...sql`, and so on.
- `database/banking_db.sql` is frozen; do not add new schema changes there.

### REST API

All endpoints live under `/api` and speak JSON. Money is always a **string**
with two decimals (`"amount": "2000.00"`), never a JSON number. Swagger UI at
<http://localhost:8080/swagger-ui.html> documents every endpoint.

| Method | Path | Purpose |
| --- | --- | --- |
| POST | `/api/accounts` | Create an account; `201` with a `Location` header |
| GET | `/api/accounts/{accountNumber}` | Balance inquiry |
| POST | `/api/accounts/{accountNumber}/deposits` | Deposit (requires `Idempotency-Key`) |
| POST | `/api/accounts/{accountNumber}/withdrawals` | Withdraw (requires `Idempotency-Key`) |
| POST | `/api/transfers` | Transfer (requires `Idempotency-Key`) |
| GET | `/api/accounts/{accountNumber}/transactions?page=0&size=20` | Paginated history, oldest first |

Request bodies: `{"accountNumber": "100001", "accountName": "Juan Dela Cruz",
"initialDeposit": "5000.00"}` to create; `{"amount": "2000.00"}` for deposit
and withdraw; `{"sourceAccountNumber": "100001", "destinationAccountNumber":
"100002", "amount": "2000.00"}` to transfer. A transfer responds with the
source account's `TRANSFER_OUT` entry — the destination receives `TRANSFER_IN`.

### Errors

Every handled error uses one body shape:
`{"status": 422, "error": "Unprocessable Entity", "message": "..."}` with:

- `404` — account does not exist
- `409` — duplicate account number, or idempotency-key conflict
- `422` — rejected business rule (invalid amount, insufficient funds, same-account transfer, bad page/size)
- `400` — malformed request (missing header, unreadable JSON, non-numeric page)

### Idempotency-Key

Deposit, withdraw, and transfer require an `Idempotency-Key` header (1–100
characters). The first request claims the key and stores the JSON response in
`idempotency_keys` **in the same database transaction as the money movement**:

- Repeating the same key with the same body returns the stored response, with
  the same transaction reference, without moving money again — safe network
  retries.
- The same key with a different body, or on a different account, is a `409`.
- A failed operation rolls the claim back, so the key stays usable for a retry.
- Concurrent requests with the same key block on the unique index; after the
  winner commits, the others replay its stored response.

### Pagination

`GET /api/accounts/{accountNumber}/transactions` takes `page` (zero-based,
default 0) and `size` (1–100, default 20) and returns `page`, `size`,
`totalElements`, `totalPages`, and the transactions oldest first.

### Run

The backend needs a JDK 17+ and a running XAMPP MySQL/MariaDB server.

```bash
cd backend
./mvnw spring-boot:run
```

On Windows, use `mvnw.cmd spring-boot:run`. Once started:

- Health: <http://localhost:8080/actuator/health>
- OpenAPI JSON: <http://localhost:8080/v3/api-docs>
- Swagger UI: <http://localhost:8080/swagger-ui.html>

### Tests

```bash
cd backend
./mvnw test
```

- `BankingApiMockMvcTest` exercises the REST layer through MockMvc with mocked
  services: status mapping (200/201/400/404/409/422), DTO shapes, the required
  `Idempotency-Key` header, and pagination parameters.
- `IdempotencyServiceTest` covers the claim/replay/conflict rules of the
  idempotency handling: fresh execution stores the response, replays return it
  without re-running the work, a changed body or scope is a conflict, and a
  failed operation leaves the key retryable.
- `AccountTest` covers the entity's deposit, withdraw, insufficient-funds, and
  invalid-amount behavior without a database.
- `BankingServiceValidationTest` covers every input rule (amount precision and
  size, blank or over-long account number and name, same-account transfer). It
  uses stub repositories that fail if validation lets a call reach them.
- `BankingServiceMySQLTest` runs the service against a MySQL 8.4 Testcontainers
  database. It covers deposit, withdraw, transfer in both lock orders, history,
  duplicate and case-insensitive account numbers, rollback after a rejected
  operation, and a 50-thread concurrency test that checks money is conserved.
- `DatabaseMigrationTest` starts a MySQL 8.4 container, lets Flyway apply `V1`
  and `V2`, and checks the schema, split transfer enum, and `idempotency_keys`.

The MySQL tests are skipped automatically when Docker is unavailable, so
`./mvnw test` still passes without Docker. The service can also be exercised
against a local XAMPP MariaDB database with `./mvnw spring-boot:run`.

### XAMPP troubleshooting

If Flyway fails with `Column count of mysql.proc is wrong`, the MariaDB system
tables were created by an older server version. Run `mysql_upgrade` with an
account that can write to XAMPP's data directory (normally the `_mysql` user),
then start the backend again.

## Console Application (Phase 0)

The original JDBC console app remains available.

### Setup
1. Start MySQL in XAMPP.
2. Run the script in `database/banking_db.sql` using phpMyAdmin (SQL tab).
3. Database settings default to the XAMPP values (`root`, empty password,
   `jdbc:mysql://localhost:3306/banking_db`). To use different ones, set the
   `BANKING_DB_URL`, `BANKING_DB_USER` and `BANKING_DB_PASSWORD` environment
   variables instead of editing
   `src/main/java/com/tesda/ciicc/banking/util/DatabaseConnection.java` —
   that keeps real credentials out of the repository.
4. Run the application:
   - From a terminal: `mvn compile exec:java`
   - From IntelliJ: open the project's `pom.xml`; IntelliJ imports it as a Maven project,
     then run `Main`.

The first build downloads MySQL Connector/J from Maven Central, so an internet connection
is needed once. After that everything is cached in `~/.m2`.

### Tests
`mvn test` runs the console test suite in `src/test/java`. It does not need MySQL, so it
passes on a machine without it.

- **Model tests** (`AccountTest`) cover deposit, withdraw, insufficient funds, and invalid
  amounts on the `Account` model.
- **Validation tests** (`BankingServiceValidationTest`) cover the input rules: amount
  precision and size, blank or over-long account number and name, and transfer between
  identical accounts. They never open a database connection.
- **Integration tests** (`BankingServiceH2Test`) run the real service and DAOs against an
  in-memory H2 database. They cover the paths that write balances: deposit, withdraw,
  transfer (recorded on both accounts), duplicate accounts, and that a failed operation
  leaves balances unchanged.
- **Concurrency test** (`BankingServiceConcurrencyTest`) runs 50 threads of random deposits
  and transfers, then checks that money is conserved and no balance goes negative.

H2 locking behaves differently from MySQL, so the concurrency test is not a substitute for
checking row locking on the real server. The demonstration below uses real MySQL.

### Demonstration Script (Console)
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
