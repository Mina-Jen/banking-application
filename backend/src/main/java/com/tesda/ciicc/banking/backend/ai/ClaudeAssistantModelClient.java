package com.tesda.ciicc.banking.backend.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Calls the Anthropic Messages API with tool definitions and parses the
 * model's answer into text and tool_use blocks. The API key comes from the
 * environment ({@code ANTHROPIC_API_KEY}) and is never stored in code or
 * configuration files; when it is missing the application still starts and
 * only the assistant answers 503.
 *
 * <p>The model's input is treated as untrusted: tool names and arguments are
 * handed to {@code AssistantTools}, which ignores everything outside its own
 * fixed vocabulary and never accepts a user id.</p>
 */
@Component
public class ClaudeAssistantModelClient implements AssistantModelClient {

    private static final Logger log = LoggerFactory.getLogger(ClaudeAssistantModelClient.class);

    static final String NOT_CONFIGURED_MESSAGE =
            "The banking assistant is not available: the Claude API key is not configured.";
    static final String UNAVAILABLE_MESSAGE =
            "The banking assistant is temporarily unavailable. Please try again later.";

    private static final String API_VERSION = "2023-06-01";

    private final RestClient http;
    private final String apiKey;
    private final String model;
    private final int maxTokens;

    public ClaudeAssistantModelClient(
            RestClient.Builder builder,
            @Value("${app.ai.api-key:}") String apiKey,
            @Value("${app.ai.model:claude-haiku-4-5}") String model,
            @Value("${app.ai.base-url:https://api.anthropic.com}") String baseUrl,
            @Value("${app.ai.assistant-max-tokens:1024}") int maxTokens) {

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(5_000);
        requestFactory.setReadTimeout(30_000);
        this.http = builder.baseUrl(baseUrl).requestFactory(requestFactory).build();
        this.apiKey = apiKey;
        this.model = model;
        this.maxTokens = maxTokens;
    }

    @Override
    public AssistantTurn complete(String systemPrompt, List<AssistantMessage> conversation,
                                  List<AssistantToolSpec> tools) throws AssistantUnavailableException {

        if (apiKey == null || apiKey.isBlank()) {
            log.warn("Assistant chat requested, but app.ai.api-key (ANTHROPIC_API_KEY) is empty.");
            throw new AssistantUnavailableException(NOT_CONFIGURED_MESSAGE);
        }

        try {
            JsonNode response = http.post()
                    .uri("/v1/messages")
                    .header("x-api-key", apiKey)
                    .header("anthropic-version", API_VERSION)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody(systemPrompt, conversation, tools))
                    .retrieve()
                    .body(JsonNode.class);
            return parseTurn(response);
        } catch (AssistantUnavailableException e) {
            throw e;
        } catch (RestClientException e) {
            log.warn("Claude assistant call failed: {}", e.getMessage());
            throw new AssistantUnavailableException(UNAVAILABLE_MESSAGE);
        }
    }

    private Map<String, Object> requestBody(String systemPrompt, List<AssistantMessage> conversation,
                                            List<AssistantToolSpec> tools) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("max_tokens", maxTokens);
        body.put("temperature", 0);
        body.put("system", systemPrompt);
        body.put("messages", conversation.stream().map(ClaudeAssistantModelClient::encodeMessage).toList());
        body.put("tools", tools.stream().map(ClaudeAssistantModelClient::encodeTool).toList());
        return body;
    }

    private static Map<String, Object> encodeMessage(AssistantMessage message) {
        return Map.of(
                "role", message.role(),
                "content", message.content().stream()
                        .map(ClaudeAssistantModelClient::encodeBlock)
                        .toList());
    }

    private static Map<String, Object> encodeBlock(AssistantBlock block) {
        if (block instanceof AssistantText text) {
            return Map.of("type", "text", "text", text.text());
        }
        if (block instanceof AssistantToolUse use) {
            return Map.of("type", "tool_use", "id", use.id(),
                    "name", use.name(), "input", use.input());
        }
        AssistantToolResult result = (AssistantToolResult) block;
        return Map.of("type", "tool_result", "tool_use_id", result.toolUseId(),
                "content", result.content());
    }

    private static Map<String, Object> encodeTool(AssistantToolSpec tool) {
        return Map.of("name", tool.name(), "description", tool.description(),
                "input_schema", tool.inputSchema());
    }

    /**
     * Keeps only text and tool_use blocks. An answer without either cannot be
     * acted on and is refused rather than returned as an empty reply.
     */
    static AssistantTurn parseTurn(JsonNode response) {
        JsonNode content = response == null ? null : response.path("content");
        if (content == null || !content.isArray() || content.isEmpty()) {
            log.warn("Claude assistant returned no content blocks.");
            throw new AssistantUnavailableException(UNAVAILABLE_MESSAGE);
        }

        List<AssistantBlock> blocks = new ArrayList<>();
        for (JsonNode block : content) {
            String type = block.path("type").asText("");
            if ("text".equals(type)) {
                String text = block.path("text").asText("");
                if (!text.isBlank()) {
                    blocks.add(new AssistantText(text));
                }
            } else if ("tool_use".equals(type)) {
                JsonNode input = block.path("input");
                if (!input.isObject()) {
                    input = JsonNodeFactory.instance.objectNode();
                }
                blocks.add(new AssistantToolUse(
                        block.path("id").asText(""),
                        block.path("name").asText(""),
                        input));
            }
        }
        if (blocks.isEmpty()) {
            log.warn("Claude assistant returned no usable content blocks.");
            throw new AssistantUnavailableException(UNAVAILABLE_MESSAGE);
        }
        return new AssistantTurn(blocks);
    }
}
