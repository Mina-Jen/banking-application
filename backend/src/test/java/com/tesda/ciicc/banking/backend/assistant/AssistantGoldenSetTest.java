package com.tesda.ciicc.banking.backend.assistant;

import com.tesda.ciicc.banking.backend.assistant.AssistantGoldenSet.ExpectedCall;
import com.tesda.ciicc.banking.backend.assistant.AssistantGoldenSet.GoldenCase;
import com.tesda.ciicc.banking.backend.assistant.AssistantGoldenSet.GoldenSet;
import com.tesda.ciicc.banking.backend.service.AssistantTools;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Checks the shape of the golden set itself, before any of it is replayed:
 * exactly 50 unique questions, every tool covered, no expected argument that
 * claims to choose a user, and both transfer-approval and no-tool questions
 * represented.
 */
class AssistantGoldenSetTest {

    private static final Set<String> TOOLS = Set.of(
            AssistantTools.GET_BALANCE,
            AssistantTools.SEARCH_TRANSACTIONS,
            AssistantTools.SUMMARIZE_SPENDING,
            AssistantTools.PROPOSE_TRANSFER);

    private final GoldenSet golden = AssistantGoldenSet.load();

    @Test
    void containsExactlyFiftyUniqueQuestions() {
        List<GoldenCase> cases = golden.cases();
        assertEquals(50, cases.size());

        Set<String> ids = new HashSet<>();
        Set<String> questions = new HashSet<>();
        for (GoldenCase testCase : cases) {
            assertTrue(ids.add(testCase.id()), "duplicate case id: " + testCase.id());
            assertTrue(questions.add(testCase.question()), "duplicate question: " + testCase.question());
            assertFalse(testCase.question().isBlank(), testCase.id() + " has a blank question");
        }
    }

    @Test
    void everyExpectedCallUsesAKnownToolAndNeverChoosesAUser() {
        for (GoldenCase testCase : golden.cases()) {
            for (ExpectedCall call : testCase.expectedToolCalls()) {
                assertTrue(TOOLS.contains(call.tool()),
                        testCase.id() + " expects unknown tool " + call.tool());
                assertTrue(call.arguments().keySet().stream()
                                .noneMatch(key -> key.toLowerCase(Locale.ROOT).contains("user")),
                        testCase.id() + " expects a user-supplied identity argument");
                assertFalse(call.arguments().containsKey("password"),
                        testCase.id() + " expects a password argument");
            }
        }
    }

    @Test
    void coversEveryToolAndIncludesNoToolQuestions() {
        Set<String> covered = new HashSet<>();
        int noToolCases = 0;
        int proposalCases = 0;
        int injectedUserCases = 0;

        for (GoldenCase testCase : golden.cases()) {
            if (testCase.expectedToolCalls().isEmpty()) {
                noToolCases++;
            }
            testCase.expectedToolCalls().forEach(call -> covered.add(call.tool()));
            if (!testCase.expectedProposals().isEmpty()) {
                proposalCases++;
            }
            if (testCase.modelAddsUserId()) {
                injectedUserCases++;
            }
        }

        assertEquals(TOOLS, covered);
        assertTrue(noToolCases >= 5, "golden set needs small-talk questions");
        assertTrue(proposalCases >= 5, "golden set needs transfer proposal questions");
        assertTrue(injectedUserCases >= 2, "golden set needs user-id injection questions");
    }

    @Test
    void everyProposalCaseAlsoExecutesTheProposalTool() {
        for (GoldenCase testCase : golden.cases()) {
            if (!testCase.expectedProposals().isEmpty()) {
                assertTrue(testCase.expectedToolCalls().stream()
                                .anyMatch(call -> AssistantTools.PROPOSE_TRANSFER.equals(call.tool())),
                        testCase.id() + " expects a proposal without calling propose_transfer");
            }
        }
    }
}
