package com.tesda.ciicc.banking.backend.web;

import com.tesda.ciicc.banking.backend.auth.CurrentUser;
import com.tesda.ciicc.banking.backend.exception.BankingException;
import com.tesda.ciicc.banking.backend.model.TransactionCategory;
import com.tesda.ciicc.banking.backend.service.BankingService;
import com.tesda.ciicc.banking.backend.web.dto.CategorySuggestionRequest;
import com.tesda.ciicc.banking.backend.web.dto.CategorySuggestionResponse;
import com.tesda.ciicc.banking.backend.web.dto.SetCategoryRequest;
import com.tesda.ciicc.banking.backend.web.dto.TransactionResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/accounts/{accountNumber}/transactions/{transactionId}")
@Tag(name = "Transaction categories", description = "AI-assisted filing of transactions under spending categories")
public class CategoryController {

    private final BankingService banking;

    public CategoryController(BankingService banking) {
        this.banking = banking;
    }

    @Operation(summary = "Suggest a category for a transaction",
            description = "Sends the transaction type, amount and an optional description to Claude and "
                    + "returns one code from the closed category vocabulary. The suggestion is NOT stored; "
                    + "accept it with PUT .../category. The account must belong to the caller.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "A suggestion (not yet saved)"),
            @ApiResponse(responseCode = "401", description = "No valid access token"),
            @ApiResponse(responseCode = "404", description = "Account or transaction not found, or not the caller's"),
            @ApiResponse(responseCode = "429", description = "More than 60 requests in one minute for this user; Retry-After says how long to wait"),
            @ApiResponse(responseCode = "503", description = "The Claude API key is missing, or the categorizer did not answer")
    })
    @PostMapping("/category-suggestion")
    public CategorySuggestionResponse suggestCategory(
            @AuthenticationPrincipal CurrentUser caller,
            @PathVariable String accountNumber,
            @PathVariable int transactionId,
            @RequestBody(required = false) CategorySuggestionRequest request) throws BankingException {

        String description = request == null ? null : request.description();
        TransactionCategory category =
                banking.suggestCategory(caller, accountNumber, transactionId, description);
        return new CategorySuggestionResponse(transactionId, category.name());
    }

    @Operation(summary = "Store a category for a transaction",
            description = "Accepts a suggested category or stores one chosen by hand. The code must be "
                    + "one of the documented vocabulary values; anything else is a 422.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Category stored; the updated transaction is returned"),
            @ApiResponse(responseCode = "401", description = "No valid access token"),
            @ApiResponse(responseCode = "404", description = "Account or transaction not found, or not the caller's"),
            @ApiResponse(responseCode = "422", description = "Category code is not in the vocabulary"),
            @ApiResponse(responseCode = "429", description = "More than 60 requests in one minute for this user; Retry-After says how long to wait")
    })
    @PutMapping("/category")
    public TransactionResponse setCategory(
            @AuthenticationPrincipal CurrentUser caller,
            @PathVariable String accountNumber,
            @PathVariable int transactionId,
            @RequestBody SetCategoryRequest request) throws BankingException {

        return TransactionResponse.from(
                banking.setCategory(caller, accountNumber, transactionId, request.category()));
    }
}
