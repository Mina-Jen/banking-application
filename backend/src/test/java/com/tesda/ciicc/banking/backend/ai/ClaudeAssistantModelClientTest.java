package com.tesda.ciicc.banking.backend.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tesda.ciicc.banking.backend.ai.AssistantModelClient.AssistantToolUse;
import com.tesda.ciicc.banking.backend.ai.AssistantModelClient.AssistantTurn;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The client's parsing and its refusal to call out without a key. The HTTP
 * call itself is replaced by a scripted model in the eval and service tests.
 */
class ClaudeAssistantModelClientTest {

    private final ObjectMapper json = new ObjectMapper();

    @Test
    void parsesTextAndToolUseBlocks() throws Exception {
        JsonNode response = json.readTree("""
                {"content": [
                  {"type": "text", "text": "Let me check that."},
                  {"type": "tool_use", "id": "toolu_1", "name": "get_balance",
                   "input": {"accountNumber": "200001"}},
                  {"type": "thinking", "thinking": "not exposed"}
                ]}
                """);

        AssistantTurn turn = ClaudeAssistantModelClient.parseTurn(response);

        assertEquals("Let me check that.", turn.text());
        assertEquals(1, turn.toolUses().size());
        AssistantToolUse use = turn.toolUses().get(0);
        assertEquals("toolu_1", use.id());
        assertEquals("get_balance", use.name());
        assertEquals("200001", use.input().path("accountNumber").asText());
    }

    @Test
    void aToolUseWithoutInputBecomesAnEmptyObject() throws Exception {
        JsonNode response = json.readTree("""
                {"content": [{"type": "tool_use", "id": "toolu_2", "name": "get_balance"}]}
                """);

        AssistantTurn turn = ClaudeAssistantModelClient.parseTurn(response);

        assertTrue(turn.toolUses().get(0).input().isObject());
        assertTrue(turn.toolUses().get(0).input().isEmpty());
    }

    @Test
    void refusesAnAnswerWithoutUsableContent() throws Exception {
        assertThrows(AssistantUnavailableException.class,
                () -> ClaudeAssistantModelClient.parseTurn(json.readTree("{\"content\": []}")));
        assertThrows(AssistantUnavailableException.class,
                () -> ClaudeAssistantModelClient.parseTurn(json.readTree(
                        "{\"content\": [{\"type\": \"thinking\", \"thinking\": \"hmm\"}]}")));
        assertThrows(AssistantUnavailableException.class,
                () -> ClaudeAssistantModelClient.parseTurn(null));
    }

    @Test
    void aMissingKeyIsRefusedBeforeAnyHttpCall() {
        ClaudeAssistantModelClient client = new ClaudeAssistantModelClient(
                RestClient.builder(), "  ", "claude-haiku-4-5", "http://127.0.0.1:1", 1024);

        AssistantUnavailableException e = assertThrows(AssistantUnavailableException.class,
                () -> client.complete("system", java.util.List.of(), java.util.List.of()));

        assertEquals(ClaudeAssistantModelClient.NOT_CONFIGURED_MESSAGE, e.getMessage());
    }
}
