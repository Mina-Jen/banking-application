package com.tesda.ciicc.banking.backend.service;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The numbers the bank hands out must be well formed on their own, without a
 * database: eleven digits, never starting with zero, and a Luhn check digit so
 * a mistyped number is caught before anyone looks it up.
 */
class AccountNumberGeneratorTest {

    /**
     * The standard worked example: 7992739871 takes check digit 3, so
     * 79927398713 is valid and 79927398710 is not. Pinned here because the rest
     * of the class would still pass if generate() and isValid() shared the same
     * wrong arithmetic.
     */
    @Test
    void luhnMatchesTheKnownWorkedExample() {
        assertTrue(AccountNumberGenerator.isValid("79927398713"));
        assertFalse(AccountNumberGenerator.isValid("79927398710"));
    }

    @Test
    void everyGeneratedNumberIsElevenDigitsWithAValidCheckDigit() {
        for (int i = 0; i < 200; i++) {
            String number = AccountNumberGenerator.generate();

            assertEquals(11, number.length(), number);
            assertTrue(number.chars().allMatch(Character::isDigit), number);
            assertNotStartsWithZero(number);
            assertTrue(AccountNumberGenerator.isValid(number), number + " fails Luhn");
        }
    }

    /**
     * One digit wrong by one is the mistake a person actually makes when typing
     * a number back to a teller, and it is the case the check digit exists for.
     */
    @Test
    void aSingleWrongDigitIsCaught() {
        String number = AccountNumberGenerator.generate();

        // The check digit itself, moved by one so it must be a different digit.
        char check = number.charAt(number.length() - 1);
        String wrongCheck = number.substring(0, number.length() - 1)
                + (char) ('0' + (check - '0' + 1) % 10);
        assertFalse(AccountNumberGenerator.isValid(wrongCheck));

        // The leftmost payload digit, also moved by one (never by five, which
        // Luhn can miss in a doubled position).
        char first = number.charAt(0);
        char nudged = first == '9' ? '8' : (char) (first + 1);
        String wrongPayload = nudged + number.substring(1);
        assertFalse(AccountNumberGenerator.isValid(wrongPayload));
    }

    @Test
    void rejectsAnythingThatIsNotADigitsOnlyNumber() {
        assertFalse(AccountNumberGenerator.isValid(null));
        assertFalse(AccountNumberGenerator.isValid(""));
        assertFalse(AccountNumberGenerator.isValid("7"));      // no room for a check digit
        assertFalse(AccountNumberGenerator.isValid("7992739871x"));
    }

    @Test
    void drawsDoNotRepeatInOneRun() {
        Set<String> drawn = new HashSet<>();
        for (int i = 0; i < 5000; i++) {
            assertTrue(drawn.add(AccountNumberGenerator.generate()), "the same number was drawn twice");
        }
        assertEquals(5000, drawn.size());
    }

    private static void assertNotStartsWithZero(String number) {
        assertFalse(number.startsWith("0"), "leading zero makes the number shorter when read back: " + number);
    }
}
