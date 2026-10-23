package com.tesda.ciicc.banking.backend.model;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Lifecycle of an anomaly flag. An ADMIN review closes a flag either as
 * REVIEWED (looks suspicious but was handled) or DISMISSED (nothing wrong).
 * A closed flag is not reopened.
 */
public enum AnomalyStatus {
    OPEN,
    REVIEWED,
    DISMISSED;

    public static Optional<AnomalyStatus> fromCode(String code) {
        if (code == null || code.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(valueOf(code.trim().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    /** The only targets a review may set; OPEN is the starting state, not a decision. */
    public boolean isReviewDecision() {
        return this != OPEN;
    }

    /** For the listing endpoint's filter validation message. */
    public static String allowedCodes() {
        return Arrays.stream(values()).map(Enum::name).collect(Collectors.joining(", "));
    }

    /** The two decisions a review may record. */
    public static String reviewableCodes() {
        return Arrays.stream(values())
                .filter(AnomalyStatus::isReviewDecision)
                .map(Enum::name)
                .collect(Collectors.joining(", "));
    }
}
