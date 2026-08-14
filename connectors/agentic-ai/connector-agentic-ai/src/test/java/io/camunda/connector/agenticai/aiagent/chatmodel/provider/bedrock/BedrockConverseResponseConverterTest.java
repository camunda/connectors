/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.chatmodel.provider.bedrock;

import static io.camunda.connector.agenticai.aiagent.agent.AgentErrorCodes.ERROR_CODE_FAILED_MODEL_CALL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.agenticai.aiagent.chatmodel.ChatModelRejectedException;
import io.camunda.connector.agenticai.aiagent.chatmodel.ChatResult;
import io.camunda.connector.agenticai.aiagent.chatmodel.ContentFilteredException;
import io.camunda.connector.agenticai.aiagent.chatmodel.ContextWindowExceededException;
import io.camunda.connector.agenticai.aiagent.chatmodel.GuardrailInterventionException;
import io.camunda.connector.agenticai.aiagent.model.message.StopReason.UnknownStopReason;
import io.camunda.connector.agenticai.aiagent.model.message.content.ProviderContent;
import io.camunda.connector.agenticai.aiagent.model.message.content.ReasoningContent;
import io.camunda.connector.agenticai.aiagent.model.message.content.TextContent;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCall;
import io.camunda.connector.agenticai.aiagent.util.AssistantMessageMetadata;
import io.camunda.connector.api.error.ConnectorException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.core.document.Document;
import software.amazon.awssdk.services.bedrockruntime.model.CachePointBlock;
import software.amazon.awssdk.services.bedrockruntime.model.CachePointType;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ConversationRole;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseMetrics;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseOutput;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseResponse;
import software.amazon.awssdk.services.bedrockruntime.model.Message;
import software.amazon.awssdk.services.bedrockruntime.model.ReasoningContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ReasoningTextBlock;
import software.amazon.awssdk.services.bedrockruntime.model.StopReason;
import software.amazon.awssdk.services.bedrockruntime.model.TokenUsage;
import software.amazon.awssdk.services.bedrockruntime.model.ToolUseBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ToolUseType;

/**
 * {@code StopReason} is imported unqualified for the AWS Converse enum ({@link
 * software.amazon.awssdk.services.bedrockruntime.model.StopReason}); the domain {@code StopReason}
 * (see {@link io.camunda.connector.agenticai.aiagent.model.message.StopReason}) is always
 * fully-qualified below to avoid a same-simple-name import clash.
 */
class BedrockConverseResponseConverterTest {

  private static final Duration EXECUTION_TIME = Duration.ofMillis(42);

  private final BedrockConverseResponseConverter converter = new BedrockConverseResponseConverter();
  private final BedrockConverseContentConverter contentConverter =
      new BedrockConverseContentConverter(new ObjectMapper());

  private static ConverseResponse response(
      List<ContentBlock> content, StopReason stopReason, TokenUsage usage) {
    // Goes through the *String* overload rather than .stopReason(StopReason) directly: the
    // UNKNOWN_TO_SDK_VERSION singleton has no memory of the original unrecognised string, so a
    // typed setter given that constant would erase it; toString() reproduces the wire value
    // faithfully for every other, real constant.
    return responseWithRawStopReason(content, stopReason.toString(), usage);
  }

  private static ConverseResponse responseWithRawStopReason(
      List<ContentBlock> content, String rawStopReason, TokenUsage usage) {
    return ConverseResponse.builder()
        .output(
            ConverseOutput.fromMessage(
                Message.builder().role(ConversationRole.ASSISTANT).content(content).build()))
        .stopReason(rawStopReason)
        .usage(usage)
        // Deliberately large and distinct from EXECUTION_TIME so tests can assert it's ignored.
        .metrics(ConverseMetrics.builder().latencyMs(999_999L).build())
        .build();
  }

  private static TokenUsage usage(int inputTokens, int outputTokens) {
    return TokenUsage.builder().inputTokens(inputTokens).outputTokens(outputTokens).build();
  }

