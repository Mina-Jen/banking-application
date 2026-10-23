package com.tesda.ciicc.banking.backend.service;

import com.tesda.ciicc.banking.backend.auth.CurrentUser;
import com.tesda.ciicc.banking.backend.exception.AnomalyFlagAlreadyReviewedException;
import com.tesda.ciicc.banking.backend.exception.AnomalyFlagNotFoundException;
import com.tesda.ciicc.banking.backend.exception.BankingValidationException;
import com.tesda.ciicc.banking.backend.model.AnomalyFlag;
import com.tesda.ciicc.banking.backend.model.AnomalyRule;
import com.tesda.ciicc.banking.backend.model.AnomalyStatus;
import com.tesda.ciicc.banking.backend.model.Role;
import com.tesda.ciicc.banking.backend.model.Transaction;
import com.tesda.ciicc.banking.backend.model.TransactionType;
import com.tesda.ciicc.banking.backend.repository.AnomalyFlagRepository;
import com.tesda.ciicc.banking.backend.repository.TransactionRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * The two anomaly rules and the ADMIN review flow, with repositories mocked so
 * each threshold can be exercised exactly at its boundary.
 */
class AnomalyServiceTest {

    private static final CurrentUser ADMIN = new CurrentUser(1, "admin", Role.ADMIN);
    private static final CurrentUser CAROL = new CurrentUser(7, "carol", Role.CUSTOMER);
    private static final Instant NOW = Instant.parse("2026-10-22T12:00:00Z");
    private static final List<TransactionType> OUTBOUND =
            List.of(TransactionType.WITHDRAW, TransactionType.TRANSFER_OUT);

    private final TransactionRepository transactions = mock(TransactionRepository.class);
    private final AnomalyFlagRepository flags = mock(AnomalyFlagRepository.class);
    private final AuditService audit = mock(AuditService.class);

    private final AnomalyService service = new AnomalyService(transactions, flags, audit,
            Clock.fixed(NOW, ZoneOffset.UTC));

    private static Transaction transaction(int id, TransactionType type, String amount) {
        Transaction transaction = new Transaction();
        transaction.setId(id);
        transaction.setTransactionReference("TXN-" + id);
        transaction.setAccountNumber("100001");
        transaction.setTransactionType(type);
        transaction.setAmount(new BigDecimal(amount));
        transaction.setBalance(new BigDecimal("10000.00"));
        return transaction;
    }

    private void baselineOf(String... amounts) {
        List<Transaction> prior = java.util.Arrays.stream(amounts)
                .map(amount -> transaction(0, TransactionType.WITHDRAW, amount))
                .toList();
        when(transactions.findByIdLessThanAndAccountNumberAndTransactionTypeInOrderByIdDesc(
                anyInt(), eq("100001"), eq(OUTBOUND), any(Pageable.class)))
                .thenReturn(prior);
    }

    private void recentOutboundCount(long count) {
        when(transactions.countByAccountNumberAndTransactionTypeInAndCreatedAtGreaterThanEqual(
                eq("100001"), eq(OUTBOUND), any(Timestamp.class)))
                .thenReturn(count);
    }

    private AnomalyFlag savedFlag() {
        ArgumentCaptor<AnomalyFlag> captor = ArgumentCaptor.forClass(AnomalyFlag.class);
        verify(flags).save(captor.capture());
        return captor.getValue();
    }

    // =====================================================================
    //  Which transactions are inspected
    // =====================================================================

    @Test
    void depositsAndIncomingTransfersAreNeverEvaluated() {
        service.evaluate(transaction(10, TransactionType.DEPOSIT, "5000.00"));
        service.evaluate(transaction(11, TransactionType.TRANSFER_IN, "5000.00"));

        verifyNoInteractions(transactions, flags);
    }

    // =====================================================================
    //  Unusual amount
    // =====================================================================

    @Test
    void anAmountAtThreeTimesTheRecentAverageAndAtTheFloorIsFlagged() {
        baselineOf("100.00", "100.00", "100.00");

        service.evaluate(transaction(4, TransactionType.WITHDRAW, "1000.00"));

        AnomalyFlag flag = savedFlag();
        assertThat(flag.getRule()).isEqualTo(AnomalyRule.UNUSUAL_AMOUNT);
        assertThat(flag.getTransactionId()).isEqualTo(4);
        assertThat(flag.getTransactionReference()).isEqualTo("TXN-4");
        assertThat(flag.getAccountNumber()).isEqualTo("100001");
        assertThat(flag.getTransactionType()).isEqualTo(TransactionType.WITHDRAW);
        assertThat(flag.getAmount()).isEqualByComparingTo("1000.00");
        assertThat(flag.getStatus()).isEqualTo(AnomalyStatus.OPEN);
        assertThat(flag.getDetails())
                .contains("1000.00", "3x", "100.00", "3 outbound transactions");
    }

