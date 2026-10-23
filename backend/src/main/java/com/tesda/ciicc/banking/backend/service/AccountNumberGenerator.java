package com.tesda.ciicc.banking.backend.service;

import java.security.SecureRandom;

/**
 * Builds the account numbers the bank assigns: ten random digits followed by a
 * Luhn check digit, so eleven digits in total ({@code accounts.account_number}
 * is VARCHAR(20)).
 *
 * <p>A customer used to type the number themselves, which put two things on
 * show: choosing a taken number produced a 409 that proved the number was real,
 * and a number that looks like a test account invited guesses. Assigning it on
 * the server removes both. The number is a label the customer is told, not a
 * secret and not the row identifier the database uses.</p>
 *
 * <p>{@code SecureRandom} rather than {@code Random} because the output must
 * not be predictable from neighbouring numbers: knowing the algorithm and one
 * number must not let anyone work out the next account opened.</p>
 */
final class AccountNumberGenerator {

    /** Digits drawn at random; the eleventh digit is the check digit. */
    private static final int PAYLOAD_DIGITS = 10;

    private static final SecureRandom RANDOM = new SecureRandom();

    private AccountNumberGenerator() {
    }

    static String generate() {
        StringBuilder payload = new StringBuilder(PAYLOAD_DIGITS);
        // Never a leading zero, so every number is exactly eleven digits long
        // and reads the same in a text message as it does in the database.
        payload.append(1 + RANDOM.nextInt(9));
        while (payload.length() < PAYLOAD_DIGITS) {
            payload.append(RANDOM.nextInt(10));
        }
        payload.append(Character.forDigit(checkDigitFor(payload.toString()), 10));
        return payload.toString();
    }

    /** The single digit that makes {@code payload + digit} a valid Luhn number. */
    private static int checkDigitFor(String payload) {
        int sum = 0;
        // The payload sits to the LEFT of the check digit, so its rightmost
        // digit is the one Luhn doubles.
        boolean doubleThis = true;
        for (int i = payload.length() - 1; i >= 0; i--) {
            sum += luhnTerm(payload.charAt(i) - '0', doubleThis);
            doubleThis = !doubleThis;
        }
        return (10 - sum % 10) % 10;
    }

    /**
     * True when the whole number satisfies Luhn, i.e. its weighted digit sum is
     * a multiple of ten. This is what catches a mistyped or transposed digit
     * when someone reads a number back; it says nothing about whether the
     * account exists, which only the table knows.
     */
    static boolean isValid(String accountNumber) {
        if (accountNumber == null || accountNumber.length() < 2) {
            return false;
        }
        int sum = 0;
        // The rightmost digit is the check digit, and Luhn never doubles it.
        boolean doubleThis = false;
        for (int i = accountNumber.length() - 1; i >= 0; i--) {
            char c = accountNumber.charAt(i);
            if (c < '0' || c > '9') {
                return false;
            }
            sum += luhnTerm(c - '0', doubleThis);
            doubleThis = !doubleThis;
        }
        return sum % 10 == 0;
    }

    /** A doubled digit above nine loses nine, which is the same as adding its own digits. */
    private static int luhnTerm(int digit, boolean doubleThis) {
        if (!doubleThis) {
            return digit;
        }
        int doubled = digit * 2;
        return doubled > 9 ? doubled - 9 : doubled;
    }
}
