/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.chatmodel.provider.bedrock;

import static io.camunda.connector.agenticai.aiagent.agent.AgentErrorCodes.ERROR_CODE_FAILED_MODEL_CALL;
import static io.camunda.connector.agenticai.aiagent.model.request.v2.BedrockConverseChatModelConfiguration.BEDROCK_CONVERSE_ID;

import io.camunda.connector.agenticai.aiagent.chatmodel.ChatModelRejectedException;
import io.camunda.connector.agenticai.aiagent.chatmodel.ChatModelRejectedException.PartialResult;
import io.camunda.connector.agenticai.aiagent.chatmodel.ChatResult;
import io.camunda.connector.agenticai.aiagent.chatmodel.ContentFilteredException;
import io.camunda.connector.agenticai.aiagent.chatmodel.ContextWindowExceededException;
import io.camunda.connector.agenticai.aiagent.chatmodel.GuardrailInterventionException;
import io.camunda.connector.agenticai.aiagent.model.AgentMetrics;
import io.camunda.connector.agenticai.aiagent.model.message.AssistantMessage;
import io.camunda.connector.agenticai.aiagent.model.message.content.Content;
import io.camunda.connector.agenticai.aiagent.model.message.content.ProviderContent;
import io.camunda.connector.agenticai.aiagent.model.message.content.ReasoningContent;
import io.camunda.connector.agenticai.aiagent.model.message.content.TextContent;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCall;
import io.camunda.connector.agenticai.aiagent.util.AssistantMessageMetadata;
import io.camunda.connector.api.error.ConnectorException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import software.amazon.awssdk.core.SdkPojo;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseResponse;
import software.amazon.awssdk.services.bedrockruntime.model.Message;
import software.amazon.awssdk.services.bedrockruntime.model.ReasoningContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.StopReason;
import software.amazon.awssdk.services.bedrockruntime.model.TokenUsage;
import software.amazon.awssdk.services.bedrockruntime.model.ToolUseBlock;

/**
 * {@code text} blocks become {@link TextContent}, {@code toolUse} blocks become {@link ToolCall}s,
 * {@code reasoningContent} blocks become {@link ReasoningContent} carrying the full raw block as
 * payload so it round-trips losslessly (see {@link BedrockConverseContentConverter}), and every
 * other block type is captured as {@link ProviderContent} via {@link BedrockConverseSdkPojoCodec}
 * rather than dropped. Only {@link ContentBlock.Type#UNKNOWN_TO_SDK_VERSION} cannot be captured
 * (the SDK surfaces no field data for it), so it fails the call instead.
 *
 * <p>Some stop reasons reject the turn instead of completing it, throwing a matching {@link
 * ChatModelRejectedException} subtype with the assistant message and metrics already built for the
 * turn as its {@link PartialResult}; see {@link #throwIfRejected}. The raw vendor stop reason
 * string is always preserved under the {@code bedrock} provider-id key in {@link
 * AssistantMessage#metadata()}; see {@link AssistantMessageMetadata} for the {@code timestamp}
 * entry every provider adds alongside it.
 */
public class BedrockConverseResponseConverter {

  private static final String METADATA_STOP_REASON = "stopReason";

  public ChatResult toResult(ConverseResponse response, Duration executionTime) {
    final AssistantMessage assistantMessage = toAssistantMessage(response);
    final AgentMetrics metrics =
        toMetrics(response, assistantMessage.toolCalls().size(), executionTime);

    throwIfRejected(response.stopReason(), new PartialResult(assistantMessage, metrics));

    return new ChatResult.Completed(assistantMessage, metrics);
  }

  /**
   * Fails the turn for the stop reasons that mean the response is unusable, rather than returning
   * it as a normal result: the blocking ones become a {@link ChatModelRejectedException} carrying
   * whatever content was already built as its {@link PartialResult}, while malformed output is a
   * generation failure rather than a policy decision and fails the call outright.
   */
  private void throwIfRejected(@Nullable StopReason stopReason, PartialResult partialResult) {
    if (stopReason == null) {
      return;
    }

    switch (stopReason) {
      case CONTENT_FILTERED ->
          throw new ContentFilteredException(
              "Model response was blocked by provider content filtering.", partialResult);
      case GUARDRAIL_INTERVENED ->
          throw new GuardrailInterventionException(
              "Model response was blocked by a provider-side guardrail policy.", partialResult);
      case MODEL_CONTEXT_WINDOW_EXCEEDED ->
          throw new ContextWindowExceededException(
              "Model's context window was exceeded before it could finish generating a response.",
              partialResult);
      case MALFORMED_MODEL_OUTPUT, MALFORMED_TOOL_USE ->
          throw new ConnectorException(
              ERROR_CODE_FAILED_MODEL_CALL,
              "The model produced malformed output (stop reason '%s')."
                  .formatted(stopReason.toString()));
      default -> {
        // every remaining stop reason describes a usable response
      }
    }
  }