  @Test
  void mapsTextAndToolUseAndStopReason() {
    final var response =
        response(
            List.of(
                ContentBlock.fromText("Hello there"),
                ContentBlock.fromToolUse(
                    ToolUseBlock.builder()
                        .toolUseId("tooluse_1")
                        .name("get_weather")
                        .input(Document.mapBuilder().putString("city", "Berlin").build())
                        .build())),
            StopReason.TOOL_USE,
            usage(10, 20));

    final ChatResult result = converter.toResult(response, EXECUTION_TIME);

    assertThat(result).isInstanceOf(ChatResult.Completed.class);

    final var assistantMessage = result.assistantMessage();
    assertThat(assistantMessage.content()).containsExactly(TextContent.textContent("Hello there"));
    assertThat(assistantMessage.toolCalls())
        .containsExactly(new ToolCall("tooluse_1", "get_weather", Map.of("city", "Berlin"), null));
    assertThat(assistantMessage.stopReason())
        .isEqualTo(io.camunda.connector.agenticai.aiagent.model.message.StopReason.TOOL_USE);
    assertThat(assistantMessage.metadata())
        .containsEntry("bedrock", Map.of("stopReason", "tool_use"))
        .containsKey(AssistantMessageMetadata.TIMESTAMP_KEY);

    final var metrics = result.metrics();
    assertThat(metrics.modelCalls()).isEqualTo(1);
    assertThat(metrics.toolCalls()).isEqualTo(1);
    assertThat(metrics.tokenUsage().inputTokenCount()).isEqualTo(10);
    assertThat(metrics.tokenUsage().outputTokenCount()).isEqualTo(20);
    assertThat(metrics.executionTime()).isEqualTo(EXECUTION_TIME);
  }

  @Test
  void plainTextContentHasNoResidualMetadata() {
    final var response =
        response(List.of(ContentBlock.fromText("hi")), StopReason.END_TURN, usage(1, 1));

    final var content = converter.toResult(response, EXECUTION_TIME).assistantMessage().content();

    assertThat(content).hasSize(1);
    final var textContent = (TextContent) content.get(0);
    assertThat(textContent.text()).isEqualTo("hi");
    assertThat(textContent.metadata()).isNull();
  }

  @Test
  void capturesSiblingFieldsAlongsideTextAsResidualMetadataForReplay() {
    // Bedrock's `text` union member is a bare String with no sibling fields on the current API, so
    // this scenario is not reachable through the real SDK today -- it exercises the generic
    // residual-metadata mechanism's forward-compatibility by manually setting a
    // second field on the same builder alongside `text` (allowed at the Java level; only the
    // *derived* type() becomes ambiguous, which this converter's `block.text() != null` check never
    // consults).
    final ContentBlock block =
        ContentBlock.builder()
            .text("hi")
            .cachePoint(CachePointBlock.builder().type(CachePointType.DEFAULT).build())
            .build();
    final var response = response(List.of(block), StopReason.END_TURN, usage(1, 1));

    final var content = converter.toResult(response, EXECUTION_TIME).assistantMessage().content();

    assertThat(content).hasSize(1);
    final var textContent = (TextContent) content.get(0);
    assertThat(textContent.text()).isEqualTo("hi");
    assertThat(textContent.metadata()).isNotNull();
    @SuppressWarnings("unchecked")
    final var bedrockMetadata = (Map<String, Object>) textContent.metadata().get("bedrock");
    assertThat(bedrockMetadata).containsOnlyKeys("cachePoint");
  }

  @Test
  void mapsToolUseTypeIntoToolCallMetadata() {
    final var response =
        response(
            List.of(
                ContentBlock.fromToolUse(
                    ToolUseBlock.builder()
                        .toolUseId("tooluse_srv")
                        .name("code_execution")
                        .type(ToolUseType.SERVER_TOOL_USE)
                        .input(Document.mapBuilder().putString("code", "print(1)").build())
                        .build())),
            StopReason.TOOL_USE,
            usage(1, 1));

    final var toolCalls =
        converter.toResult(response, EXECUTION_TIME).assistantMessage().toolCalls();

    assertThat(toolCalls).hasSize(1);
    final var toolCall = toolCalls.get(0);
    assertThat(toolCall.id()).isEqualTo("tooluse_srv");
    assertThat(toolCall.name()).isEqualTo("code_execution");
    assertThat(toolCall.arguments()).containsEntry("code", "print(1)");
    assertThat(toolCall.metadata()).containsEntry("bedrock", Map.of("type", "server_tool_use"));
  }

