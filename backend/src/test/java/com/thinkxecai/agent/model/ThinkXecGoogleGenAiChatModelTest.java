package com.thinkxecai.agent.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.genai.Client;
import com.google.genai.types.Candidate;
import com.google.genai.types.Content;
import com.google.genai.types.FinishReason;
import com.google.genai.types.FunctionCall;
import com.google.genai.types.Part;
import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.ai.model.tool.DefaultToolExecutionEligibilityPredicate;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.retry.RetryUtils;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.LIST;

/**
 * Each Gemini candidate becomes one assistant message with its text, all its function calls, and the
 * calls' thought signatures (spring-ai #5466, ADR 038). Candidates are built in memory; nothing is sent.
 */
class ThinkXecGoogleGenAiChatModelTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final byte[] SIGNATURE_1 = "signature-1".getBytes(StandardCharsets.UTF_8);
    private static final byte[] SIGNATURE_2 = "signature-2".getBytes(StandardCharsets.UTF_8);

    private final ThinkXecGoogleGenAiChatModel model = new ThinkXecGoogleGenAiChatModel(
            Client.builder().apiKey("test-key").build(),
            GoogleGenAiChatOptions.builder().model("gemini-3.8-flash").build(),
            ToolCallingManager.builder().build(),
            RetryUtils.DEFAULT_RETRY_TEMPLATE,
            ObservationRegistry.NOOP,
            new DefaultToolExecutionEligibilityPredicate());

    private static Part text(String text) {
        return Part.builder().text(text).build();
    }

    private static Part call(String name, Map<String, Object> args) {
        return Part.builder().functionCall(FunctionCall.builder().name(name).args(args).build()).build();
    }

    private static Part signed(Part part, byte[] signature) {
        return part.toBuilder().thoughtSignature(signature).build();
    }

    private static Candidate candidate(Part... parts) {
        return Candidate.builder()
                .content(Content.builder().role("model").parts(List.of(parts)).build())
                .finishReason(new FinishReason(FinishReason.Known.STOP))
                .build();
    }

    private AssistantMessage onlyMessage(Candidate candidate) {
        List<Generation> generations = model.responseCandidateToGeneration(candidate);
        assertThat(generations).hasSize(1);
        return generations.get(0).getOutput();
    }

    @Test
    void textAndAFunctionCallBecomeOneMessageWithBoth() throws Exception {
        AssistantMessage message = onlyMessage(candidate(
                text("Let me check the date."),
                signed(call("getDate", Map.of()), SIGNATURE_1)));

        assertThat(message.getText()).isEqualTo("Let me check the date.");
        assertThat(message.getToolCalls()).singleElement().satisfies(toolCall -> {
            assertThat(toolCall.name()).isEqualTo("getDate");
            assertThat(toolCall.type()).isEqualTo("function");
            assertThat(JSON.readTree(toolCall.arguments())).isEqualTo(JSON.createObjectNode());
        });
        assertThat(message.getMetadata().get(ThinkXecGoogleGenAiChatModel.THOUGHT_SIGNATURES))
                .asInstanceOf(LIST)
                .containsExactly(SIGNATURE_1);
    }

    @Test
    void parallelCallsAreKeptInOrderWithOnlyTheCallsSignatures() throws Exception {
        AssistantMessage message = onlyMessage(candidate(
                signed(text("I will look up both."), SIGNATURE_2),
                signed(call("weather", Map.of("city", "Sydney", "date", "2026-10-18")), SIGNATURE_1),
                call("getCity", Map.of())));

        assertThat(message.getToolCalls()).extracting(AssistantMessage.ToolCall::name)
                .containsExactly("weather", "getCity");
        assertThat(JSON.readTree(message.getToolCalls().get(0).arguments()))
                .isEqualTo(JSON.readTree("{\"city\":\"Sydney\",\"date\":\"2026-10-18\"}"));
        // Sent back, the first stored signature goes on the first call: a text part's must not come first.
        assertThat(message.getMetadata().get(ThinkXecGoogleGenAiChatModel.THOUGHT_SIGNATURES))
                .asInstanceOf(LIST)
                .containsExactly(SIGNATURE_1);
    }

    @Test
    void aResponseWithOnlyCallsHasEmptyText() {
        AssistantMessage message = onlyMessage(candidate(call("getDate", Map.of()), call("getCity", Map.of())));

        assertThat(message.getText()).isEmpty();
        assertThat(message.getToolCalls()).hasSize(2);
        assertThat(message.getMetadata()).doesNotContainKey(ThinkXecGoogleGenAiChatModel.THOUGHT_SIGNATURES);
    }

    @Test
    void textSplitIntoSeveralPartsIsOneMessage() {
        AssistantMessage message = onlyMessage(candidate(text("The answer "), text("is 42.")));

        assertThat(message.getText()).isEqualTo("The answer is 42.");
        assertThat(message.hasToolCalls()).isFalse();
    }

    @Test
    void thoughtPartsAreNotPartOfTheAnswer() {
        Part thought = Part.builder().text("The user wants a date, so...").thought(true).build();

        AssistantMessage message = onlyMessage(candidate(thought, text("Calling the tool."),
                call("getDate", Map.of())));

        assertThat(message.getText()).isEqualTo("Calling the tool.");
        assertThat(message.getToolCalls()).hasSize(1);
    }

    @Test
    void aCallIdFromGeminiIsKeptAndAMissingOneIsEmpty() {
        Part withId = Part.builder()
                .functionCall(FunctionCall.builder().id("call-7").name("getDate").args(Map.of()).build())
                .build();

        AssistantMessage message = onlyMessage(candidate(withId, call("getCity", Map.of())));

        assertThat(message.getToolCalls()).extracting(AssistantMessage.ToolCall::id).containsExactly("call-7", "");
    }

    @Test
    void aTextAnswerKeepsEveryPartsSignature() {
        AssistantMessage message = onlyMessage(candidate(text("Done "), signed(text("now."), SIGNATURE_2)));

        assertThat(message.getMetadata().get(ThinkXecGoogleGenAiChatModel.THOUGHT_SIGNATURES))
                .asInstanceOf(LIST)
                .containsExactly(SIGNATURE_2);
    }

    @Test
    void aCandidateWithoutContentGivesOneEmptyMessage() {
        Candidate empty = Candidate.builder().finishReason(new FinishReason(FinishReason.Known.SAFETY)).build();

        AssistantMessage message = onlyMessage(empty);

        assertThat(message.getText()).isEmpty();
        assertThat(message.hasToolCalls()).isFalse();
    }

    @Test
    void theFinishReasonAndCandidateIndexAreKept() {
        Candidate cut = candidate(text("partial")).toBuilder()
                .index(0)
                .finishReason(new FinishReason(FinishReason.Known.MAX_TOKENS))
                .build();

        Generation generation = model.responseCandidateToGeneration(cut).get(0);

        assertThat(generation.getMetadata().getFinishReason()).isEqualTo("MAX_TOKENS");
        assertThat(generation.getOutput().getMetadata())
                .containsEntry(ThinkXecGoogleGenAiChatModel.CANDIDATE_INDEX, 0)
                .containsEntry(ThinkXecGoogleGenAiChatModel.FINISH_REASON, new FinishReason(FinishReason.Known.MAX_TOKENS));
    }

}