  private AssistantMessage toAssistantMessage(ConverseResponse response) {
    final List<Content> content = new ArrayList<>();
    final List<ToolCall> toolCalls = new ArrayList<>();

    final Message message = response.output() != null ? response.output().message() : null;
    final List<ContentBlock> blocks =
        message != null && message.hasContent() ? message.content() : List.of();

    for (final ContentBlock block : blocks) {
      // Some models (observed: gpt-oss-120b) emit a text block with an empty string alongside a
      // reasoningContent/toolUse block in the same turn -- TextContent forbids blank text, so an
      // empty block carries no information to preserve and is dropped rather than crashing the
      // turn.
      if (block.text() != null && block.text().isBlank()) {
        continue;
      }

      if (block.text() != null) {
        content.add(new TextContent(block.text(), residualMetadata(block, "text")));
      } else if (block.toolUse() != null) {
        toolCalls.add(toToolCall(block.toolUse()));
      } else if (block.reasoningContent() != null) {
        content.add(toReasoningContent(block.reasoningContent()));
      } else if (block.type() == ContentBlock.Type.UNKNOWN_TO_SDK_VERSION) {
        throw new ConnectorException(
            ERROR_CODE_FAILED_MODEL_CALL,
            "Received a Bedrock Converse content block of a type unknown to this SDK version; "
                + "the SDK surfaces no field data for it, so it cannot be captured or replayed.");
      } else {
        // Fallback for any Bedrock content block member not explicitly handled above: preserve it
        // losslessly, in original order, as ProviderContent -- see BedrockConverseSdkPojoCodec.
        // Never
        // skipped, since silently dropping a block would change the conversation.
        content.add(
            new ProviderContent(
                BEDROCK_CONVERSE_ID, BedrockConverseSdkPojoCodec.capture(block), null));
      }
    }

    final String rawStopReason = response.stopReasonAsString();
    final Map<String, Object> bedrockMetadata =
        rawStopReason != null
            ? Map.of(BEDROCK_CONVERSE_ID, Map.of(METADATA_STOP_REASON, rawStopReason))
            : Map.of();

    return AssistantMessage.builder()
        .content(content)
        .toolCalls(toolCalls)
        .stopReason(mapStopReason(response.stopReason(), rawStopReason))
        .metadata(AssistantMessageMetadata.withDefaults(bedrockMetadata))
        .build();
  }

  /**
   * Maps a {@code toolUse} block to a domain {@link ToolCall}.
   *
   * <p>{@link ToolCall#metadata()} is populated from {@link ToolUseBlock}'s unmapped fields, most
   * notably {@code type}, which Converse's server-tool-use feature sets.
   */
  private ToolCall toToolCall(ToolUseBlock toolUse) {
    // Captured once and reused for both arguments and residual metadata below, rather than each
    // independently walking the same ToolUseBlock's sdkFields() via
    // BedrockConverseSdkPojoCodec.capture().
    final Map<String, Object> captured = BedrockConverseSdkPojoCodec.capture(toolUse);
    final Map<String, Object> arguments = toolUseArguments(captured);
    final Map<String, Object> metadata =
        residualMetadataFromCapture(captured, "toolUseId", "name", "input");
    return new ToolCall(toolUse.toolUseId(), toolUse.name(), arguments, metadata);
  }

  /**
   * Converts the {@code input} entry of an already-{@link
   * BedrockConverseSdkPojoCodec#capture(SdkPojo) captured} {@link ToolUseBlock} (a generic AWS
   * {@code Document}) to the domain {@code arguments} map. Numbers survive round-trip precision
   * because the capture already used {@link java.math.BigDecimal} as the plain-Java stand-in for a
   * Document number (see {@link BedrockConverseSdkPojoCodec} for why).
   */
  private Map<String, Object> toolUseArguments(Map<String, Object> captured) {
    final Object input = captured.get("input");
    if (input instanceof Map<?, ?> map) {
      final Map<String, Object> arguments = new LinkedHashMap<>();
      map.forEach((k, v) -> arguments.put(String.valueOf(k), v));
      return arguments;
    }
    // Converse always supplies a Document for `input` (an empty object for a no-argument tool
    // call), so this is defensive rather than a case reachable through the real API.
    return Map.of();
  }

