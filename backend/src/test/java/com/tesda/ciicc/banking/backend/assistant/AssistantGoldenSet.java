package com.tesda.ciicc.banking.backend.assistant;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

/**
 * Loads the assistant's golden set. Each case records a question, the tool
 * calls a correct assistant should make for it, the facts those tool results
 * must contain, and any transfer proposal that should be surfaced. The set is
 * provider-neutral: CI replays it with a scripted model, and the same file can
 * be used to grade a real model.
 */
public final class AssistantGoldenSet {

    public static final String RESOURCE = "/assistant-golden.json";

    private AssistantGoldenSet() {
    }

    public static GoldenSet load() {
        try (InputStream in = AssistantGoldenSet.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException("Missing test resource " + RESOURCE);
            }
            return new ObjectMapper().readValue(in, GoldenSet.class);
        } catch (IOException e) {
            throw new IllegalStateException("Could not read " + RESOURCE, e);
        }
    }

    public record GoldenSet(List<GoldenCase> cases) {
        public GoldenSet {
            cases = List.copyOf(cases);
        }
    }

    public record GoldenCase(String id, String question, List<ExpectedCall> expectedToolCalls,
                             List<String> expectedResultContains,
                             List<ExpectedProposal> expectedProposals,
                             boolean modelAddsUserId) {
        public GoldenCase {
            expectedToolCalls = List.copyOf(expectedToolCalls);
            expectedResultContains = List.copyOf(expectedResultContains);
            expectedProposals = List.copyOf(expectedProposals);
        }
    }

    public record ExpectedCall(String tool, Map<String, Object> arguments) {
        public ExpectedCall {
            arguments = arguments == null ? Map.of() : Map.copyOf(arguments);
        }
    }

    public record ExpectedProposal(String sourceAccountNumber, String destinationAccountNumber,
                                   String amount) {
    }
}
