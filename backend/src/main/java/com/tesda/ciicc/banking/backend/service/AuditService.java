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
    public static final String CATEGORY_SET = "CATEGORY_SET";
    public static final String ANOMALY_REVIEWED = "ANOMALY_REVIEWED";

    /** audit_log.username is VARCHAR(50); see V4__audit_log.sql. */
    private static final int MAX_USERNAME_LENGTH = 50;

    private final AuditLogRepository auditLogs;

    public AuditService(AuditLogRepository auditLogs) {
        this.auditLogs = auditLogs;
    }

    @Transactional
    public void record(String username, String action, String accountNumber, String details) {
        auditLogs.save(new AuditLog(clipUsername(username), action, accountNumber, details, currentIpAddress()));
    }

    /**
     * A login attempt arrives with a username nobody controls the length of, and
     * its failure row is written BEFORE the client is answered. Letting that
     * INSERT overflow VARCHAR(50) would lose the row and turn the 401 into a
     * 500, so the value is cut to the column instead. No username that can
     * actually exist is longer than 50 characters, so real entries are untouched.
     */
    private static String clipUsername(String username) {
        if (username == null || username.length() <= MAX_USERNAME_LENGTH) {
            return username;
        }
        return username.substring(0, MAX_USERNAME_LENGTH);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordDeniedAccess(CurrentUser caller, String accountNumber) {
        record(caller.username(), ACCESS_DENIED, accountNumber,
                "Caller role " + caller.role() + " does not own this account.");
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordDeniedMoneyMovement(CurrentUser caller, String accountNumber, String operation) {
        record(caller.username(), ACCESS_DENIED, accountNumber,
                "Caller role " + caller.role() + " may not " + operation + "; only the account holder moves money.");
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
