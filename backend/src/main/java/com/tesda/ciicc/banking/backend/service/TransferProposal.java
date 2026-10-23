package com.tesda.ciicc.banking.backend.service;

/**
 * A transfer the assistant described but did NOT execute. Confirming it is the
 * customer's job, through the ordinary transfer endpoint.
 */
public record TransferProposal(String sourceAccountNumber, String destinationAccountNumber,
                               String amount, String status, String note) {
}
