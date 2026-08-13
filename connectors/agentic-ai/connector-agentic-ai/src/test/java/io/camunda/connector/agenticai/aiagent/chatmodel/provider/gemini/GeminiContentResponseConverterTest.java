/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.chatmodel.provider.gemini;

import static io.camunda.connector.agenticai.aiagent.chatmodel.provider.gemini.GeminiContentConverter.THOUGHT_SIGNATURE_METADATA_KEY;
import static io.camunda.connector.agenticai.aiagent.model.request.v2.GeminiChatModelConfiguration.GOOGLE_GEMINI_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.groups.Tuple.tuple;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.genai.types.Blob;
import com.google.genai.types.BlockedReason;
import com.google.genai.types.Candidate;
import com.google.genai.types.Content;
import com.google.genai.types.FileData;
import com.google.genai.types.FinishReason;
import com.google.genai.types.FunctionCall;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.GenerateContentResponsePromptFeedback;
import com.google.genai.types.GenerateContentResponseUsageMetadata;
import com.google.genai.types.Part;
import io.camunda.connector.agenticai.aiagent.chatmodel.ChatResult;
import io.camunda.connector.agenticai.aiagent.model.message.StopReason;
import io.camunda.connector.agenticai.aiagent.model.message.StopReason.UnknownStopReason;
import io.camunda.connector.agenticai.aiagent.model.message.content.ProviderContent;
import io.camunda.connector.agenticai.aiagent.model.message.content.ReasoningContent;
import io.camunda.connector.agenticai.aiagent.model.message.content.TextContent;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCall;
import io.camunda.connector.agenticai.aiagent.util.AssistantMessageMetadata;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Response objects are real SDK types built through their AutoValue builders (same approach as
 * {@code GeminiContentStreamAssemblerTest}), so the fixtures exercise the exact {@code Optional}
 * shapes a live response has.
 */
class GeminiContentResponseConverterTest {

  private static final Duration EXECUTION_TIME = Duration.ofMillis(42);
  private static final byte[] THOUGHT_SIGNATURE = "signature".getBytes(StandardCharsets.UTF_8);
  private static final String THOUGHT_SIGNATURE_BASE64 =
      Base64.getEncoder().encodeToString(THOUGHT_SIGNATURE);

  private final GeminiContentResponseConverter converter = new GeminiContentResponseConverter();

  // ---------------------------------------------------------------------------------------------
  // content mapping
  // ---------------------------------------------------------------------------------------------

  @Test
  void mapsTextPartToTextContentAndStopFinishReasonToStop() {
    final var response =
        responseBuilder(candidate(FinishReason.Known.STOP, Part.fromText("Hello there")))
            .responseId("resp-1")
            .modelVersion("gemini-3-pro-preview")
            .usageMetadata(
                GenerateContentResponseUsageMetadata.builder()
                    .promptTokenCount(10)
                    .candidatesTokenCount(20)
                    .build())
            .build();

    final var result = converter.toResult(response, EXECUTION_TIME);

    assertThat(result).isInstanceOf(ChatResult.Completed.class);

    final var assistantMessage = result.assistantMessage();
    assertThat(assistantMessage.content()).containsExactly(TextContent.textContent("Hello there"));
    assertThat(assistantMessage.toolCalls()).isEmpty();
    assertThat(assistantMessage.messageId()).isEqualTo("resp-1");
    assertThat(assistantMessage.modelId()).isEqualTo("gemini-3-pro-preview");
    assertThat(assistantMessage.stopReason()).isEqualTo(StopReason.STOP);
    assertThat(assistantMessage.metadata())
        .containsEntry(GOOGLE_GEMINI_ID, Map.of("finishReason", "STOP"))
        .containsKey(AssistantMessageMetadata.TIMESTAMP_KEY);

    final var metrics = result.metrics();
    assertThat(metrics.modelCalls()).isEqualTo(1);
    assertThat(metrics.toolCalls()).isZero();
    assertThat(metrics.tokenUsage().inputTokenCount()).isEqualTo(10);
    assertThat(metrics.tokenUsage().outputTokenCount()).isEqualTo(20);
    assertThat(metrics.executionTime()).isEqualTo(EXECUTION_TIME);
  }

