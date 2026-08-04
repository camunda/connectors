/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.chatmodel.provider.anthropic;

import static io.camunda.connector.agenticai.aiagent.model.request.v2.AnthropicChatModelConfiguration.ANTHROPIC_ID;

import com.anthropic.core.JsonObject;
import com.anthropic.core.JsonValue;
import com.anthropic.core.ObjectMappers;
import com.anthropic.models.messages.ContentBlock;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.StopReason;
import com.anthropic.models.messages.ToolUseBlock;
import com.anthropic.models.messages.Usage;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.agenticai.aiagent.chatmodel.ChatResult;
import io.camunda.connector.agenticai.aiagent.model.AgentMetrics;
import io.camunda.connector.agenticai.aiagent.model.message.AssistantMessage;
import io.camunda.connector.agenticai.aiagent.model.message.content.Content;
import io.camunda.connector.agenticai.aiagent.model.message.content.ProviderContent;
import io.camunda.connector.agenticai.aiagent.model.message.content.ReasoningContent;
import io.camunda.connector.agenticai.aiagent.model.message.content.TextContent;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCall;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * Maps an accumulated Anthropic SDK {@link Message} response to the domain {@link
 * AssistantMessage}, its {@link AgentMetrics}, and a {@link ChatResult}.
 *
 * <p>{@code text} blocks become {@link TextContent}, {@code tool_use} blocks become {@link
 * ToolCall}s, {@code thinking} / {@code redacted_thinking} blocks become {@link ReasoningContent}
 * carrying the full raw block as payload (re-emitted verbatim on the request side, see {@link
 * AnthropicContentConverter}, so reasoning round-trips losslessly), and every other block type is
 * captured losslessly as {@link ProviderContent} rather than dropped.
 *
 * <p>The {@code pause_turn} stop reason surfaces as a {@link ChatResult.Continuation}; every other
 * stop reason surfaces as {@link ChatResult.Completed}. The raw vendor stop reason string is always
 * preserved under the {@value #ANTHROPIC_METADATA_KEY} key in {@link AssistantMessage#metadata()},
 * independent of how it normalizes to the domain {@code StopReason}.
 */
public class AnthropicMessageResponseConverter {

  private static final String ANTHROPIC_METADATA_KEY = "anthropic";

  private final ObjectMapper objectMapper;

  public AnthropicMessageResponseConverter(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  public ChatResult toResult(Message message, Duration executionTime) {
    final AssistantMessage assistantMessage = toAssistantMessage(message);
    final AgentMetrics metrics =
        toMetrics(message, assistantMessage.toolCalls().size(), executionTime);

    final boolean paused =
        message.stopReason().map(sr -> sr.equals(StopReason.PAUSE_TURN)).orElse(false);
    return paused
        ? new ChatResult.Continuation(assistantMessage, metrics)
        : new ChatResult.Completed(assistantMessage, metrics);
  }

  AssistantMessage toAssistantMessage(Message message) {
    final List<Content> content = new ArrayList<>();
    final List<ToolCall> toolCalls = new ArrayList<>();

    for (final ContentBlock block : message.content()) {
      if (block.isText()) {
        final var text = block.text().orElseThrow();
        content.add(new TextContent(text.text(), residualMetadata(text, "text")));
      } else if (block.isToolUse()) {
        final var toolUse = block.toolUse().orElseThrow();
        toolCalls.add(new ToolCall(toolUse.id(), toolUse.name(), toolUseArguments(toolUse)));
      } else if (block.isThinking()) {
        // Raw block preserved verbatim (minus the lifted-out text) so it replays byte-identical
        // on the request side; see AnthropicContentConverter, which merges the text back in
        final Map<String, Object> raw =
            new LinkedHashMap<>(
                ObjectMappers.jsonMapper()
                    .convertValue(block, new TypeReference<Map<String, Object>>() {}));
        final String text = block.thinking().orElseThrow().thinking();
        raw.remove("thinking");
        content.add(new ReasoningContent(ANTHROPIC_ID, raw, text, null));
      } else if (block.isRedactedThinking()) {
        // Redacted thinking blocks carry no readable text (the `data` field is encrypted), so
        // there is nothing to lift out of the payload.
        final Map<String, Object> raw =
            ObjectMappers.jsonMapper()
                .convertValue(block, new TypeReference<Map<String, Object>>() {});
        content.add(new ReasoningContent(ANTHROPIC_ID, raw, null, null));
      } else {
        // Fallback for any Anthropic content block type not explicitly handled above: preserve
        // it losslessly, in original order, as ProviderContent
        final Map<String, Object> raw =
            ObjectMappers.jsonMapper()
                .convertValue(block, new TypeReference<Map<String, Object>>() {});
        content.add(new ProviderContent(ANTHROPIC_ID, raw, null));
      }
    }

    final var builder =
        AssistantMessage.builder()
            .content(content)
            .toolCalls(toolCalls)
            .messageId(message.id())
            .modelId(message.model().asString())
            .stopReason(mapStopReason(message.stopReason().orElse(null)));
    message
        .stopReason()
        .ifPresent(
            sr ->
                builder.metadata(
                    Map.of(ANTHROPIC_METADATA_KEY, Map.of("stopReason", sr.asString()))));
    return builder.build();
  }

  private Map<String, Object> toolUseArguments(ToolUseBlock toolUse) {
    // A no-argument tool call finalizes as JsonMissing (not an empty object), which throws if
    // serialized; treat a missing or non-object input as an empty argument map
    final JsonValue input = toolUse._input();
    if (!(input instanceof JsonObject)) {
      return Map.of();
    }

    final Map<String, Object> arguments =
        objectMapper.convertValue(input, new TypeReference<Map<String, Object>>() {});
    return arguments != null ? arguments : Map.of();
  }

  /**
   * Preserves any block field not already mapped to the domain object (e.g. id/name/input on {@link
   * ToolCall}) under {@value #ANTHROPIC_METADATA_KEY}, so replaying it reproduces the exact
   * response byte sequence and an unmapped Anthropic feature doesn't silently lose data.
   */
  private @Nullable Map<String, Object> residualMetadata(Object block, String... mappedKeys) {
    final Map<String, Object> raw =
        new LinkedHashMap<>(
            ObjectMappers.jsonMapper().convertValue(block, new TypeReference<>() {}));
    raw.keySet().removeAll(Set.of(mappedKeys));
    raw.remove("type"); // pure discriminator, always inferable from the domain type
    return raw.isEmpty() ? null : Map.of(ANTHROPIC_METADATA_KEY, raw);
  }

  private AgentMetrics toMetrics(Message message, int toolCalls, Duration executionTime) {
    final Usage usage = message.usage();
    final var tokenUsage =
        AgentMetrics.TokenUsage.builder()
            .inputTokenCount((int) usage.inputTokens())
            .outputTokenCount((int) usage.outputTokens())
            .cacheReadTokenCount(usage.cacheReadInputTokens().map(Long::intValue).orElse(0))
            .cacheCreationTokenCount(usage.cacheCreationInputTokens().map(Long::intValue).orElse(0))
            .reasoningTokenCount(
                usage.outputTokensDetails().map(d -> (int) d.thinkingTokens()).orElse(0))
            .build();

    return AgentMetrics.builder()
        .modelCalls(1)
        .toolCalls(toolCalls)
        .tokenUsage(tokenUsage)
        .executionTime(executionTime)
        .build();
  }

  private io.camunda.connector.agenticai.aiagent.model.message.@Nullable StopReason mapStopReason(
      @Nullable StopReason stopReason) {
    if (stopReason == null) {
      return null;
    }

    return switch (stopReason.value()) {
      case END_TURN, STOP_SEQUENCE ->
          io.camunda.connector.agenticai.aiagent.model.message.StopReason.STOP;
      case MAX_TOKENS -> io.camunda.connector.agenticai.aiagent.model.message.StopReason.LENGTH;
      case TOOL_USE -> io.camunda.connector.agenticai.aiagent.model.message.StopReason.TOOL_USE;
      case REFUSAL ->
          io.camunda.connector.agenticai.aiagent.model.message.StopReason.CONTENT_FILTERED;
      default ->
          new io.camunda.connector.agenticai.aiagent.model.message.StopReason.UnknownStopReason(
              stopReason.asString());
    };
  }
}
