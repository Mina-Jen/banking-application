package com.tesda.ciicc.banking.backend;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(properties = "JWT_SECRET=migration-test-secret-0123456789abcdef")
@Testcontainers(disabledWithoutDocker = true)
class DatabaseMigrationTest {

    @Container
    @ServiceConnection
    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withUrlParam("sslMode", "DISABLED")
            .withUrlParam("allowPublicKeyRetrieval", "true");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void flywayCreatesTheApplicationSchema() {
        List<String> tables = jdbcTemplate.queryForList("SHOW TABLES", String.class);

        assertThat(tables)
                .contains("accounts", "transactions", "idempotency_keys",
                        "users", "account_owners", "audit_log", "anomaly_flags",
                        "flyway_schema_history");
    }

    @Test
    void anomalyFlagsCarryTheRuleDetailsAndReviewColumns() {
        var column = jdbcTemplate.queryForMap("""
                SELECT IS_NULLABLE, DATA_TYPE, CHARACTER_MAXIMUM_LENGTH
                FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME = 'anomaly_flags'
                  AND COLUMN_NAME = 'details'
                """);

        assertThat(column)
                .containsEntry("IS_NULLABLE", "NO")
                .containsEntry("DATA_TYPE", "varchar")
                .containsEntry("CHARACTER_MAXIMUM_LENGTH", 500L);
    }

    @Test
    void aTransactionCanOnlyBeFlaggedOncePerRule() {
        List<String> uniqueIndexes = jdbcTemplate.queryForList("""
                SELECT INDEX_NAME
                FROM information_schema.STATISTICS
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME = 'anomaly_flags'
                  AND NON_UNIQUE = 0
                """, String.class);

        assertThat(uniqueIndexes).contains("uq_anomaly_flags_transaction_rule");
    }

    @Test
    void transactionsCarryANullableCategoryColumn() {
        var column = jdbcTemplate.queryForMap("""
                SELECT IS_NULLABLE, DATA_TYPE, CHARACTER_MAXIMUM_LENGTH
                FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME = 'transactions'
                  AND COLUMN_NAME = 'category'
                """);

        assertThat(column)
                .containsEntry("IS_NULLABLE", "YES")
                .containsEntry("DATA_TYPE", "varchar")
                .containsEntry("CHARACTER_MAXIMUM_LENGTH", 40L);
    }

    @Test
    void transactionsCarryANullableDescriptionColumn() {
        var column = jdbcTemplate.queryForMap("""
                SELECT IS_NULLABLE, DATA_TYPE, CHARACTER_MAXIMUM_LENGTH
                FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME = 'transactions'
                  AND COLUMN_NAME = 'description'
                """);

        assertThat(column)
                .containsEntry("IS_NULLABLE", "YES")
                .containsEntry("DATA_TYPE", "varchar")
                .containsEntry("CHARACTER_MAXIMUM_LENGTH", 255L);
    }

    @Test
    void transactionTypeStoresTheSplitTransferValues() {
        String columnType = jdbcTemplate.queryForObject("""
                SELECT COLUMN_TYPE
                FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME = 'transactions'
                  AND COLUMN_NAME = 'transaction_type'
                """, String.class);

        assertThat(columnType)
                .contains("TRANSFER_OUT", "TRANSFER_IN")
                .doesNotContain("'TRANSFER'");
    }
}
