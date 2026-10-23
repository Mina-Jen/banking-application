package com.tesda.ciicc.banking.backend.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * One turn of a tool-calling conversation with a language model. The rest of
 * the application depends only on this interface, so tests can replace the
 * model with a scripted client and no production code names a provider.
 *
 * <p>The model is an untrusted advisor: it may ask for a tool, but only
 * {@code AssistantService} decides whether that tool exists and
 * {@code AssistantTools} decides what the signed-in caller is allowed to see.
 * The caller's identity never travels through this interface.</p>
 */
public interface AssistantModelClient {

    AssistantTurn complete(String systemPrompt, List<AssistantMessage> conversation,
                           List<AssistantToolSpec> tools) throws AssistantUnavailableException;

    record AssistantMessage(String role, List<AssistantBlock> content) {
        public AssistantMessage {
            content = List.copyOf(content);
        }
    }

    sealed interface AssistantBlock permits AssistantText, AssistantToolUse, AssistantToolResult {
    }

    record AssistantText(String text) implements AssistantBlock {
    }

    record AssistantToolUse(String id, String name, JsonNode input) implements AssistantBlock {
        public AssistantToolUse {
            if (input == null) {
                input = JsonNodeFactory.instance.objectNode();
            }
        }
    }

    record AssistantToolResult(String toolUseId, String content) implements AssistantBlock {
    }

    /** A tool the model may ask for: its name, what it does, and its JSON input schema. */
    record AssistantToolSpec(String name, String description, Map<String, Object> inputSchema) {
        public AssistantToolSpec {
            inputSchema = Map.copyOf(inputSchema);
        }
    }

    /**
     * Everything the model answered in one round: plain text, tool_use blocks,
     * or both. Tool blocks are preserved verbatim so they can be echoed back
     * with their results, as the Messages API requires.
     */
    record AssistantTurn(List<AssistantBlock> content) {
        public AssistantTurn {
            content = List.copyOf(content);
        }

        public List<AssistantToolUse> toolUses() {
            return content.stream()
                    .filter(AssistantToolUse.class::isInstance)
                    .map(AssistantToolUse.class::cast)
                    .toList();
        }

        public String text() {
            return content.stream()
                    .filter(AssistantText.class::isInstance)
                    .map(AssistantText.class::cast)
                    .map(AssistantText::text)
                    .filter(text -> text != null && !text.isBlank())
                    .collect(Collectors.joining("\n"))
                    .strip();
        }
    }
}
