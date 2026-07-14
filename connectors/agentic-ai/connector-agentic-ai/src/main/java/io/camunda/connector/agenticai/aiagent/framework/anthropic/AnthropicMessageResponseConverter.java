/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.framework.anthropic;

import com.anthropic.core.JsonObject;
import com.anthropic.core.JsonValue;
import com.anthropic.core.ObjectMappers;
import com.anthropic.models.beta.messages.BetaContentBlock;
import com.anthropic.models.beta.messages.BetaMessage;
import com.anthropic.models.beta.messages.BetaStopReason;
import com.anthropic.models.beta.messages.BetaToolUseBlock;
import com.anthropic.models.beta.messages.BetaUsage;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.agenticai.aiagent.framework.api.ChatModelResult;
import io.camunda.connector.agenticai.aiagent.model.AgentMetrics;
import io.camunda.connector.agenticai.aiagent.model.message.AssistantMessage;
import io.camunda.connector.agenticai.aiagent.model.message.content.Content;
import io.camunda.connector.agenticai.aiagent.model.message.content.ProviderContent;
import io.camunda.connector.agenticai.aiagent.model.message.content.ReasoningContent;
import io.camunda.connector.agenticai.aiagent.model.message.content.TextContent;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCall;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Maps an accumulated Anthropic SDK (beta messages client) {@link BetaMessage} response to the
 * domain {@link AssistantMessage}, its {@link AgentMetrics}, and a {@link ChatModelResult}.
 *
 * <p>Content mapping is on the response side: {@code text} blocks become {@link TextContent},
 * {@code tool_use} blocks become {@link ToolCall}s, and {@code thinking} / {@code
 * redacted_thinking} blocks become {@link ReasoningContent} whose {@code providerPayload} carries
 * the <strong>full raw block</strong> (a {@code Map<String, Object>} produced via the SDK's own
 * mapper — {@code type}/{@code thinking}/{@code signature} for a thinking block, {@code
 * type}/{@code data} for a redacted one), not just the bare signature/redacted string. This raw
 * payload IS re-emitted back to Anthropic on the request side (see {@link
 * AnthropicContentConverter}), so reasoning now round-trips losslessly. Every other block type
 * (server-tool and code-execution blocks such as {@code server_tool_use}, {@code
 * code_execution_tool_result}, {@code web_search_tool_result}, {@code container_upload}, etc.) is
 * captured losslessly as {@link ProviderContent}, kept inline in original order, and never added to
 * {@code toolCalls} since these are server-side blocks the caller is never expected to act on.
 *
 * <p>The {@code pause_turn} stop reason surfaces as a {@link ChatModelResult.Continuation}
 * (Anthropic paused a long-running turn and expects to be called again without new input); every
 * other stop reason surfaces as {@link ChatModelResult.Completed}. The raw vendor stop reason
 * string is always preserved in {@link AssistantMessage#metadata()}, independent of how it
 * normalizes to the domain {@code StopReason}.
 *
 * <p>Note: the domain {@code io.camunda.connector.agenticai.aiagent.model.message.StopReason} is
 * referenced by its fully qualified name throughout this class (rather than importing it under its
 * simple name) purely to avoid a name clash with the Anthropic SDK's own {@link BetaStopReason};
 * Java has no import-aliasing syntax to express this more concisely.
 *
 * <p>Uses the <strong>beta</strong> messages client types (rather than the stable {@code
 * com.anthropic.models.messages} family) since the beta client is required for upcoming Skills
 * support; this migration is otherwise behavior-identical.
 */
public class AnthropicMessageResponseConverter {

  private static final Logger LOG =
      LoggerFactory.getLogger(AnthropicMessageResponseConverter.class);

  private final ObjectMapper objectMapper;

  public AnthropicMessageResponseConverter(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  public ChatModelResult toResult(BetaMessage message, Duration executionTime) {
    final AssistantMessage assistantMessage = toAssistantMessage(message);
    final AgentMetrics metrics =
        toMetrics(message, assistantMessage.toolCalls().size(), executionTime);

    final boolean paused =
        message.stopReason().map(sr -> sr.equals(BetaStopReason.PAUSE_TURN)).orElse(false);
    return paused
        ? new ChatModelResult.Continuation(assistantMessage, metrics)
        : new ChatModelResult.Completed(assistantMessage, metrics);
  }

  AssistantMessage toAssistantMessage(BetaMessage message) {
    final List<Content> content = new ArrayList<>();
    final List<ToolCall> toolCalls = new ArrayList<>();

    for (final BetaContentBlock block : message.content()) {
      if (block.isText()) {
        content.add(TextContent.textContent(block.text().orElseThrow().text()));
      } else if (block.isToolUse()) {
        final var toolUse = block.toolUse().orElseThrow();
        toolCalls.add(new ToolCall(toolUse.id(), toolUse.name(), toolUseArguments(toolUse)));
      } else if (block.isThinking()) {
        // The raw block (type/thinking/signature) is preserved in full as providerPayload so it
        // can be replayed byte-identical on the request side (see AnthropicContentConverter);
        // uses the SDK's own mapper for the same reason as the ProviderContent branch below.
        final Map<String, Object> raw =
            ObjectMappers.jsonMapper()
                .convertValue(block, new TypeReference<Map<String, Object>>() {});
        content.add(new ReasoningContent(block.thinking().orElseThrow().thinking(), raw, null));
      } else if (block.isRedactedThinking()) {
        final Map<String, Object> raw =
            ObjectMappers.jsonMapper()
                .convertValue(block, new TypeReference<Map<String, Object>>() {});
        content.add(new ReasoningContent(null, raw, null));
      } else {
        // Server-tool / provider-specific blocks (server_tool_use, code_execution_tool_result,
        // web_search_tool_result, container_upload, etc.) have no provider-neutral
        // representation. Preserve them losslessly and in original order as ProviderContent
        // rather than silently dropping them; they are never client tool calls (the caller is
        // never expected to act on them), so they are kept out of toolCalls. Uses the SDK's own
        // mapper (not the injected app ObjectMapper) since only it knows how to serialize the raw
        // block's JsonValue/JsonField internals.
        final Map<String, Object> raw =
            ObjectMappers.jsonMapper()
                .convertValue(block, new TypeReference<Map<String, Object>>() {});
        if (LOG.isTraceEnabled()) {
          LOG.trace(
              "Anthropic server-side content block preserved as ProviderContent: type={}, payload={}",
              raw.get("type"),
              raw);
        }
        content.add(new ProviderContent("anthropic", String.valueOf(raw.get("type")), raw, null));
      }
    }

    final var builder =
        AssistantMessage.builder()
            .content(content)
            .toolCalls(toolCalls)
            .messageId(message.id())
            .modelId(message.model().asString())
            .stopReason(mapStopReason(message.stopReason().orElse(null)));
    message.stopReason().ifPresent(sr -> builder.metadata(Map.of("stopReason", sr.asString())));
    return builder.build();
  }

  private Map<String, Object> toolUseArguments(BetaToolUseBlock toolUse) {
    // A no-argument tool call streams an empty input_json_delta, which the vendor SDK's
    // BetaMessageAccumulator finalizes as JsonMissing rather than an empty object (the same
    // JsonMissing also results from a tool_use block whose "input" field is absent). JsonMissing
    // throws "JsonMissing cannot be serialized" for any ObjectMapper, so treat a missing or
    // non-object input as an empty argument map.
    final JsonValue input = toolUse._input();
    if (!(input instanceof JsonObject)) {
      return Map.of();
    }

    final Map<String, Object> arguments =
        objectMapper.convertValue(input, new TypeReference<Map<String, Object>>() {});
    return arguments != null ? arguments : Map.of();
  }

  private AgentMetrics toMetrics(BetaMessage message, int toolCalls, Duration executionTime) {
    final BetaUsage usage = message.usage();
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

  /**
   * Normalizes the raw Anthropic stop reason to the provider-neutral domain {@code StopReason}.
   * {@code pause_turn} maps to {@code null} since it is surfaced as a {@link
   * ChatModelResult.Continuation} rather than a stop reason (the turn isn't actually finished).
   * Uses {@link BetaStopReason#value()} rather than {@code known()} so a genuinely unrecognised
   * future value degrades to the domain {@code UNKNOWN} sentinel instead of throwing.
   */
  private io.camunda.connector.agenticai.aiagent.model.message.@Nullable StopReason mapStopReason(
      @Nullable BetaStopReason stopReason) {
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
      case PAUSE_TURN -> null; // surfaced as Continuation, not a stop reason
      default -> io.camunda.connector.agenticai.aiagent.model.message.StopReason.UNKNOWN;
    };
  }
}
