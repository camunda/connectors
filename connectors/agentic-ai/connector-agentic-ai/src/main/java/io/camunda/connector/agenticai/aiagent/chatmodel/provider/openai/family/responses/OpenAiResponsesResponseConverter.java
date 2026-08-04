/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.chatmodel.provider.openai.family.responses;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openai.core.ObjectMappers;
import com.openai.models.ResponsesModel;
import com.openai.models.responses.Response;
import com.openai.models.responses.ResponseFunctionToolCall;
import com.openai.models.responses.ResponseOutputItem;
import com.openai.models.responses.ResponseOutputMessage;
import com.openai.models.responses.ResponseUsage;
import io.camunda.connector.agenticai.aiagent.chatmodel.ChatResult;
import io.camunda.connector.agenticai.aiagent.model.AgentMetrics;
import io.camunda.connector.agenticai.aiagent.model.message.AssistantMessage;
import io.camunda.connector.agenticai.aiagent.model.message.StopReason;
import io.camunda.connector.agenticai.aiagent.model.message.content.Content;
import io.camunda.connector.agenticai.aiagent.model.message.content.ProviderContent;
import io.camunda.connector.agenticai.aiagent.model.message.content.ReasoningContent;
import io.camunda.connector.agenticai.aiagent.model.message.content.TextContent;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCall;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Maps an accumulated OpenAI Responses API SDK {@link Response} to the domain {@link
 * AssistantMessage}, its {@link AgentMetrics}, and a {@link ChatResult}.
 *
 * <p>Content mapping is on the response side: an output {@code message} item's {@code output_text}
 * parts become {@link TextContent}, {@code function_call} items become {@link ToolCall}s, and
 * {@code reasoning} items become {@link ReasoningContent} whose {@code payload} carries the
 * <strong>full raw item</strong> (a {@code Map<String, Object>} produced via the SDK's own {@link
 * ObjectMappers#jsonMapper()} -- {@code id}/{@code summary}/{@code encrypted_content}/... -- unlike
 * Anthropic's thinking blocks, this always includes {@code encrypted_content} rather than a
 * signature). This raw payload IS re-emitted back to OpenAI on the request side (see {@code
 * OpenAiResponsesRequestConverter}), so reasoning round-trips losslessly; the summary text stays
 * inside the raw payload rather than being duplicated onto the domain type.
 *
 * <p>Server-tool items ({@code web_search_call}, {@code code_interpreter_call}) have no
 * provider-neutral representation and are captured losslessly as {@link ProviderContent} (the raw
 * item map already carries its own {@code type} discriminator), kept inline in original order, and
 * never added to {@code toolCalls} since these are server-side items the caller is never expected
 * to act on. Any future/unknown output item kind not recognized by this SDK version falls back to
 * the same {@link ProviderContent} treatment rather than being silently dropped.
 *
 * <p>A response that was cut off because it hit the max output token limit ({@code
 * incomplete_details.reason == max_output_tokens}) maps to {@link StopReason#LENGTH} on the
 * assistant message and is still surfaced as a normal {@link ChatResult.Completed} -- mirroring
 * {@code AnthropicMessageResponseConverter}'s {@code MAX_TOKENS -> LENGTH} mapping -- rather than
 * throwing. The Responses API otherwise has no equivalent of Anthropic's {@code pause_turn} stop
 * reason, so every other call always surfaces as {@link ChatResult.Completed} too.
 */
public class OpenAiResponsesResponseConverter {

  private static final Logger LOG = LoggerFactory.getLogger(OpenAiResponsesResponseConverter.class);

  private static final String OPENAI_PROVIDER = "openai";

  private final ObjectMapper objectMapper;

  public OpenAiResponsesResponseConverter(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  public ChatResult toResult(Response response, Duration executionTime) {
    final AssistantMessage assistantMessage = toAssistantMessage(response);
    final AgentMetrics metrics =
        toMetrics(response, assistantMessage.toolCalls().size(), executionTime);
    return new ChatResult.Completed(assistantMessage, metrics);
  }

  AssistantMessage toAssistantMessage(Response response) {
    final List<Content> content = new ArrayList<>();
    final List<ToolCall> toolCalls = new ArrayList<>();

    for (final ResponseOutputItem item : response.output()) {
      if (item.message().isPresent()) {
        for (final ResponseOutputMessage.Content messageContent : item.message().get().content()) {
          messageContent
              .outputText()
              .ifPresent(text -> content.add(TextContent.textContent(text.text())));
          // A refusal has no dedicated domain content type; surface its text as visible
          // assistant text rather than silently dropping it.
          messageContent
              .refusal()
              .ifPresent(refusal -> content.add(TextContent.textContent(refusal.refusal())));
        }
      } else if (item.functionCall().isPresent()) {
        final ResponseFunctionToolCall functionCall = item.functionCall().get();
        toolCalls.add(
            ToolCall.builder()
                .id(functionCall.callId())
                .name(functionCall.name())
                .arguments(parseArguments(functionCall.arguments()))
                .build());
      } else if (item.reasoning().isPresent()) {
        content.add(toReasoningContent(item));
      } else if (item.webSearchCall().isPresent() || item.codeInterpreterCall().isPresent()) {
        content.add(ProviderContent.providerContent(OPENAI_PROVIDER, toRawMap(item)));
      } else {
        // Server-tool / provider-specific items not recognized by this SDK version have no
        // provider-neutral representation. Preserve them losslessly and in original order as
        // ProviderContent rather than silently dropping them; they are never client tool calls
        // (the caller is never expected to act on them), so they are kept out of toolCalls.
        final Map<String, Object> raw = toRawMap(item);
        if (LOG.isTraceEnabled()) {
          LOG.trace(
              "OpenAI server-side output item preserved as ProviderContent: type={}, payload={}",
              raw.get("type"),
              raw);
        }
        content.add(ProviderContent.providerContent(OPENAI_PROVIDER, raw));
      }
    }

    return AssistantMessage.builder()
        .content(content)
        .toolCalls(toolCalls)
        .messageId(response.id())
        .modelId(modelId(response.model()))
        .stopReason(truncated(response) ? StopReason.LENGTH : null)
        .build();
  }

  /**
   * The response is complete except that it was cut off by the max output token limit. Mapped to
   * {@link StopReason#LENGTH} rather than raised as an error -- mirrors {@code
   * AnthropicMessageResponseConverter}'s {@code MAX_TOKENS -> LENGTH} mapping; the caller decides
   * whether/how to react to a truncated turn, the converter never throws for it.
   */
  private boolean truncated(Response response) {
    return response
        .incompleteDetails()
        .flatMap(Response.IncompleteDetails::reason)
        .map(reason -> reason.value() == Response.IncompleteDetails.Reason.Value.MAX_OUTPUT_TOKENS)
        .orElse(false);
  }

  /**
   * {@link ResponsesModel} is a three-way union (a bare string, a {@code ChatModel} enum member, or
   * a Responses-only enum member): the wire value {@code "gpt-5"} deserializes into the {@code
   * ChatModel} variant (it matches a known enum member) rather than the bare-string variant, so
   * {@code asString()} cannot be called unconditionally -- the matching variant must be resolved
   * first.
   */
  private String modelId(ResponsesModel model) {
    if (model.isString()) {
      return model.asString();
    } else if (model.isChat()) {
      return model.asChat().asString();
    } else if (model.isOnly()) {
      return model.asOnly().asString();
    }
    return model.toString();
  }

  /**
   * Builds the {@link ReasoningContent} for a reasoning output item: {@code payload} is the full
   * raw item -- carrying {@code encrypted_content} and the summary -- so it can be replayed
   * byte-identical on the request side.
   */
  private ReasoningContent toReasoningContent(ResponseOutputItem item) {
    return ReasoningContent.reasoningContent(OPENAI_PROVIDER, toRawMap(item));
  }

  /**
   * Uses the SDK's own {@link ObjectMappers#jsonMapper()} rather than the injected app {@link
   * ObjectMapper}: only it knows how to serialize the raw item's {@code JsonValue}/{@code
   * JsonField} internals faithfully (e.g. omitting genuinely-absent optional fields instead of
   * materializing them as explicit {@code null}, and not leaking the Kotlin-generated {@code
   * isValid()} property as a spurious {@code valid} key) -- mirrors the Anthropic sibling's raw
   * block capture.
   */
  private Map<String, Object> toRawMap(ResponseOutputItem item) {
    return ObjectMappers.jsonMapper()
        .convertValue(item, new TypeReference<Map<String, Object>>() {});
  }

  /**
   * No blank/missing guard is needed here: {@code functionCall.arguments()} is a {@code
   * getRequired("arguments")} accessor that throws if the field is absent, and OpenAI always sends
   * a valid JSON object string for {@code arguments} -- {@code "{}"} for a no-argument call --
   * never a blank or missing one.
   */
  private Map<String, Object> parseArguments(String argumentsJson) {
    try {
      final Map<String, Object> arguments =
          objectMapper.readValue(argumentsJson, new TypeReference<Map<String, Object>>() {});
      return arguments != null ? arguments : Map.of();
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Failed to parse tool call arguments", e);
    }
  }

  private AgentMetrics toMetrics(Response response, int toolCalls, Duration executionTime) {
    final AgentMetrics.TokenUsage tokenUsage =
        response.usage().map(this::toTokenUsage).orElseGet(AgentMetrics.TokenUsage::empty);

    return AgentMetrics.builder()
        .modelCalls(1)
        .toolCalls(toolCalls)
        .tokenUsage(tokenUsage)
        .executionTime(executionTime)
        .build();
  }

  private AgentMetrics.TokenUsage toTokenUsage(ResponseUsage usage) {
    return AgentMetrics.TokenUsage.builder()
        .inputTokenCount((int) usage.inputTokens())
        .outputTokenCount((int) usage.outputTokens())
        .cacheReadTokenCount((int) usage.inputTokensDetails().cachedTokens())
        .reasoningTokenCount((int) usage.outputTokensDetails().reasoningTokens())
        .build();
  }
}
