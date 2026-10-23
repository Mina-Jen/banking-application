package com.tesda.ciicc.banking.backend.web;

import com.tesda.ciicc.banking.backend.auth.CurrentUser;
import com.tesda.ciicc.banking.backend.exception.BankingException;
import com.tesda.ciicc.banking.backend.service.AnomalyService;
import com.tesda.ciicc.banking.backend.web.dto.AnomalyFlagPageResponse;
import com.tesda.ciicc.banking.backend.web.dto.AnomalyFlagResponse;
import com.tesda.ciicc.banking.backend.web.dto.ReviewAnomalyFlagRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/anomaly-flags")
@Tag(name = "Anomaly flags", description = "ADMIN review of money movements the anomaly rules flagged")
public class AnomalyFlagController {

    private final AnomalyService anomalies;

    public AnomalyFlagController(AnomalyService anomalies) {
        this.anomalies = anomalies;
    }

    @Operation(summary = "List anomaly flags, newest first",
            description = "ADMIN only. Without a status filter every flag is listed; status=OPEN is "
                    + "the review queue. Flags never block or reverse a transaction.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Page of flags (possibly empty)"),
            @ApiResponse(responseCode = "401", description = "No valid access token"),
            @ApiResponse(responseCode = "403", description = "Not an ADMIN"),
            @ApiResponse(responseCode = "422", description = "Invalid status, page or size"),
            @ApiResponse(responseCode = "429", description = "More than 60 requests in one minute for this user; Retry-After says how long to wait")
    })
    @GetMapping
    public AnomalyFlagPageResponse listFlags(
            @AuthenticationPrincipal CurrentUser caller,

            @Parameter(description = "OPEN, REVIEWED or DISMISSED; omit for every state", example = "OPEN")
            @RequestParam(required = false) String status,

            @Parameter(description = "Zero-based page number", example = "0")
            @RequestParam(defaultValue = "0") int page,

            @Parameter(description = "Page size, 1 to 100", example = "20")
            @RequestParam(defaultValue = "20") int size) throws BankingException {

        return AnomalyFlagPageResponse.from(anomalies.listFlags(caller, status, page, size));
    }

    @Operation(summary = "Close an anomaly flag",
            description = "ADMIN only. Records REVIEWED or DISMISSED, who decided and when. "
                    + "A decision is one-way: an already closed flag is a 409.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Flag closed; the updated flag is returned"),
            @ApiResponse(responseCode = "401", description = "No valid access token"),
            @ApiResponse(responseCode = "403", description = "Not an ADMIN"),
            @ApiResponse(responseCode = "404", description = "No flag with that id"),
            @ApiResponse(responseCode = "409", description = "The flag was already reviewed"),
            @ApiResponse(responseCode = "422", description = "Status is not REVIEWED or DISMISSED"),
            @ApiResponse(responseCode = "429", description = "More than 60 requests in one minute for this user; Retry-After says how long to wait")
    })
    @PutMapping("/{flagId}")
    public AnomalyFlagResponse reviewFlag(
            @AuthenticationPrincipal CurrentUser caller,
            @PathVariable int flagId,
            @RequestBody ReviewAnomalyFlagRequest request) throws BankingException {

        return AnomalyFlagResponse.from(anomalies.reviewFlag(caller, flagId, request.status()));
    }
}
