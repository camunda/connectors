/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.framework.openai.family.responses;

import static io.camunda.connector.agenticai.aiagent.agent.AgentErrorCodes.ERROR_CODE_RESPONSE_TRUNCATED;

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
import io.camunda.connector.agenticai.aiagent.framework.api.ChatModelResult;
import io.camunda.connector.agenticai.aiagent.model.AgentMetrics;
import io.camunda.connector.agenticai.aiagent.model.message.AssistantMessage;
import io.camunda.connector.agenticai.aiagent.model.message.content.Content;
import io.camunda.connector.agenticai.aiagent.model.message.content.ProviderContent;
import io.camunda.connector.agenticai.aiagent.model.message.content.ReasoningContent;
import io.camunda.connector.agenticai.aiagent.model.message.content.TextContent;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCall;
import io.camunda.connector.api.error.ConnectorException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Maps an accumulated OpenAI Responses API SDK {@link Response} to the domain {@link
 * AssistantMessage}, its {@link AgentMetrics}, and a {@link ChatModelResult}.
 *
 * <p>Content mapping is on the response side: an output {@code message} item's {@code output_text}
 * parts become {@link TextContent}, {@code function_call} items become {@link ToolCall}s, and
 * {@code reasoning} items become {@link ReasoningContent} whose {@code providerPayload} carries the
 * <strong>full raw item</strong> (a {@code Map<String, Object>} produced via the SDK's own {@link
 * ObjectMappers#jsonMapper()} -- {@code id}/{@code summary}/{@code encrypted_content}/... -- unlike
 * Anthropic's thinking blocks, this always includes {@code encrypted_content} rather than a
 * signature). This raw payload IS re-emitted back to OpenAI on the request side (see {@code
 * OpenAiResponsesRequestConverter}), so reasoning round-trips losslessly; the summary text stays
 * inside the raw payload rather than being duplicated onto the domain type.
 *
 * <p>Server-tool items ({@code web_search_call}, {@code code_interpreter_call}) have no
 * provider-neutral representation and are captured losslessly as {@link ProviderContent}, kept
 * inline in original order, and never added to {@code toolCalls} since these are server-side items
 * the caller is never expected to act on. Any future/unknown output item kind not recognized by
 * this SDK version falls back to the same {@link ProviderContent} treatment (keyed by its raw
 * {@code type} string) rather than being silently dropped.
 *
 * <p>The Responses API has no equivalent of Anthropic's {@code pause_turn} stop reason, so every
 * call always surfaces as a {@link ChatModelResult.Completed}.
 */
public class OpenAiResponsesResponseConverter {

  private static final Logger LOG = LoggerFactory.getLogger(OpenAiResponsesResponseConverter.class);

  private final ObjectMapper objectMapper;

  public OpenAiResponsesResponseConverter(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  public ChatModelResult toResult(Response response, Duration executionTime) {
    final boolean truncated =
        response
            .incompleteDetails()
            .flatMap(Response.IncompleteDetails::reason)
            .map(
                reason ->
                    reason.value() == Response.IncompleteDetails.Reason.Value.MAX_OUTPUT_TOKENS)
            .orElse(false);
    if (truncated) {
      throw new ConnectorException(
          ERROR_CODE_RESPONSE_TRUNCATED,
          "The model response was truncated because it reached the maximum output token limit"
              + " before completing. Increase the model's max output tokens.");
    }

    final AssistantMessage assistantMessage = toAssistantMessage(response);
    final AgentMetrics metrics =
        toMetrics(response, assistantMessage.toolCalls().size(), executionTime);
    return new ChatModelResult.Completed(assistantMessage, metrics);
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
      } else if (item.webSearchCall().isPresent()) {
        content.add(ProviderContent.providerContent("openai", "web_search_call", toRawMap(item)));
      } else if (item.codeInterpreterCall().isPresent()) {
        content.add(
            ProviderContent.providerContent("openai", "code_interpreter_call", toRawMap(item)));
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
        content.add(
            ProviderContent.providerContent("openai", String.valueOf(raw.get("type")), raw));
      }
    }

    return AssistantMessage.builder()
        .content(content)
        .toolCalls(toolCalls)
        .messageId(response.id())
        .modelId(modelId(response.model()))
        .build();
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
   * Builds the {@link ReasoningContent} for a reasoning output item: {@code providerPayload} is the
   * full raw item -- carrying {@code encrypted_content} and the summary -- so it can be replayed
   * byte-identical on the request side.
   */
  private ReasoningContent toReasoningContent(ResponseOutputItem item) {
    return new ReasoningContent(toRawMap(item), null);
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
