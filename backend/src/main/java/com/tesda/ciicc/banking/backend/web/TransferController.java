package com.tesda.ciicc.banking.backend.web;

import com.tesda.ciicc.banking.backend.auth.CurrentUser;
import com.tesda.ciicc.banking.backend.exception.BankingException;
import com.tesda.ciicc.banking.backend.service.BankingService;
import com.tesda.ciicc.banking.backend.web.dto.TransactionResponse;
import com.tesda.ciicc.banking.backend.web.dto.TransferRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;

@RestController
@RequestMapping("/api/transfers")
@Tag(name = "Transfers", description = "Moving money between two accounts in one transaction")
public class TransferController {

    private final BankingService banking;
    private final IdempotencyService idempotency;

    public TransferController(BankingService banking, IdempotencyService idempotency) {
        this.banking = banking;
        this.idempotency = idempotency;
    }

    @Operation(summary = "Transfer money between two accounts",
            description = "Records TRANSFER_OUT on the source and TRANSFER_IN on the destination. "
                    + "The caller must own the source account; the destination may belong to anyone. "
                    + "Requires an Idempotency-Key header; replaying the same key with the same body "
                    + "returns the stored response without transferring again.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Transfer recorded (or replayed); the response is the source account's TRANSFER_OUT entry"),
            @ApiResponse(responseCode = "400", description = "Missing or invalid Idempotency-Key"),
            @ApiResponse(responseCode = "401", description = "No valid access token"),
            @ApiResponse(responseCode = "404", description = "Source account is missing or not the caller's, or the destination does not exist — a CUSTOMER gets the same generic answer for all of them"),
            @ApiResponse(responseCode = "409", description = "Idempotency key already used with a different request"),
            @ApiResponse(responseCode = "422", description = "Invalid amount, same account on both sides, or insufficient funds"),
            @ApiResponse(responseCode = "429", description = "More than 60 requests in one minute for this user; Retry-After says how long to wait")
    })
    @PostMapping
    public TransactionResponse transfer(
            @AuthenticationPrincipal CurrentUser caller,
            @RequestHeader(IdempotencyService.HEADER_NAME) String idempotencyKey,
            @RequestBody TransferRequest request) throws BankingException {

        idempotency.requireValidKey(idempotencyKey);
        BigDecimal amount = Money.parse(request.amount(), "Transfer amount");
        // Scope includes the caller id, so two customers can never replay each
        // other's stored response with the same key and body.
        return idempotency.execute(idempotencyKey, "TRANSFER", String.valueOf(caller.id()), request,
                TransactionResponse.class,
                () -> TransactionResponse.from(banking.transfer(caller,
                        request.sourceAccountNumber(), request.destinationAccountNumber(), amount,
                        request.description())));
    }
}