  @Test
  void mapsNoArgumentToolUseToEmptyArguments() {
    final var response =
        response(
            List.of(
                ContentBlock.fromToolUse(
                    ToolUseBlock.builder()
                        .toolUseId("tooluse_now")
                        .name("now")
                        .input(Document.mapBuilder().build())
                        .build())),
            StopReason.TOOL_USE,
            usage(1, 1));

    final var toolCalls =
        converter.toResult(response, EXECUTION_TIME).assistantMessage().toolCalls();

    assertThat(toolCalls).containsExactly(new ToolCall("tooluse_now", "now", Map.of(), null));
  }

  @Test
  void mapsUnmappedContentBlockToProviderContentPreservingOrder() {
    final ContentBlock cachePoint =
        ContentBlock.fromCachePoint(CachePointBlock.builder().type(CachePointType.DEFAULT).build());
    final var response =
        response(
            List.of(ContentBlock.fromText("before"), cachePoint, ContentBlock.fromText("after")),
            StopReason.END_TURN,
            usage(1, 1));

    final var content = converter.toResult(response, EXECUTION_TIME).assistantMessage().content();

    assertThat(content)
        .containsExactly(
            TextContent.textContent("before"),
            new ProviderContent("bedrock", BedrockConverseSdkPojoCodec.capture(cachePoint), null),
            TextContent.textContent("after"));
  }

  @Test
  void throwsForUnknownToSdkVersionContentBlock() {
    // No content-block member is set at all; per ContentBlock's own union bookkeeping this resolves
    // type() to UNKNOWN_TO_SDK_VERSION, the same value the SDK produces for a genuinely
    // unrecognised
    // wire member -- the one case BedrockConverseSdkPojoCodec cannot capture, since the SDK
    // surfaces no
    // field
    // data for it.
    final ContentBlock emptyUnion = ContentBlock.builder().build();
    assertThat(emptyUnion.type()).isEqualTo(ContentBlock.Type.UNKNOWN_TO_SDK_VERSION);

    final var response = response(List.of(emptyUnion), StopReason.END_TURN, usage(1, 1));

    assertThatThrownBy(() -> converter.toResult(response, EXECUTION_TIME))
        .isInstanceOf(ConnectorException.class)
        .hasMessageContaining("unknown to this SDK version");
  }

  @Test
  void capturesReasoningContentWithSignature() {
    final var reasoningBlock =
        ReasoningContentBlock.fromReasoningText(
            ReasoningTextBlock.builder()
                .text("Let me think it through")
                .signature("sig-123")
                .build());
    final var response =
        response(
            List.of(
                ContentBlock.fromReasoningContent(reasoningBlock), ContentBlock.fromText("done")),
            StopReason.END_TURN,
            usage(1, 1));

    final var content = converter.toResult(response, EXECUTION_TIME).assistantMessage().content();

    assertThat(content).hasSize(2);
    final var reasoningContent = (ReasoningContent) content.get(0);
    assertThat(reasoningContent.provider()).isEqualTo("bedrock");
    assertThat(reasoningContent.text()).isEqualTo("Let me think it through");

    @SuppressWarnings("unchecked")
    final var payload = (Map<String, Object>) reasoningContent.payload();
    @SuppressWarnings("unchecked")
    final var reasoningText = (Map<String, Object>) payload.get("reasoningText");
    assertThat(reasoningText).containsEntry("signature", "sig-123").doesNotContainKey("text");
    assertThat(content.get(1)).isEqualTo(TextContent.textContent("done"));
  }

  @Test
  void capturesReasoningContentWithRedactedContent() {
    final var reasoningBlock =
        ReasoningContentBlock.fromRedactedContent(
            SdkBytes.fromUtf8String("opaque-encrypted-reasoning-payload"));
    final var response =
        response(
            List.of(ContentBlock.fromReasoningContent(reasoningBlock)),
            StopReason.END_TURN,
            usage(1, 1));

    final var content = converter.toResult(response, EXECUTION_TIME).assistantMessage().content();

    assertThat(content).hasSize(1);
    final var reasoningContent = (ReasoningContent) content.get(0);
    assertThat(reasoningContent.provider()).isEqualTo("bedrock");
    assertThat(reasoningContent.text()).isNull();
    @SuppressWarnings("unchecked")
    final var payload = (Map<String, Object>) reasoningContent.payload();
    assertThat(payload).containsOnlyKeys("redactedContent");
  }

