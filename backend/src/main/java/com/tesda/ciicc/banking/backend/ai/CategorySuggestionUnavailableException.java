package com.tesda.ciicc.banking.backend.ai;

/**
 * The suggester could not answer: no API key is configured, the upstream call
 * failed, or the model returned a category outside the closed vocabulary.
 * Nothing is wrong with the caller's request, so the API maps this to 503.
 */
public class CategorySuggestionUnavailableException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public CategorySuggestionUnavailableException(String message) {
        super(message);
    }
}
