package com.tesda.ciicc.banking.backend.web;

import com.tesda.ciicc.banking.backend.auth.CurrentUser;
import com.tesda.ciicc.banking.backend.exception.BankingException;
import com.tesda.ciicc.banking.backend.model.Account;
import com.tesda.ciicc.banking.backend.service.BankingService;
import com.tesda.ciicc.banking.backend.web.dto.AccountPageResponse;
import com.tesda.ciicc.banking.backend.web.dto.AccountResponse;
import com.tesda.ciicc.banking.backend.web.dto.AmountRequest;
import com.tesda.ciicc.banking.backend.web.dto.CreateAccountRequest;
import com.tesda.ciicc.banking.backend.web.dto.CreateAccountWithNumberRequest;
import com.tesda.ciicc.banking.backend.web.dto.TransactionPageResponse;
import com.tesda.ciicc.banking.backend.web.dto.TransactionResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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

    @Operation(summary = "Open an account",
            description = "The bank assigns the account number: 11 digits, drawn on the server. "
                    + "Owned by the calling CUSTOMER (admins create operator accounts with no owner). "
                    + "A positive initial deposit is recorded as the account's first transaction. "
                    + "A customer never hears \"that number already exists\", because they never choose one.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Account created, with its assigned number"),
            @ApiResponse(responseCode = "401", description = "No valid access token"),
            @ApiResponse(responseCode = "422", description = "Invalid account name or initial deposit"),
            @ApiResponse(responseCode = "429", description = "More than 60 requests in one minute for this user; Retry-After says how long to wait"),
            @ApiResponse(responseCode = "503", description = "No account number could be assigned; safe to retry")
    })
    @PostMapping
    public ResponseEntity<AccountResponse> createAccount(
            @AuthenticationPrincipal CurrentUser caller,
            @RequestBody CreateAccountRequest request) throws BankingException {

        BigDecimal initialDeposit = Money.parse(request.initialDeposit(), "Initial deposit");
        Account account = banking.createAccount(caller, request.accountName(), initialDeposit);
        return ResponseEntity
                .created(URI.create("/api/accounts/" + account.getAccountNumber()))
                .body(AccountResponse.from(account));
    }

    @Operation(summary = "Open an account with a number the operator chose",
            description = "ADMIN only: an operator keys an account opened on paper or in another system. "
                    + "A CUSTOMER calling this is refused with 403, and a number that is taken is a 409.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Account created"),
            @ApiResponse(responseCode = "401", description = "No valid access token"),
            @ApiResponse(responseCode = "403", description = "Not an ADMIN"),
            @ApiResponse(responseCode = "409", description = "Account number already exists"),
            @ApiResponse(responseCode = "422", description = "Invalid account number, name, or initial deposit"),
            @ApiResponse(responseCode = "429", description = "More than 60 requests in one minute for this user; Retry-After says how long to wait")
    })
    @PostMapping("/with-number")
    public ResponseEntity<AccountResponse> createAccountWithNumber(
            @AuthenticationPrincipal CurrentUser caller,
            @RequestBody CreateAccountWithNumberRequest request) throws BankingException {

        BigDecimal initialDeposit = Money.parse(request.initialDeposit(), "Initial deposit");
        Account account = banking.createAccount(caller, request.accountNumber(),
                request.accountName(), initialDeposit);
        return ResponseEntity
                .created(URI.create("/api/accounts/" + account.getAccountNumber()))
                .body(AccountResponse.from(account));
    }

    @Operation(summary = "List the accounts the caller may see",
            description = "A CUSTOMER gets only the accounts they own; an ADMIN gets every account. "
                    + "Ordered by account number, oldest page first.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Page of accounts (possibly empty)"),
            @ApiResponse(responseCode = "401", description = "No valid access token"),
            @ApiResponse(responseCode = "422", description = "Invalid page or size"),
            @ApiResponse(responseCode = "429", description = "More than 60 requests in one minute for this user; Retry-After says how long to wait")
    })
    @GetMapping
    public AccountPageResponse listAccounts(
            @AuthenticationPrincipal CurrentUser caller,

            @Parameter(description = "Zero-based page number", example = "0")
            @RequestParam(defaultValue = "0") int page,

            @Parameter(description = "Page size, 1 to 100", example = "20")
            @RequestParam(defaultValue = "20") int size) throws BankingException {

        return AccountPageResponse.from(banking.listAccounts(caller, page, size));
    }

    @Operation(summary = "Get one account by account number")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Account found"),
            @ApiResponse(responseCode = "401", description = "No valid access token"),
            @ApiResponse(responseCode = "404", description = "Account does not exist, or is not the caller's — a CUSTOMER gets the identical answer for both"),
            @ApiResponse(responseCode = "422", description = "Invalid account number"),
            @ApiResponse(responseCode = "429", description = "More than 60 requests in one minute for this user; Retry-After says how long to wait")
    })
    @GetMapping("/{accountNumber}")
    public AccountResponse getAccount(@AuthenticationPrincipal CurrentUser caller,
                                      @PathVariable String accountNumber) throws BankingException {
        return AccountResponse.from(banking.getAccount(caller, accountNumber));
    }

    @Operation(summary = "Deposit into an account",
            description = "Requires an Idempotency-Key header. Replaying the same key with the same body "
                    + "returns the stored response without depositing again.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Deposit recorded (or replayed)"),
            @ApiResponse(responseCode = "400", description = "Missing or invalid Idempotency-Key"),
            @ApiResponse(responseCode = "401", description = "No valid access token"),
            @ApiResponse(responseCode = "404", description = "Account does not exist, or is not the caller's — a CUSTOMER gets the identical answer for both"),
            @ApiResponse(responseCode = "409", description = "Idempotency key already used with a different request"),
            @ApiResponse(responseCode = "422", description = "Invalid amount"),
            @ApiResponse(responseCode = "429", description = "More than 60 requests in one minute for this user; Retry-After says how long to wait")
    })
    @PostMapping("/{accountNumber}/deposits")
    public TransactionResponse deposit(
            @AuthenticationPrincipal CurrentUser caller,
            @PathVariable String accountNumber,
            @RequestHeader(IdempotencyService.HEADER_NAME) String idempotencyKey,
            @RequestBody AmountRequest request) throws BankingException {

        idempotency.requireValidKey(idempotencyKey);
        BigDecimal amount = Money.parse(request.amount(), "Deposit amount");
        return idempotency.execute(idempotencyKey, "DEPOSIT", idempotencyScope(caller, accountNumber),
                request, TransactionResponse.class,
                () -> TransactionResponse.from(
                        banking.deposit(caller, accountNumber, amount, request.description())));
    }
    @Operation(summary = "Withdraw from an account",
            description = "Requires an Idempotency-Key header, same replay rules as deposit.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Withdrawal recorded (or replayed)"),
            @ApiResponse(responseCode = "400", description = "Missing or invalid Idempotency-Key"),
            @ApiResponse(responseCode = "401", description = "No valid access token"),
            @ApiResponse(responseCode = "404", description = "Account does not exist, or is not the caller's — a CUSTOMER gets the identical answer for both"),
            @ApiResponse(responseCode = "409", description = "Idempotency key already used with a different request"),
            @ApiResponse(responseCode = "422", description = "Invalid amount or insufficient funds"),
            @ApiResponse(responseCode = "429", description = "More than 60 requests in one minute for this user; Retry-After says how long to wait")
    })
    @PostMapping("/{accountNumber}/withdrawals")
    public TransactionResponse withdraw(
            @AuthenticationPrincipal CurrentUser caller,
            @PathVariable String accountNumber,
            @RequestHeader(IdempotencyService.HEADER_NAME) String idempotencyKey,
            @RequestBody AmountRequest request) throws BankingException {

        idempotency.requireValidKey(idempotencyKey);
        BigDecimal amount = Money.parse(request.amount(), "Withdrawal amount");
        return idempotency.execute(idempotencyKey, "WITHDRAW", idempotencyScope(caller, accountNumber),
                request, TransactionResponse.class,
                () -> TransactionResponse.from(
                        banking.withdraw(caller, accountNumber, amount, request.description())));
    }

    @Operation(summary = "Transaction history of an account, oldest first")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Page of transactions"),
            @ApiResponse(responseCode = "401", description = "No valid access token"),
            @ApiResponse(responseCode = "404", description = "Account does not exist, or is not the caller's — a CUSTOMER gets the identical answer for both"),
            @ApiResponse(responseCode = "422", description = "Invalid page or size"),
            @ApiResponse(responseCode = "429", description = "More than 60 requests in one minute for this user; Retry-After says how long to wait")
    })
    @GetMapping("/{accountNumber}/transactions")
    public TransactionPageResponse transactionHistory(
            @AuthenticationPrincipal CurrentUser caller,
            @PathVariable String accountNumber,

            @Parameter(description = "Zero-based page number", example = "0")
            @RequestParam(defaultValue = "0") int page,

            @Parameter(description = "Page size, 1 to 100", example = "20")
            @RequestParam(defaultValue = "20") int size) throws BankingException {

        return TransactionPageResponse.from(
                banking.getTransactionHistoryPage(caller, accountNumber, page, size));
    }

    /**
     * Idempotency keys are scoped per user: without the caller id in the
     * hashed scope, customer B replaying customer A's key with an identical
     * body would receive A's stored response instead of an error.
     */
    private static String idempotencyScope(CurrentUser caller, String accountNumber) {
        return caller.id() + "|" + accountNumber;
    }
}
