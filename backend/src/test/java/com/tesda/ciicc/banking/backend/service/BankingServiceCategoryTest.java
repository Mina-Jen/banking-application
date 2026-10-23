package com.tesda.ciicc.banking.backend.service;

import com.tesda.ciicc.banking.backend.ai.CategorySuggestionClient;
import com.tesda.ciicc.banking.backend.auth.CurrentUser;
import com.tesda.ciicc.banking.backend.exception.AccountNotFoundException;
import com.tesda.ciicc.banking.backend.exception.BankingValidationException;
import com.tesda.ciicc.banking.backend.exception.TransactionNotFoundException;
import com.tesda.ciicc.banking.backend.model.Role;
import com.tesda.ciicc.banking.backend.model.Transaction;
import com.tesda.ciicc.banking.backend.model.TransactionCategory;
import com.tesda.ciicc.banking.backend.model.TransactionType;
import com.tesda.ciicc.banking.backend.repository.AccountOwnerRepository;
import com.tesda.ciicc.banking.backend.repository.AccountRepository;
import com.tesda.ciicc.banking.backend.repository.TransactionRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Category rules with the AI client replaced by a mock, which is what keeps
 * this test offline and deterministic: it asserts exactly what the bank sends
 * to the model, and that a suggestion is never trusted blindly.
 */
class BankingServiceCategoryTest {

    private static final CurrentUser CAROL = new CurrentUser(7, "carol", Role.CUSTOMER);

    private final AccountRepository accounts = mock(AccountRepository.class);
    private final TransactionRepository transactions = mock(TransactionRepository.class);
    private final AccountOwnerRepository accountOwners = mock(AccountOwnerRepository.class);
    private final AuditService audit = mock(AuditService.class);
    private final CategorySuggestionClient suggester = mock(CategorySuggestionClient.class);

    private final BankingService service =
            new BankingService(accounts, transactions, accountOwners, audit, suggester,
                    mock(AnomalyService.class));

    private static Transaction storedTransaction() {
        Transaction transaction = new Transaction();
        transaction.setId(42);
        transaction.setTransactionReference("TXN-cat001");
        transaction.setAccountNumber("100001");
        transaction.setTransactionType(TransactionType.WITHDRAW);
        transaction.setAmount(new BigDecimal("250.00"));
        transaction.setBalance(new BigDecimal("750.00"));
        return transaction;
    }

    private void carolOwnsTheAccount() {
        when(accountOwners.existsByUserIdAndAccountNumber(CAROL.id(), "100001")).thenReturn(true);
    }

    @Test
    void suggestionSendsTypeAmountAndDescriptionAndReturnsTheClientsAnswer() throws Exception {
        carolOwnsTheAccount();
        when(transactions.findByIdAndAccountNumber(42, "100001"))
                .thenReturn(Optional.of(storedTransaction()));
        when(suggester.suggestCategory(any())).thenReturn(TransactionCategory.DINING);

        TransactionCategory result =
                service.suggestCategory(CAROL, "100001", 42, "Jollibee lunch");

        assertEquals(TransactionCategory.DINING, result);
        verify(suggester).suggestCategory(new CategorySuggestionClient.CategorySuggestionInput(
                TransactionType.WITHDRAW, new BigDecimal("250.00"), "Jollibee lunch"));
    }

    @Test
    void aForeignAccountNeverReachesTheTransactionTableOrTheModel() {
        when(accountOwners.existsByUserIdAndAccountNumber(CAROL.id(), "100001")).thenReturn(false);

        assertThrows(AccountNotFoundException.class,
                () -> service.suggestCategory(CAROL, "100001", 42, "anything"));

        verifyNoInteractions(transactions, suggester);
        verify(audit).recordDeniedAccess(CAROL, "100001");
    }

    @Test
    void anUnknownTransactionIs404AndTheModelIsNotCalled() {
        carolOwnsTheAccount();
        when(transactions.findByIdAndAccountNumber(42, "100001")).thenReturn(Optional.empty());

        assertThrows(TransactionNotFoundException.class,
                () -> service.suggestCategory(CAROL, "100001", 42, null));

        verifyNoInteractions(suggester);
    }

    @Test
    void acceptingACategoryStoresItAndWritesAnAuditEntry() throws Exception {
        carolOwnsTheAccount();
        Transaction transaction = storedTransaction();
        when(transactions.findByIdAndAccountNumber(42, "100001"))
                .thenReturn(Optional.of(transaction));

        Transaction updated = service.setCategory(CAROL, "100001", 42, "groceries");

        assertEquals(TransactionCategory.GROCERIES, updated.getCategory());
        verify(audit).record(eq("carol"), eq(AuditService.CATEGORY_SET), eq("100001"),
                contains("TXN-cat001"));
    }

    @Test
    void anUnknownCategoryIsRejectedBeforeTheTransactionIsRead() {
        carolOwnsTheAccount();

        BankingValidationException e = assertThrows(BankingValidationException.class,
                () -> service.setCategory(CAROL, "100001", 42, "PIZZA"));

        assertEquals("Category must be one of: " + TransactionCategory.allowedCodes() + ".",
                e.getMessage());
        verifyNoInteractions(transactions);
    }

    @Test
    void suggestionDoesNotStoreAnythingOnItsOwn() throws Exception {
        carolOwnsTheAccount();
        Transaction transaction = storedTransaction();
        when(transactions.findByIdAndAccountNumber(42, "100001"))
                .thenReturn(Optional.of(transaction));
        when(suggester.suggestCategory(any())).thenReturn(TransactionCategory.DINING);

        service.suggestCategory(CAROL, "100001", 42, "Jollibee");

        assertNull(transaction.getCategory(), "a suggestion must not be saved until it is accepted");
        verify(transactions, org.mockito.Mockito.never()).save(any());
    }
}
