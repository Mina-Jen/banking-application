package com.tesda.ciicc.banking.backend.ai;

/**
 * The assistant cannot answer: no API key is configured, the upstream call
 * failed, or the model produced nothing usable. Nothing is wrong with the
 * caller's request, so the API maps this to 503.
 */
public class AssistantUnavailableException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public AssistantUnavailableException(String message) {
        super(message);
    }
}