    @Test
    void anAmountBelowTheFloorIsNotFlaggedEvenWhenItIsAboveTheAverage() {
        baselineOf("10.00", "10.00", "10.00");
        recentOutboundCount(4);

        service.evaluate(transaction(5, TransactionType.WITHDRAW, "100.00"));

        verifyNoInteractions(flags);
    }

    @Test
    void anAmountBelowThreeTimesTheAverageIsNotFlagged() {
        baselineOf("400.00", "400.00", "400.00");
        recentOutboundCount(4);

        service.evaluate(transaction(5, TransactionType.WITHDRAW, "1100.00"));

        verifyNoInteractions(flags);
    }

    @Test
    void fewerThanThreePriorOutboundTransactionsIsNotAPatternYet() {
        baselineOf("1.00", "1.00");
        recentOutboundCount(3);

        service.evaluate(transaction(4, TransactionType.WITHDRAW, "5000.00"));

        verifyNoInteractions(flags);
    }

    @Test
    void transfersOutAreComparedAgainstTheSameOutboundBaseline() {
        baselineOf("100.00", "100.00", "100.00");

        service.evaluate(transaction(4, TransactionType.TRANSFER_OUT, "1000.00"));

        assertThat(savedFlag().getRule()).isEqualTo(AnomalyRule.UNUSUAL_AMOUNT);
    }

    // =====================================================================
    //  Velocity
    // =====================================================================

    @Test
    void theSixthOutboundTransactionInsideTenMinutesIsFlagged() {
        baselineOf();
        recentOutboundCount(6);

        service.evaluate(transaction(10, TransactionType.WITHDRAW, "50.00"));

        AnomalyFlag flag = savedFlag();
        assertThat(flag.getRule()).isEqualTo(AnomalyRule.VELOCITY);
        assertThat(flag.getDetails()).contains("6 outbound transactions", "10 minutes", "limit 5");

        ArgumentCaptor<Timestamp> since = ArgumentCaptor.forClass(Timestamp.class);
        verify(transactions).countByAccountNumberAndTransactionTypeInAndCreatedAtGreaterThanEqual(
                eq("100001"), eq(OUTBOUND), since.capture());
        assertThat(since.getValue().toInstant()).isEqualTo(NOW.minusSeconds(600));
    }

    @Test
    void fiveOutboundTransactionsInsideTenMinutesAreNotFlagged() {
        baselineOf();
        recentOutboundCount(5);

        service.evaluate(transaction(9, TransactionType.WITHDRAW, "50.00"));

        verifyNoInteractions(flags);
    }

    @Test
    void bothRulesCanFlagTheSameTransaction() {
        baselineOf("100.00", "100.00", "100.00");
        recentOutboundCount(6);

        service.evaluate(transaction(10, TransactionType.WITHDRAW, "1000.00"));

        ArgumentCaptor<AnomalyFlag> captor = ArgumentCaptor.forClass(AnomalyFlag.class);
        verify(flags, times(2)).save(captor.capture());
        assertThat(captor.getAllValues()).extracting(AnomalyFlag::getRule)
                .containsExactlyInAnyOrder(AnomalyRule.UNUSUAL_AMOUNT, AnomalyRule.VELOCITY);
    }

    @Test
    void aBrokenRuleNeverThrowsIntoTheMoneyMovement() {
        when(transactions.findByIdLessThanAndAccountNumberAndTransactionTypeInOrderByIdDesc(
                anyInt(), eq("100001"), eq(OUTBOUND), any(Pageable.class)))
                .thenThrow(new IllegalStateException("database down"));

        assertDoesNotThrow(() -> service.evaluate(transaction(4, TransactionType.WITHDRAW, "5000.00")));
        verifyNoInteractions(flags);
    }

    // =====================================================================
    //  Admin listing
    // =====================================================================

    @Test
    void aCustomerCannotListFlags() {
        assertThrows(AccessDeniedException.class, () -> service.listFlags(CAROL, null, 0, 20));
        verifyNoInteractions(flags);
    }