  @Test
  void mapsPartsWithoutAKnownShapeToProviderContentPreservingOrder() {
    final var fileDataPart =
        Part.builder()
            .fileData(FileData.builder().fileUri("gs://bucket/chart.png").mimeType("image/png"))
            .build();

    final var response =
        response(
            candidate(
                FinishReason.Known.STOP,
                Part.fromText("before"),
                fileDataPart,
                Part.fromText("after")));

    final var assistantMessage = converter.toAssistantMessage(response);

    assertThat(assistantMessage.content())
        .containsExactly(
            TextContent.textContent("before"),
            new ProviderContent(
                GOOGLE_GEMINI_ID,
                Map.of(
                    "fileData",
                    Map.of("fileUri", "gs://bucket/chart.png", "mimeType", "image/png")),
                null),
            TextContent.textContent("after"));
  }

  @Test
  void preservesABinaryProviderPartLosslesslyAcrossTheRoundTrip() {
    // Binary part data stays a byte[] inside the ProviderContent payload (Jackson's convertValue
    // does not base64-encode it); what matters is that the request converter rebuilds the identical
    // Part from it, whether the payload was JSON-persisted in between or not.
    final var inlineDataPart =
        Part.builder()
            .inlineData(
                Blob.builder()
                    .data("image-bytes".getBytes(StandardCharsets.UTF_8))
                    .mimeType("image/png"))
            .build();

    final var content =
        converter
            .toAssistantMessage(response(candidate(FinishReason.Known.STOP, inlineDataPart)))
            .content();

    assertThat(content).singleElement().isInstanceOf(ProviderContent.class);
    assertThat(new GeminiContentConverter(new ObjectMapper()).toParts(content))
        .singleElement()
        .satisfies(
            part -> {
              final var blob = part.inlineData().orElseThrow();
              assertThat(blob.mimeType()).contains("image/png");
              assertThat(blob.data().orElseThrow())
                  .isEqualTo("image-bytes".getBytes(StandardCharsets.UTF_8));
            });
  }

  @Test
  void mapsCandidateWithoutContentToAnEmptyMessage() {
    // The real shape of a SAFETY-filtered (and of some MAX_TOKENS) candidate: no content at all.
    final var response =
        response(Candidate.builder().finishReason(FinishReason.Known.SAFETY).build());

    final var assistantMessage = converter.toAssistantMessage(response);

    assertThat(assistantMessage.content()).isEmpty();
    assertThat(assistantMessage.toolCalls()).isEmpty();
    assertThat(assistantMessage.stopReason()).isEqualTo(StopReason.CONTENT_FILTERED);
  }

  @Test
  void convertsAFilteredResponseTheSdkConvenienceAccessorsRefuseToRead() {
    final var response =
        response(candidate(FinishReason.Known.SAFETY, Part.fromText("partial answer")));

    // Guards the reason this converter reads candidates().get(0).content().parts() directly:
    // GenerateContentResponse#parts()/text()/functionCalls() call checkFinishReason() internally,
    // which throws for every finish reason outside {UNSPECIFIED, STOP, MAX_TOKENS}.
    assertThatThrownBy(response::parts).isInstanceOf(IllegalArgumentException.class);

    final var assistantMessage = converter.toAssistantMessage(response);

    assertThat(assistantMessage.content())
        .containsExactly(TextContent.textContent("partial answer"));
    assertThat(assistantMessage.stopReason()).isEqualTo(StopReason.CONTENT_FILTERED);
  }

  // ---------------------------------------------------------------------------------------------
  // tool calls
  // ---------------------------------------------------------------------------------------------

