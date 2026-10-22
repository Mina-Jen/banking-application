package com.tesda.ciicc.banking.backend.web;

import com.tesda.ciicc.banking.backend.exception.BankingValidationException;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Request/response conversion for money: amounts arrive as decimal strings in
 * JSON and leave as fixed two-decimal strings.
 */
public final class Money {

    private Money() {
    }

    /**
     * Returns the parsed amount, or null for a missing value so the service's
     * own "must be greater than zero" rule produces the error.
     */
    public static BigDecimal parse(String raw, String label) throws BankingValidationException {
        if (raw == null) {
            return null;
        }
        try {
            String trimmed = raw.trim();
            if (trimmed.isEmpty()) {
                throw new NumberFormatException("blank amount");
            }
            return new BigDecimal(trimmed);
        } catch (NumberFormatException e) {
            throw new BankingValidationException(label + " must be a number.");
        }
    }

    /**
     * Every stored money value has at most two decimals (DECIMAL(15,2) and the
     * service reject anything smaller), so this never loses information.
     */
    public static String format(BigDecimal value) {
        return value.setScale(2, RoundingMode.UNNECESSARY).toPlainString();
    }
}
