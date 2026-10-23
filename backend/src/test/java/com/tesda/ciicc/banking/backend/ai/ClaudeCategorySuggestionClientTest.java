package com.tesda.ciicc.banking.backend.ai;

import com.tesda.ciicc.banking.backend.model.TransactionCategory;
import com.tesda.ciicc.banking.backend.model.TransactionType;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The client's two safety rules that do not need a network: only vocabulary
 * codes can come out of it, and a missing key is a clean refusal. The HTTP
 * call itself is replaced by a mock wherever a whole suggestion is exercised
 * (see BankingServiceCategoryTest and CategoryApiMockMvcTest).
 */
class ClaudeCategorySuggestionClientTest {

    @Test
    void findsTheCodeEvenWhenTheModelAddsWords() {
        assertEquals(TransactionCategory.DINING, ClaudeCategorySuggestionClient.parseCategory("Category: dining."));
        assertEquals(TransactionCategory.GROCERIES, ClaudeCategorySuggestionClient.parseCategory("`GROCERIES`"));
    }

    @Test
    void rejectsAReplyWithoutAVocabularyCode() {
        assertThrows(CategorySuggestionUnavailableException.class,
                () -> ClaudeCategorySuggestionClient.parseCategory("I cannot determine a category."));
        assertThrows(CategorySuggestionUnavailableException.class,
                () -> ClaudeCategorySuggestionClient.parseCategory("  "));
    }

    @Test
    void aMissingKeyIsRefusedBeforeAnyHttpCall() {
        ClaudeCategorySuggestionClient client = new ClaudeCategorySuggestionClient(
                RestClient.builder(), "  ", "claude-haiku-4-5", "http://127.0.0.1:1");

        CategorySuggestionUnavailableException e = assertThrows(CategorySuggestionUnavailableException.class,
                () -> client.suggestCategory(new CategorySuggestionClient.CategorySuggestionInput(
                        TransactionType.DEPOSIT, new BigDecimal("100.00"), "salary")));

        assertEquals(ClaudeCategorySuggestionClient.NOT_CONFIGURED_MESSAGE, e.getMessage());
    }
}
