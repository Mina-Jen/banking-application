package com.tesda.ciicc.banking.backend.service;

import com.tesda.ciicc.banking.backend.auth.CurrentUser;
import com.tesda.ciicc.banking.backend.exception.AnomalyFlagAlreadyReviewedException;
import com.tesda.ciicc.banking.backend.exception.AnomalyFlagNotFoundException;
import com.tesda.ciicc.banking.backend.exception.BankingException;
import com.tesda.ciicc.banking.backend.exception.BankingValidationException;
import com.tesda.ciicc.banking.backend.model.AnomalyFlag;
import com.tesda.ciicc.banking.backend.model.AnomalyRule;
import com.tesda.ciicc.banking.backend.model.AnomalyStatus;
import com.tesda.ciicc.banking.backend.model.Transaction;
import com.tesda.ciicc.banking.backend.model.TransactionType;
import com.tesda.ciicc.banking.backend.repository.AnomalyFlagRepository;
import com.tesda.ciicc.banking.backend.repository.TransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.util.List;

/**
 * Rule-based anomaly detection (Phase 4). Both rules look only at money
 * leaving an account ({@link TransactionType#WITHDRAW} and
 * {@link TransactionType#TRANSFER_OUT}); deposits and incoming transfers are
 * never flagged.
 *
 * <p>Evaluation happens AFTER the money movement has been recorded, inside the
 * same database transaction: a flag and the transaction it describes commit
 * or roll back together, so a flag never points at a movement that did not
 * happen. The rules never reject, reverse or delay a movement — they only
 * leave a flag for an ADMIN to review. A failure while evaluating is logged
 * and swallowed for the same reason.</p>
 *
 * <p>Flags are visible only to ADMIN, through the review endpoints. A customer
 * is not blocked and is not told about a flag.</p>
 */
@Service
public class AnomalyService {

    private static final Logger log = LoggerFactory.getLogger(AnomalyService.class);

    private static final List<TransactionType> OUTBOUND =
            List.of(TransactionType.WITHDRAW, TransactionType.TRANSFER_OUT);

    /** How many prior outbound transactions form the spending baseline. */
    private static final int BASELINE_SIZE = 30;
    /** Fewer transactions than this is not a pattern yet. */
    private static final int MIN_BASELINE_SIZE = 3;
    /** Flag when an amount reaches three times the account's recent average... */
    private static final BigDecimal UNUSUAL_MULTIPLIER = new BigDecimal("3");
    /** ...and is at least this much, so small accounts do not flag small amounts. */
    private static final BigDecimal UNUSUAL_FLOOR = new BigDecimal("1000.00");

    /** More outbound transactions than this inside {@link #VELOCITY_WINDOW} is unusual. */
    private static final int VELOCITY_LIMIT = 5;
    private static final Duration VELOCITY_WINDOW = Duration.ofMinutes(10);

    /** Same cap as every other paginated endpoint. */
    private static final int MAX_PAGE_SIZE = 100;

    private final TransactionRepository transactions;
    private final AnomalyFlagRepository flags;
    private final AuditService audit;
    private final Clock clock;

    public AnomalyService(TransactionRepository transactions, AnomalyFlagRepository flags,
                          AuditService audit, Clock clock) {
        this.transactions = transactions;
        this.flags = flags;
        this.audit = audit;
        this.clock = clock;
    }

    // =====================================================================
    //  Detection
    // =====================================================================

    /**
     * Applies every rule to one recorded transaction. Called inside the
     * caller's transaction; a rule failure is logged, never thrown, because
     * anomaly detection must not turn a completed movement into an error.
     */
    public void evaluate(Transaction transaction) {
        try {
            if (!OUTBOUND.contains(transaction.getTransactionType())) {
                return;
            }
            evaluateUnusualAmount(transaction);
            evaluateVelocity(transaction);
        } catch (RuntimeException e) {
            log.warn("Could not evaluate anomaly rules for transaction {}: {}",
                    transaction.getTransactionReference(), e.toString());
        }
    }

