package com.tesda.ciicc.banking.backend.ai;

import com.tesda.ciicc.banking.backend.model.TransactionCategory;
import com.tesda.ciicc.banking.backend.model.TransactionType;

import java.math.BigDecimal;

/**
 * Suggests a category for one transaction. The implementation may call an
 * external model, so tests replace this interface with a mock and the rest of
 * the application never depends on a particular AI provider.
 */
public interface CategorySuggestionClient {

    TransactionCategory suggestCategory(CategorySuggestionInput input)
            throws CategorySuggestionUnavailableException;

    /**
     * The only transaction facts sent to the model. Account numbers and
     * references stay in the bank: they are not needed to pick a category and
     * would identify the customer to a third party.
     */
    record CategorySuggestionInput(TransactionType type, BigDecimal amount, String description) {
    }
}
