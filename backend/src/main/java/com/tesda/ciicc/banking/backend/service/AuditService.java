package com.tesda.ciicc.banking.backend.service;

import com.tesda.ciicc.banking.backend.auth.CurrentUser;
import com.tesda.ciicc.banking.backend.model.AuditLog;
import com.tesda.ciicc.banking.backend.repository.AuditLogRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Writes the audit trail ({@code audit_log}). Rows are only ever inserted.
 *
 * <p>Two write modes, and the difference matters:</p>
 * <ul>
 *   <li>{@link #record} joins the caller's transaction, so a money movement
 *   and its audit entry commit together or not at all — an audit entry never
 *   describes an operation that did not happen.</li>
 *   <li>{@link #recordDeniedAccess} commits in its OWN transaction right away,
 *   because a denied attempt usually causes the surrounding transaction to
 *   roll back, and the denial is exactly what must stay visible.</li>
 * </ul>
 */
@Service
public class AuditService {

    public static final String LOGIN_SUCCESS = "LOGIN_SUCCESS";
    public static final String LOGIN_FAILURE = "LOGIN_FAILURE";
    public static final String LOGIN_RATE_LIMITED = "LOGIN_RATE_LIMITED";
    public static final String REGISTER = "REGISTER";
    public static final String REFRESH = "REFRESH";
    public static final String ACCESS_DENIED = "ACCESS_DENIED";
    public static final String ACCOUNT_CREATED = "ACCOUNT_CREATED";
    public static final String DEPOSIT = "DEPOSIT";
    public static final String WITHDRAW = "WITHDRAW";
    public static final String TRANSFER = "TRANSFER";

    private final AuditLogRepository auditLogs;

    public AuditService(AuditLogRepository auditLogs) {
        this.auditLogs = auditLogs;
    }

    @Transactional
    public void record(String username, String action, String accountNumber, String details) {
        auditLogs.save(new AuditLog(username, action, accountNumber, details, currentIpAddress()));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordDeniedAccess(CurrentUser caller, String accountNumber) {
        record(caller.username(), ACCESS_DENIED, accountNumber,
                "Caller role " + caller.role() + " does not own this account.");
    }

    /**
     * The caller's IP address, or null outside a web request (e.g. in plain
     * unit tests). Reads the current request instead of passing the address
     * through every service signature.
     */
    public static String currentIpAddress() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            return attributes.getRequest().getRemoteAddr();
        }
        return null;
    }
}