    @Test
    void adminListsTheRequestedStatusNewestFirst() throws Exception {
        AnomalyFlag open = openFlag();
        when(flags.findByStatus(eq(AnomalyStatus.OPEN), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(open)));

        Page<AnomalyFlag> result = service.listFlags(ADMIN, "open", 0, 20);

        assertThat(result.getContent()).containsExactly(open);
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(flags).findByStatus(eq(AnomalyStatus.OPEN), pageable.capture());

        Sort.Order newestFirst = pageable.getValue().getSort().getOrderFor("createdAt");
        assertThat(newestFirst).isNotNull();
        assertThat(newestFirst.getDirection()).isEqualTo(Sort.Direction.DESC);
        assertThat(pageable.getValue().getPageSize()).isEqualTo(20);
    }

    @Test
    void adminWithoutAStatusFilterListsEveryState() throws Exception {
        when(flags.findAll(any(Pageable.class))).thenReturn(Page.empty());

        service.listFlags(ADMIN, null, 0, 20);

        verify(flags).findAll(any(Pageable.class));
    }

    @Test
    void anUnknownStatusFilterIsRejectedBeforeTheQuery() {
        assertThrows(BankingValidationException.class,
                () -> service.listFlags(ADMIN, "PIZZA", 0, 20));
        verifyNoInteractions(flags);
    }

    @Test
    void anInvalidPageIsRejected() {
        assertThrows(BankingValidationException.class,
                () -> service.listFlags(ADMIN, null, -1, 20));
        verifyNoInteractions(flags);
    }

    // =====================================================================
    //  Admin review
    // =====================================================================

    @Test
    void anAdminClosesAnOpenFlagAndItIsAudited() throws Exception {
        AnomalyFlag flag = openFlag();
        when(flags.findById(12)).thenReturn(Optional.of(flag));

        AnomalyFlag result = service.reviewFlag(ADMIN, 12, "reviewed");

        assertThat(result.getStatus()).isEqualTo(AnomalyStatus.REVIEWED);
        assertThat(result.getReviewedBy()).isEqualTo("admin");
        assertThat(result.getReviewedAt()).isEqualTo(Timestamp.from(NOW));
        verify(audit).record(eq("admin"), eq(AuditService.ANOMALY_REVIEWED), eq("100001"),
                contains("12"));
    }

    @Test
    void aFlagCanBeDismissed() throws Exception {
        AnomalyFlag flag = openFlag();
        when(flags.findById(12)).thenReturn(Optional.of(flag));

        AnomalyFlag result = service.reviewFlag(ADMIN, 12, "dismissed");

        assertThat(result.getStatus()).isEqualTo(AnomalyStatus.DISMISSED);
    }

    @Test
    void aCustomerCannotCloseAFlag() {
        assertThrows(AccessDeniedException.class,
                () -> service.reviewFlag(CAROL, 12, "REVIEWED"));
        verifyNoInteractions(flags, audit);
    }

    @Test
    void anUnknownFlagIs404() {
        when(flags.findById(99)).thenReturn(Optional.empty());

        assertThrows(AnomalyFlagNotFoundException.class,
                () -> service.reviewFlag(ADMIN, 99, "DISMISSED"));
        verifyNoInteractions(audit);
    }

    @Test
    void aClosedFlagCannotBeReviewedAgain() {
        AnomalyFlag flag = openFlag();
        flag.resolve(AnomalyStatus.DISMISSED, "admin", Timestamp.from(NOW));
        when(flags.findById(12)).thenReturn(Optional.of(flag));

        assertThrows(AnomalyFlagAlreadyReviewedException.class,
                () -> service.reviewFlag(ADMIN, 12, "REVIEWED"));
        verifyNoInteractions(audit);
    }

    @Test
    void openIsNotAReviewDecision() {
        assertThrows(BankingValidationException.class,
                () -> service.reviewFlag(ADMIN, 12, "OPEN"));
        verifyNoInteractions(flags);
    }

    private static AnomalyFlag openFlag() {
        AnomalyFlag flag = new AnomalyFlag(
                transaction(42, TransactionType.WITHDRAW, "5000.00"),
                AnomalyRule.UNUSUAL_AMOUNT,
                "5000.00 is far above the account's recent outbound average.");
        ReflectionTestUtils.setField(flag, "id", 12);
        flag.setCreatedAt(Timestamp.from(NOW));
        return flag;
    }
}