    private void evaluateUnusualAmount(Transaction transaction) {
        List<Transaction> baseline = transactions
                .findByIdLessThanAndAccountNumberAndTransactionTypeInOrderByIdDesc(
                        transaction.getId(), transaction.getAccountNumber(), OUTBOUND,
                        PageRequest.of(0, BASELINE_SIZE));
        if (baseline.size() < MIN_BASELINE_SIZE) {
            return;
        }

        BigDecimal total = baseline.stream()
                .map(Transaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal average = total.divide(BigDecimal.valueOf(baseline.size()), 2, RoundingMode.HALF_UP);
        BigDecimal threshold = average.multiply(UNUSUAL_MULTIPLIER);

        if (transaction.getAmount().compareTo(threshold) < 0
                || transaction.getAmount().compareTo(UNUSUAL_FLOOR) < 0) {
            return;
        }

        flags.save(new AnomalyFlag(transaction, AnomalyRule.UNUSUAL_AMOUNT,
                transaction.getAmount().toPlainString() + " is at least "
                        + UNUSUAL_MULTIPLIER.toPlainString() + "x the " + average.toPlainString()
                        + " average of the last " + baseline.size() + " outbound transactions"
                        + " (flag floor " + UNUSUAL_FLOOR.toPlainString() + ")."));
    }

    private void evaluateVelocity(Transaction transaction) {
        Timestamp since = Timestamp.from(clock.instant().minus(VELOCITY_WINDOW));
        long recent = transactions.countByAccountNumberAndTransactionTypeInAndCreatedAtGreaterThanEqual(
                transaction.getAccountNumber(), OUTBOUND, since);
        if (recent <= VELOCITY_LIMIT) {
            return;
        }

        flags.save(new AnomalyFlag(transaction, AnomalyRule.VELOCITY,
                recent + " outbound transactions in the last " + VELOCITY_WINDOW.toMinutes()
                        + " minutes (limit " + VELOCITY_LIMIT + ")."));
    }

    // =====================================================================
    //  Review (ADMIN only)
    // =====================================================================

    /**
     * One page of flags, newest first. Without a {@code status} filter every
     * lifecycle state is listed; {@code OPEN} is the review queue.
     */
    @Transactional(readOnly = true)
    public Page<AnomalyFlag> listFlags(CurrentUser caller, String statusCode, int page, int size)
            throws BankingException {

        requireAdmin(caller);
        requirePage(page, size);

        Pageable pageable = PageRequest.of(page, size,
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
        if (statusCode == null || statusCode.isBlank()) {
            return flags.findAll(pageable);
        }

        AnomalyStatus status = AnomalyStatus.fromCode(statusCode).orElseThrow(() ->
                new BankingValidationException(
                        "Status must be one of: " + AnomalyStatus.allowedCodes() + "."));
        return flags.findByStatus(status, pageable);
    }

    /**
     * Closes one flag as REVIEWED or DISMISSED. A decision is one-way: a flag
     * that was already closed cannot be reviewed again, so the audit trail
     * keeps the first decision and who made it.
     */
    @Transactional(rollbackFor = Exception.class)
    public AnomalyFlag reviewFlag(CurrentUser caller, int flagId, String statusCode)
            throws BankingException {

        requireAdmin(caller);
        AnomalyStatus decision = requireReviewDecision(statusCode);

        AnomalyFlag flag = flags.findById(flagId)
                .orElseThrow(() -> new AnomalyFlagNotFoundException("Anomaly flag not found."));
        if (flag.getStatus() != AnomalyStatus.OPEN) {
            throw new AnomalyFlagAlreadyReviewedException(
                    "Anomaly flag " + flagId + " has already been reviewed.");
        }

        flag.resolve(decision, caller.username(), Timestamp.from(clock.instant()));
        audit.record(caller.username(), AuditService.ANOMALY_REVIEWED, flag.getAccountNumber(),
                "Flag " + flag.getId() + " (" + flag.getRule() + ") marked " + decision + ".");
        return flag;
    }

    // =====================================================================
    //  Validation
    // =====================================================================

    private static void requireAdmin(CurrentUser caller) {
        if (!caller.isAdmin()) {
            throw new AccessDeniedException("Only an ADMIN may access anomaly flags.");
        }
    }

    /** OPEN is where a flag starts, not a decision an admin can record. */
    private static AnomalyStatus requireReviewDecision(String statusCode)
            throws BankingValidationException {
        return AnomalyStatus.fromCode(statusCode)
                .filter(AnomalyStatus::isReviewDecision)
                .orElseThrow(() -> new BankingValidationException(
                        "Review status must be one of: " + AnomalyStatus.reviewableCodes() + "."));
    }

    private static void requirePage(int page, int size) throws BankingValidationException {
        if (page < 0) {
            throw new BankingValidationException("Page must be zero or greater.");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new BankingValidationException("Page size must be between 1 and " + MAX_PAGE_SIZE + ".");
        }
    }
}
