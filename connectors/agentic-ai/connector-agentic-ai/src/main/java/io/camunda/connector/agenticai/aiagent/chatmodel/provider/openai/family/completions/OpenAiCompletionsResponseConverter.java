/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.chatmodel.provider.openai.family.completions;

import static io.camunda.connector.agenticai.aiagent.agent.AgentErrorCodes.ERROR_CODE_FAILED_MODEL_CALL;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openai.models.chat.completions.ChatCompletion;
import com.openai.models.chat.completions.ChatCompletionMessage;
import com.openai.models.chat.completions.ChatCompletionMessageFunctionToolCall;
import com.openai.models.chat.completions.ChatCompletionMessageToolCall;
import com.openai.models.completions.CompletionUsage;
import io.camunda.connector.agenticai.aiagent.chatmodel.ChatModelRejectedException.PartialResult;
import io.camunda.connector.agenticai.aiagent.chatmodel.ChatResult;
import io.camunda.connector.agenticai.aiagent.chatmodel.ContentFilteredException;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.openai.family.OpenAiToolCallArguments;
import io.camunda.connector.agenticai.aiagent.model.AgentMetrics;
import io.camunda.connector.agenticai.aiagent.model.message.AssistantMessage;
import io.camunda.connector.agenticai.aiagent.model.message.StopReason;
import io.camunda.connector.agenticai.aiagent.model.message.StopReason.UnknownStopReason;
import io.camunda.connector.agenticai.aiagent.model.message.content.Content;
import io.camunda.connector.agenticai.aiagent.model.message.content.ReasoningContent;
import io.camunda.connector.agenticai.aiagent.model.message.content.TextContent;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCall;
import io.camunda.connector.agenticai.aiagent.util.AssistantMessageMetadata;
import io.camunda.connector.api.error.ConnectorException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Maps an accumulated OpenAI Chat Completions API SDK {@link ChatCompletion} to the domain {@link
 * AssistantMessage}, its {@link AgentMetrics}, and a {@link ChatResult}.
 *
 * <p>Deliberate subset of the sibling {@code OpenAiResponsesResponseConverter}: the Completions
 * message shape has no reasoning/thinking field and no server-tool result items, so no {@link
 * ReasoningContent} or {@code ProviderContent} is ever emitted here -- only {@link TextContent}
 * (from {@code content} and, when present, {@code refusal}) and {@link ToolCall} (from {@code
 * tool_calls}). Reasoning *token* accounting is not deferred, though: {@code
 * completion_tokens_details.reasoning_tokens} is surfaced via {@link AgentMetrics.TokenUsage}, even
 * though no corresponding {@link ReasoningContent} exists on the message.
 *
 * <p>The domain {@link StopReason} is derived from the choice's {@code finish_reason}; see {@link
 * #mapStopReason} for the mapping - except {@code content_filter}, which throws {@link
 * ContentFilteredException} instead, carrying the assistant message and metrics already built for
 * the turn as its {@link PartialResult} -- see {@link #hasRefusal} for the same treatment of a
 * refusal message, which carries no {@code finish_reason} signal of its own. Every other call
 * surfaces as a {@link ChatResult.Completed}.
 *
 * <p>The raw vendor {@code finish_reason} string is always preserved under the {@code openai}
 * provider-id key in {@link AssistantMessage#metadata()}; see {@link AssistantMessageMetadata} for
 * the {@code timestamp} entry every provider adds alongside it.
 */
public class OpenAiCompletionsResponseConverter {

  private static final String OPENAI_PROVIDER = "openai";

  private final ObjectMapper objectMapper;

  public OpenAiCompletionsResponseConverter(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  public ChatResult toResult(ChatCompletion completion, Duration executionTime) {
    final ChatCompletion.Choice choice = firstChoice(completion);
    final AssistantMessage assistantMessage = toAssistantMessage(completion, choice);
    final AgentMetrics metrics =
        toMetrics(completion, assistantMessage.toolCalls().size(), executionTime);

    if (hasRefusal(choice)
        || (assistantMessage.stopReason() instanceof UnknownStopReason unknown
            && ChatCompletion.Choice.FinishReason.CONTENT_FILTER
                .asString()
                .equals(unknown.value()))) {
      throw new ContentFilteredException(
          "Model response was blocked by provider content filtering.",
          new PartialResult(assistantMessage, metrics));
    }

    return new ChatResult.Completed(assistantMessage, metrics);
  }

  /**
   * OpenAI's own default is exactly one choice per completion ({@code n=1}); this connector never
   * configures {@code n}, so a well-formed response always has one. The SDK doesn't enforce this as
   * a non-empty guarantee, so guard explicitly rather than index blindly.
   */
  private ChatCompletion.Choice firstChoice(ChatCompletion completion) {
    final List<ChatCompletion.Choice> choices = completion.choices();
    if (choices.isEmpty()) {
      throw new ConnectorException(
          ERROR_CODE_FAILED_MODEL_CALL, "OpenAI response contained no choices");
    }
    return choices.get(0);
  }

  /**
   * A refusal carries no {@code finish_reason} signal of its own -- mirrors the Responses sibling's
   * {@code hasRefusal}, treating it the same as {@code content_filter} for a uniform "blocked"
   * outcome across both mechanisms.
   */
  private boolean hasRefusal(ChatCompletion.Choice choice) {
    return choice.message().refusal().isPresent();
  }

  private AssistantMessage toAssistantMessage(
      ChatCompletion completion, ChatCompletion.Choice choice) {
    final ChatCompletionMessage message = choice.message();

    final List<Content> content = new ArrayList<>();
    message
        .content()
        .filter(text -> !text.isBlank())
        .ifPresent(text -> content.add(TextContent.textContent(text)));
    // A refusal has no dedicated domain content type; kept as TextContent so the declination stays
    // visible in the partial result once toResult() throws ContentFilteredException for it (see
    // hasRefusal).
    message.refusal().ifPresent(refusal -> content.add(TextContent.textContent(refusal)));

    final List<ToolCall> toolCalls = new ArrayList<>();
    message.toolCalls().ifPresent(calls -> calls.forEach(call -> toToolCall(call, toolCalls)));

    final Map<String, Object> openAiMetadata =
        Map.of(OPENAI_PROVIDER, Map.of("stopReason", choice.finishReason().asString()));

    return AssistantMessage.builder()
        .content(content)
        .toolCalls(toolCalls)
        .messageId(completion.id())
        .modelId(completion.model())
        .stopReason(mapStopReason(choice.finishReason()))
        .metadata(AssistantMessageMetadata.withDefaults(openAiMetadata))
        .build();
  }

  /**
   * Maps the Completions API's {@code finish_reason} to the domain {@link StopReason}: {@code stop}
   * maps to {@link StopReason#STOP}, {@code tool_calls}/{@code function_call} map to {@link
   * StopReason#TOOL_USE}, and {@code length} maps to {@link StopReason#LENGTH} -- all returned as a
   * normal completion rather than raised as an error (see the class Javadoc). An unrecognized value
   * falls back to {@link StopReason.UnknownStopReason}, carrying the raw vendor value verbatim.
   * {@code content_filter} never reaches this mapping - {@link #toResult} throws before calling it.
   */
  private StopReason mapStopReason(ChatCompletion.Choice.FinishReason finishReason) {
    return switch (finishReason.value()) {
      case STOP -> StopReason.STOP;
      case TOOL_CALLS, FUNCTION_CALL -> StopReason.TOOL_USE;
      case LENGTH -> StopReason.LENGTH;
      default -> new StopReason.UnknownStopReason(finishReason.asString());
    };
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
            .arguments(
                OpenAiToolCallArguments.parse(objectMapper, functionCall.function().arguments()))
            .build());
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
        .inputTokenCount((int) (usage.promptTokens() - cachedTokens))
        .outputTokenCount((int) usage.completionTokens())
        .cacheReadTokenCount((int) cachedTokens)
        .reasoningTokenCount((int) reasoningTokens)
        .build();
  }
}