  /**
   * Maps a {@code reasoningContent} block to {@link ReasoningContent}. {@code reasoningText.text}
   * is lifted out into {@link ReasoningContent#text()} so it isn't persisted twice, while the full
   * raw block -- including {@code signature} (on a {@code reasoningText} member) and {@code
   * redactedContent} -- is preserved verbatim in the payload for {@link
   * BedrockConverseContentConverter} to replay byte-identically.
   *
   * <p>This round-trip is <strong>mandatory, not optional</strong>: reasoning arrives unbidden
   * (DeepSeek R1 always reasons; Claude Opus 4.6+ defaults to adaptive thinking), and AWS is
   * explicit that the signature and all previous reasoning blocks must be replayed verbatim in
   * subsequent Converse requests, or the call is rejected.
   */
  private ReasoningContent toReasoningContent(ReasoningContentBlock reasoningContentBlock) {
    final Map<String, Object> payload =
        new LinkedHashMap<>(BedrockConverseSdkPojoCodec.capture(reasoningContentBlock));

    String text = null;
    if (payload.get("reasoningText") instanceof Map<?, ?> reasoningTextValue) {
      final Map<String, Object> reasoningText = new LinkedHashMap<>();
      reasoningTextValue.forEach((k, v) -> reasoningText.put(String.valueOf(k), v));
      final Object textValue = reasoningText.remove("text");
      text = textValue instanceof String s ? s : null;
      payload.put("reasoningText", reasoningText);
    }

    return new ReasoningContent(BEDROCK_CONVERSE_ID, payload, text, null);
  }

  /**
   * Preserves any field of {@code pojo} not already mapped to a domain object (e.g.
   * toolUseId/name/input on {@link ToolCall}, or any sibling field alongside {@code text} on a text
   * {@link ContentBlock}) under the {@code bedrock} provider-id key, so replaying it reproduces the
   * response verbatim and an unmapped Bedrock field doesn't silently lose data.
   */
  private @Nullable Map<String, Object> residualMetadata(SdkPojo pojo, String... mappedKeys) {
    return residualMetadataFromCapture(BedrockConverseSdkPojoCodec.capture(pojo), mappedKeys);
  }

  /**
   * Same as {@link #residualMetadata(SdkPojo, String...)}, but starting from an already-{@link
   * BedrockConverseSdkPojoCodec#capture(SdkPojo) captured} map, so a caller that also needs the
   * capture for another purpose (e.g. {@link #toToolCall(ToolUseBlock)} extracting {@code input})
   * doesn't walk the same pojo's {@code sdkFields()} twice.
   */
  private @Nullable Map<String, Object> residualMetadataFromCapture(
      Map<String, Object> captured, String... mappedKeys) {
    final Map<String, Object> raw = new LinkedHashMap<>(captured);
    raw.keySet().removeAll(Set.of(mappedKeys));
    return raw.isEmpty() ? null : Map.of(BEDROCK_CONVERSE_ID, raw);
  }

  private AgentMetrics toMetrics(ConverseResponse response, int toolCalls, Duration executionTime) {
    final TokenUsage usage = response.usage();
    final var tokenUsage =
        AgentMetrics.TokenUsage.builder()
            .inputTokenCount(orZero(usage.inputTokens()))
            .outputTokenCount(orZero(usage.outputTokens()))
            .cacheReadTokenCount(orZero(usage.cacheReadInputTokens()))
            .cacheCreationTokenCount(orZero(usage.cacheWriteInputTokens()))
            // Converse's TokenUsage carries no reasoning-token breakdown, so this is always 0.
            .reasoningTokenCount(0)
            .build();

    return AgentMetrics.builder()
        .modelCalls(1)
        .toolCalls(toolCalls)
        .tokenUsage(tokenUsage)
        .executionTime(executionTime)
        .build();
  }

  private static int orZero(@Nullable Integer value) {
    return value != null ? value : 0;
  }

  private io.camunda.connector.agenticai.aiagent.model.message.@Nullable StopReason mapStopReason(
      @Nullable StopReason stopReason, @Nullable String rawStopReason) {
    if (stopReason == null) {
      return null;
    }

    return switch (stopReason) {
      case END_TURN, STOP_SEQUENCE ->
          io.camunda.connector.agenticai.aiagent.model.message.StopReason.STOP;
      case TOOL_USE -> io.camunda.connector.agenticai.aiagent.model.message.StopReason.TOOL_USE;
      case MAX_TOKENS -> io.camunda.connector.agenticai.aiagent.model.message.StopReason.LENGTH;
      // The rejected stop reasons never reach a returned ChatResult - toResult() throws before
      // returning for them, using this mapping's UnknownStopReason fallback only as the raw value
      // stashed on the exception's partial AssistantMessage.
      default ->
          new io.camunda.connector.agenticai.aiagent.model.message.StopReason.UnknownStopReason(
              // Fallback for NullAway; rawStopReason always mirrors stopReason here.
              rawStopReason != null ? rawStopReason : stopReason.toString());
    };
  }
}