  @Test
  void mapsFunctionCallToToolCallAndOverridesStopWithToolUse() {
    // Gemini reports finishReason STOP even when the candidate contains function calls.
    final var response =
        response(
            candidate(
                FinishReason.Known.STOP,
                Part.fromText("Checking the weather"),
                functionCallPart("call-1", "get_weather", Map.of("city", "Berlin"))));

    final var result = converter.toResult(response, EXECUTION_TIME);
    final var assistantMessage = result.assistantMessage();

    assertThat(assistantMessage.toolCalls())
        .containsExactly(new ToolCall("call-1", "get_weather", Map.of("city", "Berlin")));
    assertThat(assistantMessage.content())
        .containsExactly(TextContent.textContent("Checking the weather"));
    assertThat(assistantMessage.stopReason()).isEqualTo(StopReason.TOOL_USE);
    // the raw vendor finish reason is preserved unchanged, independent of the override
    assertThat(assistantMessage.metadata())
        .containsEntry(GOOGLE_GEMINI_ID, Map.of("finishReason", "STOP"));
    assertThat(result.metrics().toolCalls()).isEqualTo(1);
  }

  @Test
  void derivesToolUseEvenWithoutAFinishReason() {
    final var response =
        response(
            Candidate.builder().content(contentOf(functionCallPart(null, "now", null))).build());

    assertThat(converter.toAssistantMessage(response).stopReason()).isEqualTo(StopReason.TOOL_USE);
  }

  @Test
  void synthesizesDistinctToolCallIdsWhenTheResponseCarriesNone() {
    // Gemini's Developer API routinely omits FunctionCall.id, including for parallel calls to the
    // same function - the synthesized ids must still be distinct so the agent loop can correlate
    // each result back to its own call.
    final var response =
        response(
            candidate(
                FinishReason.Known.STOP,
                functionCallPart(null, "get_weather", Map.of("city", "Berlin")),
                functionCallPart(null, "get_weather", Map.of("city", "Hamburg"))));

    final var toolCalls = converter.toAssistantMessage(response).toolCalls();

    assertThat(toolCalls).hasSize(2);
    assertThat(toolCalls).extracting(ToolCall::id).doesNotContainNull().doesNotHaveDuplicates();
    assertThat(toolCalls)
        .extracting(ToolCall::name, ToolCall::arguments)
        .containsExactly(
            tuple("get_weather", Map.of("city", "Berlin")),
            tuple("get_weather", Map.of("city", "Hamburg")));
  }

  @Test
  void mapsFunctionCallWithoutArgumentsToEmptyArguments() {
    final var response =
        response(candidate(FinishReason.Known.STOP, functionCallPart("call-1", "now", null)));

    assertThat(converter.toAssistantMessage(response).toolCalls())
        .containsExactly(new ToolCall("call-1", "now", Map.of()));
  }

  @Test
  void keepsAFunctionCallWithoutANameAsProviderContentRatherThanAnUnexecutableToolCall() {
    final var response =
        response(
            candidate(
                FinishReason.Known.STOP,
                Part.builder().functionCall(FunctionCall.builder().args(Map.of("a", 1))).build()));

    final var assistantMessage = converter.toAssistantMessage(response);

    assertThat(assistantMessage.toolCalls()).isEmpty();
    assertThat(assistantMessage.content()).singleElement().isInstanceOf(ProviderContent.class);
    // no executable tool call -> no TOOL_USE override
    assertThat(assistantMessage.stopReason()).isEqualTo(StopReason.STOP);
  }

  @Test
  void preservesAThoughtSignatureCarriedOnAFunctionCallPart() {
    final var part =
        functionCallPart("call-1", "get_weather", Map.of("city", "Berlin")).toBuilder()
            .thoughtSignature(THOUGHT_SIGNATURE)
            .build();

    final var toolCalls =
        converter
            .toAssistantMessage(response(candidate(FinishReason.Known.STOP, part)))
            .toolCalls();

    assertThat(toolCalls)
        .singleElement()
        .satisfies(
            toolCall ->
                assertThat(toolCall.metadata())
                    .isEqualTo(
                        Map.of(
                            GOOGLE_GEMINI_ID,
                            Map.of(THOUGHT_SIGNATURE_METADATA_KEY, THOUGHT_SIGNATURE_BASE64))));
  }

  // ---------------------------------------------------------------------------------------------
  // reasoning
  // ---------------------------------------------------------------------------------------------

