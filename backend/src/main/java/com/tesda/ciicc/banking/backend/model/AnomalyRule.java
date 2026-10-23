package com.tesda.ciicc.banking.backend.model;

/**
 * The rule that produced an anomaly flag. Both rules inspect outbound money
 * movement only (WITHDRAW and TRANSFER_OUT).
 */
public enum AnomalyRule {
    /** The amount is far above what this account normally sends out. */
    UNUSUAL_AMOUNT,
    /** Several outbound transactions happened inside a short time window. */
    VELOCITY
}
