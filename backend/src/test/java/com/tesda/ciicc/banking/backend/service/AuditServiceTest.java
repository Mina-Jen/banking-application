package com.tesda.ciicc.banking.backend.service;

import com.tesda.ciicc.banking.backend.model.AuditLog;
import com.tesda.ciicc.banking.backend.repository.AuditLogRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * The audit trail records login attempts before their outcome is known, so the
 * username it stores is attacker-controlled. {@code audit_log.username} is
 * VARCHAR(50), and a row that overflows it is the whole failure being lost.
 */
class AuditServiceTest {

    private final AuditLogRepository logs = mock(AuditLogRepository.class);
    private final AuditService audit = new AuditService(logs);

    private AuditLog onlySavedRow() {
        ArgumentCaptor<AuditLog> saved = ArgumentCaptor.forClass(AuditLog.class);
        verify(logs).save(saved.capture());
        return saved.getValue();
    }

    @Test
    void anOverlongUsernameIsCutToTheColumnInsteadOfFailingTheInsert() {
        audit.record("a".repeat(60), AuditService.LOGIN_FAILURE, null, "Invalid username or password");

        AuditLog row = onlySavedRow();
        assertEquals("a".repeat(50), row.getUsername());
        assertEquals(AuditService.LOGIN_FAILURE, row.getAction());
        assertEquals("Invalid username or password", row.getDetails());
    }

    @Test
    void aUsernameThatFitsIsStoredUntouched() {
        audit.record("alice", AuditService.LOGIN_SUCCESS, null, "Logged in");

        assertEquals("alice", onlySavedRow().getUsername());
    }

    @Test
    void aUsernameOfExactlyTheColumnLengthIsNotCut() {
        audit.record("b".repeat(50), AuditService.LOGIN_FAILURE, null, "Invalid username or password");

        assertEquals("b".repeat(50), onlySavedRow().getUsername());
    }

    @Test
    void anUnknownCallerStaysNull() {
        audit.record(null, AuditService.REFRESH, null, "Issued a new token pair");

        assertNull(onlySavedRow().getUsername());
    }
}
