package com.tesda.ciicc.banking.backend.web;

import com.tesda.ciicc.banking.backend.auth.CurrentUser;
import com.tesda.ciicc.banking.backend.exception.BankingException;
import com.tesda.ciicc.banking.backend.service.AssistantService;
import com.tesda.ciicc.banking.backend.web.dto.AssistantChatRequest;
import com.tesda.ciicc.banking.backend.web.dto.AssistantChatResponse;
import com.tesda.ciicc.banking.backend.web.dto.AssistantToolCallResponse;
import com.tesda.ciicc.banking.backend.web.dto.TransferProposalResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Phase 4 chat assistant. The caller is taken from the verified access token
 * and passed to the service; the model never receives or chooses a user id.
 * Every lookup the assistant performs is read-only, and a transfer is only
 * ever returned as a proposal for the customer to confirm through
 * {@code POST /api/transfers}.
 */
@RestController
@RequestMapping("/api/assistant")
@Tag(name = "Assistant", description = "Claude-powered chat over the caller's own accounts")
public class AssistantController {

    private final AssistantService assistant;

    public AssistantController(AssistantService assistant) {
        this.assistant = assistant;
    }

    @Operation(summary = "Ask the banking assistant a question",
            description = "Runs a tool-calling conversation with Claude. The tools read balances, "
                    + "search transactions and summarize spending, always for the authenticated caller. "
                    + "A requested transfer comes back as a proposal only; confirm it with POST "
                    + "/api/transfers. Requires ANTHROPIC_API_KEY.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The assistant's answer"),
            @ApiResponse(responseCode = "401", description = "No valid access token"),
            @ApiResponse(responseCode = "422", description = "The message is empty or too long"),
            @ApiResponse(responseCode = "429", description = "More than 60 requests in one minute for this user; Retry-After says how long to wait"),
            @ApiResponse(responseCode = "503", description = "The Claude API key is missing, or the assistant did not answer")
    })
    @PostMapping("/chat")
    public AssistantChatResponse chat(@AuthenticationPrincipal CurrentUser caller,
                                      @RequestBody AssistantChatRequest request)
            throws BankingException {

        AssistantService.AssistantReply answer = assistant.chat(caller, request.message());

        List<AssistantToolCallResponse> toolCalls = answer.toolCalls().stream()
                .map(call -> new AssistantToolCallResponse(call.tool(), call.arguments()))
                .toList();
        List<TransferProposalResponse> proposals = answer.transferProposals().stream()
                .map(proposal -> new TransferProposalResponse(
                        proposal.sourceAccountNumber(),
                        proposal.destinationAccountNumber(),
                        proposal.amount(),
                        proposal.status(),
                        proposal.note()))
                .toList();
        return new AssistantChatResponse(answer.reply(), toolCalls, proposals);
    }
}
