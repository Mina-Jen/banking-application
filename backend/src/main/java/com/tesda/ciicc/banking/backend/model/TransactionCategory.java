package com.tesda.ciicc.banking.backend.model;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * The closed vocabulary a transaction can be filed under. Keeping it closed
 * is what makes an AI suggestion safe to persist: the model can only choose
 * one of these codes, never invent free text that later breaks reporting.
 */
public enum TransactionCategory {
    GROCERIES,
    DINING,
    TRANSPORT,
    UTILITIES,
    RENT,
    SHOPPING,
    HEALTH,
    ENTERTAINMENT,
    EDUCATION,
    SALARY,
    TRANSFER,
    FEES,
    OTHER;

    public static Optional<TransactionCategory> fromCode(String code) {
        if (code == null || code.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(valueOf(code.trim().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    /** The comma-separated list used in prompts and validation messages. */
    public static String allowedCodes() {
        return Arrays.stream(values()).map(Enum::name).collect(Collectors.joining(", "));
    }
}
