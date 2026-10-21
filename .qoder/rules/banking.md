# Banking project rules
- Java 17+, Maven, MySQL. Future backend: Spring Boot 3.x with Flyway migrations.
- Money is ALWAYS BigDecimal in code and a string in JSON. Never float or double.
- Every operation that changes balances must run in one database transaction,
  lock accounts with SELECT ... FOR UPDATE, and lock in account-number order
  (see BankingService.transfer).
- Do not remove or weaken existing validation rules or exceptions.
- Never put passwords or API keys in code; use environment variables.
- Every change must keep `mvn test` passing and include tests for new behavior.
- Keep changes small. Do not refactor files unrelated to the task.