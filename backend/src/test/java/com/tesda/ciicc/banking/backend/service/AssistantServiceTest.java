package com.tesda.ciicc.banking.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tesda.ciicc.banking.backend.ai.AssistantModelClient;
import com.tesda.ciicc.banking.backend.ai.AssistantModelClient.AssistantMessage;
import com.tesda.ciicc.banking.backend.ai.AssistantModelClient.AssistantText;
import com.tesda.ciicc.banking.backend.ai.AssistantModelClient.AssistantToolResult;
import com.tesda.ciicc.banking.backend.ai.AssistantModelClient.AssistantToolUse;
import com.tesda.ciicc.banking.backend.ai.AssistantModelClient.AssistantTurn;
import com.tesda.ciicc.banking.backend.ai.AssistantUnavailableException;
import com.tesda.ciicc.banking.backend.auth.CurrentUser;
import com.tesda.ciicc.banking.backend.exception.BankingValidationException;
import com.tesda.ciicc.banking.backend.model.Role;
import com.tesda.ciicc.banking.backend.service.AssistantService.AssistantReply;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * The tool loop: text answers pass through, tool results are fed back with
 * their tool_use ids, proposals are surfaced, and a model that keeps asking
 * for tools is cut off. The system prompt is checked for identity leakage.
 */
@ExtendWith(MockitoExtension.class)
class AssistantServiceTest {

    @Mock
    private AssistantModelClient model;

    @Mock
    private AssistantTools tools;

    private final ObjectMapper json = new ObjectMapper();
    private final CurrentUser caller = new CurrentUser(42, "secret-username", Role.CUSTOMER);
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-22T12:00:00Z"), ZoneOffset.UTC);

    private AssistantService service;

    @BeforeEach
    void setUp() {
        service = new AssistantService(model, tools, clock);
    }

    @Test
    void returnsAPlainTextAnswerAndSendsNoIdentityToTheModel() throws Exception {
        when(model.complete(anyString(), anyList(), anyList()))
                .thenReturn(new AssistantTurn(List.of(new AssistantText("Hello!"))));

        AssistantReply reply = service.chat(caller, "  hi  ");

        assertEquals("Hello!", reply.reply());
        assertTrue(reply.toolCalls().isEmpty());
        assertTrue(reply.transferProposals().isEmpty());

        ArgumentCaptor<String> prompt = ArgumentCaptor.forClass(String.class);
        verify(model).complete(prompt.capture(), anyList(), anyList());
        assertTrue(prompt.getValue().contains("Today is 2026-10-22"));
        assertFalse(prompt.getValue().contains("secret-username"));
        assertFalse(prompt.getValue().contains("42"));
    }

    @Test
    void feedsToolResultsBackAndSurfacesProposals() throws Exception {
        JsonNode input = json.readTree("""
                {"sourceAccountNumber":"200001","destinationAccountNumber":"200002","amount":"100.00"}
                """);
        AssistantToolUse use = new AssistantToolUse("toolu_1", AssistantTools.PROPOSE_TRANSFER, input);

        when(tools.execute(eq(caller), eq(AssistantTools.PROPOSE_TRANSFER), any(JsonNode.class)))
                .thenReturn(new AssistantTools.ToolExecution(AssistantTools.PROPOSE_TRANSFER,
                        Map.of("sourceAccountNumber", "200001"),
                        "{\"status\":\"PROPOSED\"}",
                        new TransferProposal("200001", "200002", "100.00", "PROPOSED", "note")));

        List<List<AssistantMessage>> conversations = new ArrayList<>();
        AtomicInteger calls = new AtomicInteger();
        when(model.complete(anyString(), anyList(), anyList())).thenAnswer(invocation -> {
            conversations.add(new ArrayList<>(invocation.getArgument(1)));
            if (calls.getAndIncrement() == 0) {
                return new AssistantTurn(List.of(use));
            }
            return new AssistantTurn(List.of(
                    new AssistantText("That is only a proposal; confirm it in the app.")));
        });

        AssistantReply reply = service.chat(caller, "transfer 100.00 to my savings");

        assertEquals(1, reply.toolCalls().size());
        assertEquals(AssistantTools.PROPOSE_TRANSFER, reply.toolCalls().get(0).tool());
        assertEquals(Map.of("sourceAccountNumber", "200001"),
                reply.toolCalls().get(0).arguments());
        assertEquals(1, reply.transferProposals().size());
        assertEquals("100.00", reply.transferProposals().get(0).amount());

        List<AssistantMessage> secondConversation = conversations.get(1);
        AssistantMessage last = secondConversation.get(secondConversation.size() - 1);
        assertEquals("user", last.role());
        AssistantToolResult result = (AssistantToolResult) last.content().get(0);
        assertEquals("toolu_1", result.toolUseId());
        assertEquals("{\"status\":\"PROPOSED\"}", result.content());
    }

    @Test
    void stopsAfterTheMaximumNumberOfToolRounds() {
        AssistantToolUse use = new AssistantToolUse("toolu_loop", AssistantTools.GET_BALANCE,
                json.createObjectNode());
        when(model.complete(anyString(), anyList(), anyList()))
                .thenReturn(new AssistantTurn(List.of(use)));
        when(tools.execute(eq(caller), eq(AssistantTools.GET_BALANCE), any(JsonNode.class)))
                .thenReturn(new AssistantTools.ToolExecution(AssistantTools.GET_BALANCE,
                        Map.of(), "{}", null));

        assertThrows(AssistantUnavailableException.class, () -> service.chat(caller, "keep going"));

        verify(model, times(AssistantService.MAX_TOOL_ROUNDS))
                .complete(anyString(), anyList(), anyList());
        verify(tools, times(AssistantService.MAX_TOOL_ROUNDS))
                .execute(eq(caller), eq(AssistantTools.GET_BALANCE), any(JsonNode.class));
    }

    @Test
    void anEmptyModelTurnIsUnavailable() {
        when(model.complete(anyString(), anyList(), anyList()))
                .thenReturn(new AssistantTurn(List.of()));

        assertThrows(AssistantUnavailableException.class, () -> service.chat(caller, "hello"));
    }

    @Test
    void aBlankMessageIsRejectedBeforeCallingTheModel() {
        assertThrows(BankingValidationException.class, () -> service.chat(caller, "   "));
        verifyNoInteractions(model, tools);
    }

    @Test
    void anOverlongMessageIsRejected() {
        String message = "x".repeat(AssistantService.MAX_MESSAGE_LENGTH + 1);

        assertThrows(BankingValidationException.class, () -> service.chat(caller, message));
        verifyNoInteractions(model, tools);
    }
}
