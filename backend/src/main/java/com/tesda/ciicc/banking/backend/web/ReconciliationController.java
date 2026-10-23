package com.tesda.ciicc.banking.backend.web;

import com.tesda.ciicc.banking.backend.auth.CurrentUser;
import com.tesda.ciicc.banking.backend.service.ReconciliationService;
import com.tesda.ciicc.banking.backend.web.dto.ReconciliationResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/reconciliation")
@Tag(name = "Reconciliation", description = "ADMIN-only money-integrity check: the ledger against the stored balances")
public class ReconciliationController {

    private final ReconciliationService reconciliation;

    public ReconciliationController(ReconciliationService reconciliation) {
        this.reconciliation = reconciliation;
    }

    @Operation(summary = "Check that every account balance equals the sum of its ledger rows",
            description = "ADMIN only. Read-only: it reports the accounts that do not add up and changes "
                    + "no balance, because a discrepancy has to be explained before it is corrected.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Report; reconciled=false lists the mismatching accounts"),
            @ApiResponse(responseCode = "401", description = "No valid access token"),
            @ApiResponse(responseCode = "403", description = "Not an ADMIN"),
            @ApiResponse(responseCode = "429", description = "More than 60 requests in one minute for this user; Retry-After says how long to wait")
    })
    @GetMapping
    public ReconciliationResponse reconcile(@AuthenticationPrincipal CurrentUser caller) {
        return ReconciliationResponse.from(reconciliation.reconcile(caller));
    }
}