  @Test
  void reasoningContentRoundTripsThroughContentConverterToTheOriginalBlock() {
    final ContentBlock original =
        ContentBlock.fromReasoningContent(
            ReasoningContentBlock.fromReasoningText(
                ReasoningTextBlock.builder()
                    .text("The user is asking about the weather, so I should call getWeather.")
                    .signature("sig-9f3a7c21")
                    .build()));
    final var response = response(List.of(original), StopReason.END_TURN, usage(1, 1));

    final var content = converter.toResult(response, EXECUTION_TIME).assistantMessage().content();
    assertThat(content).hasSize(1);
    final var reasoningContent = content.get(0);

    final var rebuilt = contentConverter.toContentBlocks(List.of(reasoningContent));

    assertThat(rebuilt).containsExactly(original);
  }

  @Test
  void reasoningContentWithRedactedContentRoundTripsThroughContentConverterToTheOriginalBlock() {
    final ContentBlock original =
        ContentBlock.fromReasoningContent(
            ReasoningContentBlock.fromRedactedContent(
                SdkBytes.fromUtf8String("opaque-encrypted-reasoning-payload")));
    final var response = response(List.of(original), StopReason.END_TURN, usage(1, 1));

    final var content = converter.toResult(response, EXECUTION_TIME).assistantMessage().content();
    final var reasoningContent = content.get(0);

    final var rebuilt = contentConverter.toContentBlocks(List.of(reasoningContent));

    assertThat(rebuilt).containsExactly(original);
  }

  @Test
  void populatesCacheAndReasoningTokenSubsets() {
    final var usage =
        TokenUsage.builder()
            .inputTokens(100)
            .outputTokens(50)
            .cacheReadInputTokens(3)
            .cacheWriteInputTokens(4)
            .build();
    final var response = response(List.of(ContentBlock.fromText("ok")), StopReason.END_TURN, usage);

    final var tokenUsage = converter.toResult(response, EXECUTION_TIME).metrics().tokenUsage();

    assertThat(tokenUsage.inputTokenCount()).isEqualTo(100);
    assertThat(tokenUsage.outputTokenCount()).isEqualTo(50);
    assertThat(tokenUsage.cacheReadTokenCount()).isEqualTo(3);
    assertThat(tokenUsage.cacheCreationTokenCount()).isEqualTo(4);
    // Converse's TokenUsage has no reasoning-token field at all.
    assertThat(tokenUsage.reasoningTokenCount()).isEqualTo(0);
  }

  @Test
  void usesPassedInExecutionTimeNotServerSideLatency() {
    final var response =
        response(List.of(ContentBlock.fromText("ok")), StopReason.END_TURN, usage(1, 1));

    final var metrics = converter.toResult(response, EXECUTION_TIME).metrics();

    // ConverseMetrics.latencyMs on the fixture is 999_999ms; the converter must ignore it entirely
    // and use the externally-measured wall-clock duration instead.
    assertThat(metrics.executionTime()).isEqualTo(EXECUTION_TIME);
    assertThat(metrics.executionTime()).isNotEqualTo(Duration.ofMillis(999_999L));
  }

  static Stream<Arguments> stopReasons() {
    return Stream.of(
        Arguments.of(
            StopReason.END_TURN,
            "end_turn",
            io.camunda.connector.agenticai.aiagent.model.message.StopReason.STOP),
        Arguments.of(
            StopReason.STOP_SEQUENCE,
            "stop_sequence",
            io.camunda.connector.agenticai.aiagent.model.message.StopReason.STOP),
        Arguments.of(
            StopReason.TOOL_USE,
            "tool_use",
            io.camunda.connector.agenticai.aiagent.model.message.StopReason.TOOL_USE),
        Arguments.of(
            StopReason.MAX_TOKENS,
            "max_tokens",
            io.camunda.connector.agenticai.aiagent.model.message.StopReason.LENGTH));
  }