  @Test
  void mapsThoughtPartToReasoningContentLiftingTextAndSignature() {
    final var thoughtPart =
        Part.builder()
            .thought(true)
            .text("Let me think it through")
            .thoughtSignature(THOUGHT_SIGNATURE)
            .build();

    final var assistantMessage =
        converter.toAssistantMessage(
            response(candidate(FinishReason.Known.STOP, thoughtPart, Part.fromText("the answer"))));

    assertThat(assistantMessage.content())
        .containsExactly(
            new ReasoningContent(
                GOOGLE_GEMINI_ID,
                Map.of("thought", true),
                "Let me think it through",
                Map.of(THOUGHT_SIGNATURE_METADATA_KEY, THOUGHT_SIGNATURE_BASE64)),
            TextContent.textContent("the answer"));
  }

  @Test
  void writesAThoughtSignatureTheRequestConverterReadsBackVerbatim() {
    final var thoughtPart =
        Part.builder()
            .thought(true)
            .text("Let me think it through")
            .thoughtSignature(THOUGHT_SIGNATURE)
            .build();

    final var content =
        converter
            .toAssistantMessage(response(candidate(FinishReason.Known.STOP, thoughtPart)))
            .content();

    // Round trip through the request-direction converter: same metadata key, same base64 alphabet.
    final var replayed = new GeminiContentConverter(new ObjectMapper()).toParts(content);

    assertThat(replayed)
        .singleElement()
        .satisfies(
            part -> {
              assertThat(part.thought()).contains(true);
              assertThat(part.text()).contains("Let me think it through");
              assertThat(part.thoughtSignature()).contains(THOUGHT_SIGNATURE);
            });
  }

  @Test
  void mapsThoughtPartWithoutTextToReasoningContentWithoutText() {
    final var thoughtPart =
        Part.builder().thought(true).thoughtSignature(THOUGHT_SIGNATURE).build();

    assertThat(
            converter
                .toAssistantMessage(response(candidate(FinishReason.Known.STOP, thoughtPart)))
                .content())
        .containsExactly(
            new ReasoningContent(
                GOOGLE_GEMINI_ID,
                Map.of("thought", true),
                null,
                Map.of(THOUGHT_SIGNATURE_METADATA_KEY, THOUGHT_SIGNATURE_BASE64)));
  }

  @Test
  void mapsThoughtPartWithoutSignatureToReasoningContentWithoutMetadata() {
    final var thoughtPart = Part.builder().thought(true).text("thinking").build();

    assertThat(
            converter
                .toAssistantMessage(response(candidate(FinishReason.Known.STOP, thoughtPart)))
                .content())
        .containsExactly(
            new ReasoningContent(GOOGLE_GEMINI_ID, Map.of("thought", true), "thinking", null));
  }

  // ---------------------------------------------------------------------------------------------
  // finish reason mapping
  // ---------------------------------------------------------------------------------------------

