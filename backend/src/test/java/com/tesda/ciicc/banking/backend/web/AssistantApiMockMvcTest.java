package com.tesda.ciicc.banking.backend.web;

import com.tesda.ciicc.banking.backend.ai.AssistantUnavailableException;
import com.tesda.ciicc.banking.backend.auth.JwtService;
import com.tesda.ciicc.banking.backend.auth.SecurityConfig;
import com.tesda.ciicc.banking.backend.exception.BankingValidationException;
import com.tesda.ciicc.banking.backend.service.AssistantService;
import com.tesda.ciicc.banking.backend.service.TransferProposal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Web layer of the assistant chat endpoint: authentication, response shape and
 * error mapping. The orchestration loop is mocked here; it is covered by
 * AssistantServiceTest, the tools by AssistantToolsTest and the full flow by
 * AssistantGoldenEvalTest.
 */
@WebMvcTest(controllers = AssistantController.class, properties = "app.api.max-requests=1000")
@Import(SecurityConfig.class)
class AssistantApiMockMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AssistantService assistant;

    @MockitoBean
    private JwtService jwt;

    @Test
    void chattingWithoutATokenIs401() throws Exception {
        mockMvc.perform(post("/api/assistant/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"What is my balance?\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockCurrentUser(id = 7, username = "juan")
    void theAnswerCarriesTheToolCallsAndAnyProposal() throws Exception {
        when(assistant.chat(argThat(caller -> caller.id() == 7 && "juan".equals(caller.username())),
                eq("Move 100 to savings.")))
                .thenReturn(new AssistantService.AssistantReply(
                        "That is only a proposal; confirm it in the app.",
                        List.of(new AssistantService.ToolCall("get_balance",
                                Map.of("accountNumber", "200001"))),
                        List.of(new TransferProposal("200001", "200002", "100.00", "PROPOSED",
                                "This is only a proposal; no money has moved."))));

        mockMvc.perform(post("/api/assistant/chat")
                        .header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"Move 100 to savings.\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reply").value("That is only a proposal; confirm it in the app."))
                .andExpect(jsonPath("$.toolCalls[0].tool").value("get_balance"))
                .andExpect(jsonPath("$.toolCalls[0].arguments.accountNumber").value("200001"))
                .andExpect(jsonPath("$.transferProposals[0].sourceAccountNumber").value("200001"))
                .andExpect(jsonPath("$.transferProposals[0].destinationAccountNumber").value("200002"))
                .andExpect(jsonPath("$.transferProposals[0].amount").value("100.00"))
                .andExpect(jsonPath("$.transferProposals[0].status").value("PROPOSED"))
                .andExpect(jsonPath("$.transferProposals[0].note").isNotEmpty());

        // The controller must pass the verified principal, not anything from the request body.
        verify(assistant).chat(argThat(caller -> caller.id() == 7 && caller.username().equals("juan")),
                eq("Move 100 to savings."));
    }

    @Test
    @WithMockCurrentUser
    void aQuestionWithNoToolsHasEmptyLists() throws Exception {
        when(assistant.chat(argThat(caller -> caller != null), eq("Hello")))
                .thenReturn(new AssistantService.AssistantReply("Hello! How can I help?", List.of(), List.of()));

        mockMvc.perform(post("/api/assistant/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"Hello\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reply").value("Hello! How can I help?"))
                .andExpect(jsonPath("$.toolCalls").isEmpty())
                .andExpect(jsonPath("$.transferProposals").isEmpty());
    }

    @Test
    @WithMockCurrentUser
    void aBlankMessageIs422() throws Exception {
        when(assistant.chat(argThat(caller -> caller != null), eq("   ")))
                .thenThrow(new BankingValidationException("Message must not be empty."));

        mockMvc.perform(post("/api/assistant/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"   \"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.message").value("Message must not be empty."));
    }

    @Test
    @WithMockCurrentUser
    void anUnavailableModelIs503() throws Exception {
        when(assistant.chat(argThat(caller -> caller != null), eq("What is my balance?")))
                .thenThrow(new AssistantUnavailableException(
                        "The banking assistant is temporarily unavailable. Please try again later."));

        mockMvc.perform(post("/api/assistant/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"What is my balance?\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value(503));
    }
}
