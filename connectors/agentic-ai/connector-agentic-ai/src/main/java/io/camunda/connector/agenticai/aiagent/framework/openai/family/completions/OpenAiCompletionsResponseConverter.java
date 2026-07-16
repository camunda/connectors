/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.framework.openai.family.completions;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openai.models.chat.completions.ChatCompletion;
import com.openai.models.chat.completions.ChatCompletionMessage;
import com.openai.models.chat.completions.ChatCompletionMessageFunctionToolCall;
import com.openai.models.chat.completions.ChatCompletionMessageToolCall;
import com.openai.models.completions.CompletionUsage;
import io.camunda.connector.agenticai.aiagent.framework.api.ChatModelResult;
import io.camunda.connector.agenticai.aiagent.model.AgentMetrics;
import io.camunda.connector.agenticai.aiagent.model.message.AssistantMessage;
import io.camunda.connector.agenticai.aiagent.model.message.content.Content;
import io.camunda.connector.agenticai.aiagent.model.message.content.ReasoningContent;
import io.camunda.connector.agenticai.aiagent.model.message.content.TextContent;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCall;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Maps an accumulated OpenAI Chat Completions API SDK {@link ChatCompletion} to the domain {@link
 * AssistantMessage}, its {@link AgentMetrics}, and a {@link ChatModelResult}.
 *
 * <p>Deliberate pilot subset of the sibling {@code OpenAiResponsesResponseConverter}: the
 * Completions message shape has no reasoning/thinking field and no server-tool result items, so no
 * {@link ReasoningContent} or {@code ProviderContent} is ever emitted here -- only {@link
 * TextContent} (from {@code content} and, when present, {@code refusal}) and {@link ToolCall} (from
 * {@code tool_calls}). Reasoning *token* accounting is not deferred, though: {@code
 * completion_tokens_details.reasoning_tokens} is surfaced via {@link AgentMetrics.TokenUsage}, even
 * though no corresponding {@link ReasoningContent} exists on the message.
 *
 * <p>The Completions API has no equivalent of Anthropic's {@code pause_turn} stop reason, so every
 * call always surfaces as a {@link ChatModelResult.Completed}.
 */
public class OpenAiCompletionsResponseConverter {

  private final ObjectMapper objectMapper;

  public OpenAiCompletionsResponseConverter(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  public ChatModelResult toResult(ChatCompletion completion, Duration executionTime) {
    final AssistantMessage assistantMessage = toAssistantMessage(completion);
    final AgentMetrics metrics =
        toMetrics(completion, assistantMessage.toolCalls().size(), executionTime);
    return new ChatModelResult.Completed(assistantMessage, metrics);
  }

  AssistantMessage toAssistantMessage(ChatCompletion completion) {
    final ChatCompletionMessage message = completion.choices().get(0).message();

    final List<Content> content = new ArrayList<>();
    message
        .content()
        .filter(text -> !text.isBlank())
        .ifPresent(text -> content.add(TextContent.textContent(text)));
    // A refusal has no dedicated domain content type; surface its text as visible assistant text
    // rather than silently dropping it, mirroring the Responses sibling's refusal handling.
    message.refusal().ifPresent(refusal -> content.add(TextContent.textContent(refusal)));

    final List<ToolCall> toolCalls = new ArrayList<>();
    message.toolCalls().ifPresent(calls -> calls.forEach(call -> toToolCall(call, toolCalls)));

    return AssistantMessage.builder()
        .content(content)
        .toolCalls(toolCalls)
        .messageId(completion.id())
        .modelId(completion.model())
        .build();
  }

  private void toToolCall(ChatCompletionMessageToolCall call, List<ToolCall> toolCalls) {
    if (call.function().isEmpty()) {
      // Only function tool calls have a provider-neutral representation; custom tool calls are
      // not supported by the domain model and are silently skipped.
      return;
    }

    final ChatCompletionMessageFunctionToolCall functionCall = call.function().get();
    toolCalls.add(
        ToolCall.builder()
            .id(functionCall.id())
            .name(functionCall.function().name())
            .arguments(parseArguments(functionCall.function().arguments()))
            .build());
  }

  /**
   * No blank/missing guard is needed here: {@code function.arguments()} is a {@code
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

  private AgentMetrics toMetrics(ChatCompletion completion, int toolCalls, Duration executionTime) {
    final AgentMetrics.TokenUsage tokenUsage =
        completion.usage().map(this::toTokenUsage).orElseGet(AgentMetrics.TokenUsage::empty);

    return AgentMetrics.builder()
        .modelCalls(1)
        .toolCalls(toolCalls)
        .tokenUsage(tokenUsage)
        .executionTime(executionTime)
        .build();
  }

  private AgentMetrics.TokenUsage toTokenUsage(CompletionUsage usage) {
    final long cachedTokens =
        usage
            .promptTokensDetails()
            .flatMap(CompletionUsage.PromptTokensDetails::cachedTokens)
            .orElse(0L);
    final long reasoningTokens =
        usage
            .completionTokensDetails()
            .flatMap(CompletionUsage.CompletionTokensDetails::reasoningTokens)
            .orElse(0L);

    return AgentMetrics.TokenUsage.builder()
        .inputTokenCount((int) usage.promptTokens())
        .outputTokenCount((int) usage.completionTokens())
        .cacheReadTokenCount((int) cachedTokens)
        .reasoningTokenCount((int) reasoningTokens)
        .build();
  }
}