  @Test
  void mapsMaxTokensToLength() {
    final var response =
        response(candidate(FinishReason.Known.MAX_TOKENS, Part.fromText("cut off")));

    final var result = converter.toResult(response, EXECUTION_TIME);

    assertThat(result).isInstanceOf(ChatResult.Completed.class);
    assertThat(result.assistantMessage().stopReason()).isEqualTo(StopReason.LENGTH);
    assertThat(result.assistantMessage().metadata())
        .containsEntry(GOOGLE_GEMINI_ID, Map.of("finishReason", "MAX_TOKENS"));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "SAFETY",
        "RECITATION",
        "BLOCKLIST",
        "PROHIBITED_CONTENT",
        "SPII",
        "IMAGE_SAFETY",
        "IMAGE_PROHIBITED_CONTENT",
        "IMAGE_RECITATION"
      })
  void mapsFilteringFinishReasonsToContentFiltered(String finishReason) {
    final var response =
        response(
            Candidate.builder()
                .finishReason(finishReason)
                .content(contentOf(Part.fromText("filtered")))
                .build());

    final var result = converter.toResult(response, EXECUTION_TIME);

    assertThat(result.assistantMessage().stopReason()).isEqualTo(StopReason.CONTENT_FILTERED);
    assertThat(result.assistantMessage().metadata())
        .containsEntry(GOOGLE_GEMINI_ID, Map.of("finishReason", finishReason));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "LANGUAGE",
        "OTHER",
        "MALFORMED_FUNCTION_CALL",
        "UNEXPECTED_TOOL_CALL",
        "NO_IMAGE",
        "SOME_FUTURE_VENDOR_FINISH_REASON"
      })
  void mapsEveryOtherFinishReasonToUnknownStopReasonCarryingTheRawValue(String finishReason) {
    final var response =
        response(
            Candidate.builder()
                .finishReason(finishReason)
                .content(contentOf(Part.fromText("??")))
                .build());

    final var result = converter.toResult(response, EXECUTION_TIME);

    assertThat(result).isInstanceOf(ChatResult.Completed.class);
    assertThat(result.assistantMessage().stopReason())
        .isEqualTo(new UnknownStopReason(finishReason));
    assertThat(result.assistantMessage().metadata())
        .containsEntry(GOOGLE_GEMINI_ID, Map.of("finishReason", finishReason));
  }

  @Test
  void stampsTimestampMetadataEvenWithoutAFinishReason() {
    final var response =
        response(Candidate.builder().content(contentOf(Part.fromText("partial answer"))).build());

    final var assistantMessage = converter.toAssistantMessage(response);

    assertThat(assistantMessage.stopReason()).isNull();
    assertThat(assistantMessage.metadata())
        .containsOnlyKeys(AssistantMessageMetadata.TIMESTAMP_KEY);
  }

  // ---------------------------------------------------------------------------------------------
  // blocked prompt
  // ---------------------------------------------------------------------------------------------

  @Test
  void mapsABlockedPromptToContentFilteredCarryingAnExplanation() {
    // A blocked prompt yields promptFeedback and NO candidates at all (absent Optional).
    final var response =
        GenerateContentResponse.builder()
            .responseId("resp-blocked")
            .modelVersion("gemini-3-pro-preview")
            .promptFeedback(
                GenerateContentResponsePromptFeedback.builder()
                    .blockReason(BlockedReason.Known.SAFETY)
                    .build())
            .usageMetadata(
                GenerateContentResponseUsageMetadata.builder().promptTokenCount(7).build())
            .build();

    final var result = converter.toResult(response, EXECUTION_TIME);

    assertThat(result).isInstanceOf(ChatResult.Completed.class);

    final var assistantMessage = result.assistantMessage();
    assertThat(assistantMessage.content())
        .containsExactly(TextContent.textContent("Prompt blocked: SAFETY"));
    assertThat(assistantMessage.toolCalls()).isEmpty();
    assertThat(assistantMessage.stopReason()).isEqualTo(StopReason.CONTENT_FILTERED);
    assertThat(assistantMessage.messageId()).isEqualTo("resp-blocked");
    assertThat(assistantMessage.modelId()).isEqualTo("gemini-3-pro-preview");
    assertThat(assistantMessage.metadata())
        .containsEntry(GOOGLE_GEMINI_ID, Map.of("blockReason", "SAFETY"))
        .containsKey(AssistantMessageMetadata.TIMESTAMP_KEY);

    assertThat(result.metrics().tokenUsage().inputTokenCount()).isEqualTo(7);
    assertThat(result.metrics().modelCalls()).isEqualTo(1);
    assertThat(result.metrics().toolCalls()).isZero();
  }

  @Test
  void treatsAnEmptyCandidateListLikeAnAbsentOne() {
    final var response =
        GenerateContentResponse.builder()
            .candidates(List.of())
            .promptFeedback(
                GenerateContentResponsePromptFeedback.builder()
                    .blockReason(BlockedReason.Known.PROHIBITED_CONTENT)
                    .build())
            .build();

    final var assistantMessage = converter.toAssistantMessage(response);

    assertThat(assistantMessage.content())
        .containsExactly(TextContent.textContent("Prompt blocked: PROHIBITED_CONTENT"));
    assertThat(assistantMessage.stopReason()).isEqualTo(StopReason.CONTENT_FILTERED);
  }

  @Test
  void explainsAResponseWithoutCandidatesEvenWithoutABlockReason() {
    final var response = GenerateContentResponse.builder().build();

    final var assistantMessage = converter.toAssistantMessage(response);

    assertThat(assistantMessage.content())
        .containsExactly(TextContent.textContent("Prompt blocked (no block reason reported)"));
    assertThat(assistantMessage.stopReason()).isEqualTo(StopReason.CONTENT_FILTERED);
    assertThat(assistantMessage.metadata())
        .containsOnlyKeys(AssistantMessageMetadata.TIMESTAMP_KEY);
  }

  // ---------------------------------------------------------------------------------------------
  // metrics
  // ---------------------------------------------------------------------------------------------

  @Test
  void mapsUsageMetadataIncludingImplicitCacheReadsAndThoughtTokens() {
    final var response =
        responseBuilder(candidate(FinishReason.Known.STOP, Part.fromText("ok")))
            .usageMetadata(
                GenerateContentResponseUsageMetadata.builder()
                    .promptTokenCount(100)
                    .candidatesTokenCount(50)
                    .cachedContentTokenCount(3)
                    .thoughtsTokenCount(5)
                    .totalTokenCount(158)
                    .build())
            .build();

    final var tokenUsage = converter.toResult(response, EXECUTION_TIME).metrics().tokenUsage();

    assertThat(tokenUsage.inputTokenCount()).isEqualTo(100);
    assertThat(tokenUsage.outputTokenCount()).isEqualTo(50);
    assertThat(tokenUsage.cacheReadTokenCount()).isEqualTo(3);
    assertThat(tokenUsage.reasoningTokenCount()).isEqualTo(5);
    // Gemini's implicit caching reports reads only; there is no cache-write counter to map.
    assertThat(tokenUsage.cacheCreationTokenCount()).isZero();
  }

  @Test
  void defaultsEveryTokenCountToZeroWhenUsageMetadataIsMissing() {
    final var response = response(candidate(FinishReason.Known.STOP, Part.fromText("ok")));

    final var tokenUsage = converter.toResult(response, EXECUTION_TIME).metrics().tokenUsage();

    assertThat(tokenUsage.inputTokenCount()).isZero();
    assertThat(tokenUsage.outputTokenCount()).isZero();
    assertThat(tokenUsage.cacheReadTokenCount()).isZero();
    assertThat(tokenUsage.cacheCreationTokenCount()).isZero();
    assertThat(tokenUsage.reasoningTokenCount()).isZero();
  }

  @Test
  void defaultsPartiallyReportedTokenCountsToZero() {
    final var response =
        responseBuilder(candidate(FinishReason.Known.STOP, Part.fromText("ok")))
            .usageMetadata(
                GenerateContentResponseUsageMetadata.builder().promptTokenCount(11).build())
            .build();

    final var tokenUsage = converter.toResult(response, EXECUTION_TIME).metrics().tokenUsage();

    assertThat(tokenUsage.inputTokenCount()).isEqualTo(11);
    assertThat(tokenUsage.outputTokenCount()).isZero();
    assertThat(tokenUsage.cacheReadTokenCount()).isZero();
    assertThat(tokenUsage.reasoningTokenCount()).isZero();
  }

  @Test
  void leavesMessageIdAndModelIdUnsetWhenTheResponseDoesNotReportThem() {
    final var response = response(candidate(FinishReason.Known.STOP, Part.fromText("ok")));

    final var assistantMessage = converter.toAssistantMessage(response);

    assertThat(assistantMessage.messageId()).isNull();
    assertThat(assistantMessage.modelId()).isNull();
  }

  // ---------------------------------------------------------------------------------------------
  // fixtures
  // ---------------------------------------------------------------------------------------------

  private static GenerateContentResponse response(Candidate candidate) {
    return responseBuilder(candidate).build();
  }

  private static GenerateContentResponse.Builder responseBuilder(Candidate candidate) {
    return GenerateContentResponse.builder().candidates(List.of(candidate));
  }

  private static Candidate candidate(FinishReason.Known finishReason, Part... parts) {
    return Candidate.builder().finishReason(finishReason).content(contentOf(parts)).build();
  }

  private static Content contentOf(Part... parts) {
    return Content.builder().role("model").parts(List.of(parts)).build();
  }

  private static Part functionCallPart(String id, String name, Map<String, Object> args) {
    final var functionCall = FunctionCall.builder().name(name);
    if (id != null) {
      functionCall.id(id);
    }
    if (args != null) {
      functionCall.args(args);
    }
    return Part.builder().functionCall(functionCall).build();
  }
}
