package com.tesda.ciicc.banking.backend.web;

import com.tesda.ciicc.banking.backend.exception.BankingException;
import com.tesda.ciicc.banking.backend.model.Account;
import com.tesda.ciicc.banking.backend.service.BankingService;
import com.tesda.ciicc.banking.backend.web.dto.AccountResponse;
import com.tesda.ciicc.banking.backend.web.dto.AmountRequest;
import com.tesda.ciicc.banking.backend.web.dto.CreateAccountRequest;
import com.tesda.ciicc.banking.backend.web.dto.TransactionPageResponse;
import com.tesda.ciicc.banking.backend.web.dto.TransactionResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.net.URI;

@RestController
@RequestMapping("/api/accounts")
@Tag(name = "Accounts", description = "Account management and money movement")
public class AccountController {

    private final BankingService banking;
    private final IdempotencyService idempotency;

    public AccountController(BankingService banking, IdempotencyService idempotency) {
        this.banking = banking;
        this.idempotency = idempotency;
    }

    @Operation(summary = "Create an account",
            description = "A positive initial deposit is recorded as the account's first transaction.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Account created"),
            @ApiResponse(responseCode = "409", description = "Account number already exists"),
            @ApiResponse(responseCode = "422", description = "Invalid account number, name, or initial deposit")
    })
    @PostMapping
    public ResponseEntity<AccountResponse> createAccount(@RequestBody CreateAccountRequest request)
            throws BankingException {

        BigDecimal initialDeposit = Money.parse(request.initialDeposit(), "Initial deposit");
        Account account = banking.createAccount(
                request.accountNumber(), request.accountName(), initialDeposit);
        return ResponseEntity
                .created(URI.create("/api/accounts/" + account.getAccountNumber()))
                .body(AccountResponse.from(account));
    }

    @Operation(summary = "Get one account by account number")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Account found"),
            @ApiResponse(responseCode = "404", description = "Account does not exist"),
            @ApiResponse(responseCode = "422", description = "Invalid account number")
    })
    @GetMapping("/{accountNumber}")
    public AccountResponse getAccount(@PathVariable String accountNumber) throws BankingException {
        return AccountResponse.from(banking.getAccount(accountNumber));
    }

    @Operation(summary = "Deposit into an account",
            description = "Requires an Idempotency-Key header. Replaying the same key with the same body "
                    + "returns the stored response without depositing again.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Deposit recorded (or replayed)"),
            @ApiResponse(responseCode = "400", description = "Missing or invalid Idempotency-Key"),
            @ApiResponse(responseCode = "404", description = "Account does not exist"),
            @ApiResponse(responseCode = "409", description = "Idempotency key already used with a different request"),
            @ApiResponse(responseCode = "422", description = "Invalid amount")
    })
    @PostMapping("/{accountNumber}/deposits")
    public TransactionResponse deposit(
            @PathVariable String accountNumber,
            @RequestHeader(IdempotencyService.HEADER_NAME) String idempotencyKey,
            @RequestBody AmountRequest request) throws BankingException {

        idempotency.requireValidKey(idempotencyKey);
        BigDecimal amount = Money.parse(request.amount(), "Deposit amount");
        return idempotency.execute(idempotencyKey, "DEPOSIT", accountNumber, request,
                TransactionResponse.class,
                () -> TransactionResponse.from(banking.deposit(accountNumber, amount)));
    }
    @Operation(summary = "Withdraw from an account",
            description = "Requires an Idempotency-Key header, same replay rules as deposit.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Withdrawal recorded (or replayed)"),
            @ApiResponse(responseCode = "400", description = "Missing or invalid Idempotency-Key"),
            @ApiResponse(responseCode = "404", description = "Account does not exist"),
            @ApiResponse(responseCode = "409", description = "Idempotency key already used with a different request"),
            @ApiResponse(responseCode = "422", description = "Invalid amount or insufficient funds")
    })
    @PostMapping("/{accountNumber}/withdrawals")
    public TransactionResponse withdraw(
            @PathVariable String accountNumber,
            @RequestHeader(IdempotencyService.HEADER_NAME) String idempotencyKey,
            @RequestBody AmountRequest request) throws BankingException {

        idempotency.requireValidKey(idempotencyKey);
        BigDecimal amount = Money.parse(request.amount(), "Withdrawal amount");
        return idempotency.execute(idempotencyKey, "WITHDRAW", accountNumber, request,
                TransactionResponse.class,
                () -> TransactionResponse.from(banking.withdraw(accountNumber, amount)));
    }

    @Operation(summary = "Transaction history of an account, oldest first")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Page of transactions"),
            @ApiResponse(responseCode = "404", description = "Account does not exist"),
            @ApiResponse(responseCode = "422", description = "Invalid page or size")
    })
    @GetMapping("/{accountNumber}/transactions")
    public TransactionPageResponse transactionHistory(
            @PathVariable String accountNumber,

            @Parameter(description = "Zero-based page number", example = "0")
            @RequestParam(defaultValue = "0") int page,

            @Parameter(description = "Page size, 1 to 100", example = "20")
            @RequestParam(defaultValue = "20") int size) throws BankingException {

        return TransactionPageResponse.from(banking.getTransactionHistoryPage(accountNumber, page, size));
    }
}