  @ParameterizedTest
  @MethodSource("stopReasons")
  void mapsKnownStopReasonsToDomainStopReason(
      StopReason vendorStopReason,
      String rawValue,
      io.camunda.connector.agenticai.aiagent.model.message.StopReason expected) {
    final var response =
        response(List.of(ContentBlock.fromText("x")), vendorStopReason, usage(1, 1));

    final var assistantMessage = converter.toResult(response, EXECUTION_TIME).assistantMessage();

    assertThat(assistantMessage.stopReason()).isEqualTo(expected);
    assertThat(assistantMessage.metadata())
        .containsEntry("bedrock", Map.of("stopReason", rawValue));
  }

  static Stream<Arguments> rejectedStopReasons() {
    return Stream.of(
        Arguments.of(
            StopReason.CONTENT_FILTERED,
            ContentFilteredException.class,
            "Model response was blocked by provider content filtering."),
        Arguments.of(
            StopReason.GUARDRAIL_INTERVENED,
            GuardrailInterventionException.class,
            "Model response was blocked by a provider-side guardrail policy."),
        Arguments.of(
            StopReason.MODEL_CONTEXT_WINDOW_EXCEEDED,
            ContextWindowExceededException.class,
            "Model's context window was exceeded before it could finish generating a response."));
  }

  @ParameterizedTest
  @MethodSource("rejectedStopReasons")
  void throwsRejectionCarryingThePartialResult(
      StopReason vendorStopReason,
      Class<? extends ChatModelRejectedException> expectedType,
      String expectedMessage) {
    final var response =
        response(List.of(ContentBlock.fromText("as far as I got")), vendorStopReason, usage(7, 3));

    assertThatThrownBy(() -> converter.toResult(response, EXECUTION_TIME))
        .isInstanceOfSatisfying(
            expectedType,
            e -> {
              assertThat(e.getMessage()).isEqualTo(expectedMessage);
              assertThat(e.partialResult()).isNotNull();
              assertThat(e.partialResult().assistantMessage().content())
                  .containsExactly(TextContent.textContent("as far as I got"));
              assertThat(e.partialResult().metrics().tokenUsage().inputTokenCount()).isEqualTo(7);
            });
  }

  @ParameterizedTest
  @EnumSource(
      value = StopReason.class,
      names = {"MALFORMED_MODEL_OUTPUT", "MALFORMED_TOOL_USE"})
  void failsTheCallOnMalformedModelOutput(StopReason vendorStopReason) {
    final var response =
        response(List.of(ContentBlock.fromText("x")), vendorStopReason, usage(1, 1));

    assertThatThrownBy(() -> converter.toResult(response, EXECUTION_TIME))
        .isInstanceOfSatisfying(
            ConnectorException.class,
            e -> {
              assertThat(e.getErrorCode()).isEqualTo(ERROR_CODE_FAILED_MODEL_CALL);
              assertThat(e.getMessage())
                  .isEqualTo(
                      "The model produced malformed output (stop reason '%s')."
                          .formatted(vendorStopReason.toString()));
            });
  }

  @Test
  void mapsUnrecognisedStopReasonToUnknownStopReasonCarryingTheRawValue() {
    // The AWS SDK produces UNKNOWN_TO_SDK_VERSION for a stop reason string it doesn't recognise,
    // but stopReasonAsString() still returns the raw wire value verbatim.
    assertThat(StopReason.fromValue("some_new_vendor_stop_reason"))
        .isEqualTo(StopReason.UNKNOWN_TO_SDK_VERSION);

    final var response =
        responseWithRawStopReason(
            List.of(ContentBlock.fromText("x")), "some_new_vendor_stop_reason", usage(1, 1));

    final var assistantMessage = converter.toResult(response, EXECUTION_TIME).assistantMessage();

    assertThat(assistantMessage.stopReason())
        .isEqualTo(new UnknownStopReason("some_new_vendor_stop_reason"));
    assertThat(assistantMessage.metadata())
        .containsEntry("bedrock", Map.of("stopReason", "some_new_vendor_stop_reason"));
  }
}
