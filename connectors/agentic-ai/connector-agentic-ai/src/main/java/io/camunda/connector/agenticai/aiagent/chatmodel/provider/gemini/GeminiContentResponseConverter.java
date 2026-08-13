/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.chatmodel.provider.gemini;

import static io.camunda.connector.agenticai.aiagent.chatmodel.provider.gemini.GeminiContentConverter.THOUGHT_SIGNATURE_METADATA_KEY;
import static io.camunda.connector.agenticai.aiagent.model.request.v2.GeminiChatModelConfiguration.GOOGLE_GEMINI_ID;

import com.fasterxml.jackson.core.type.TypeReference;
import com.google.genai.JsonSerializable;
import com.google.genai.types.BlockedReason;
import com.google.genai.types.Candidate;
import com.google.genai.types.FinishReason;
import com.google.genai.types.FunctionCall;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.GenerateContentResponsePromptFeedback;
import com.google.genai.types.GenerateContentResponseUsageMetadata;
import com.google.genai.types.Part;
import io.camunda.connector.agenticai.aiagent.chatmodel.ChatResult;
import io.camunda.connector.agenticai.aiagent.model.AgentMetrics;
import io.camunda.connector.agenticai.aiagent.model.message.AssistantMessage;
import io.camunda.connector.agenticai.aiagent.model.message.AssistantMessageBuilder;
import io.camunda.connector.agenticai.aiagent.model.message.StopReason;
import io.camunda.connector.agenticai.aiagent.model.message.content.Content;
import io.camunda.connector.agenticai.aiagent.model.message.content.ProviderContent;
import io.camunda.connector.agenticai.aiagent.model.message.content.ReasoningContent;
import io.camunda.connector.agenticai.aiagent.model.message.content.TextContent;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCall;
import io.camunda.connector.agenticai.aiagent.util.AssistantMessageMetadata;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.util.StringUtils;

/**
 * Maps the assembled Gemini SDK {@link GenerateContentResponse} (see {@link
 * GeminiContentStreamAssembler}) to the domain {@link AssistantMessage}, its {@link AgentMetrics},
 * and a {@link ChatResult}.
 *
 * <p>{@code text} parts become {@link TextContent}, {@code functionCall} parts become {@link
 * ToolCall}s, {@code thought}-flagged parts become {@link ReasoningContent}, and every other part
 * shape is captured losslessly as {@link ProviderContent} rather than dropped.
 *
 * <p>Two Gemini specifics drive the finish-reason handling:
 *
 * <ul>
 *   <li><b>No tool-use finish reason.</b> Gemini reports {@code STOP} even when the candidate
 *       contains {@code functionCall} parts, so the mapped stop reason is overridden with {@link
 *       StopReason#TOOL_USE} whenever the response produced tool calls. The raw vendor value is
 *       preserved unchanged under the {@code google-gemini} metadata key regardless.
 *   <li><b>A blocked prompt has no candidate.</b> The response then carries only {@code
 *       promptFeedback}, which is surfaced as a {@link StopReason#CONTENT_FILTERED} message
 *       explaining the block instead of an empty message or an exception.
 * </ul>
 *
 * <p>Every result is a {@link ChatResult.Completed}: Gemini has no equivalent of Anthropic's {@code
 * pause_turn}. {@code FinishReason.Known} contains no paused/continuation value and nothing else in
 * the SDK models a turn the provider expects to be resumed without new input, so {@link
 * ChatResult.Continuation} is never produced.
 *
 * <p>The candidate's parts are read via {@code candidates().get(0).content().parts()} rather than
 * the {@link GenerateContentResponse#parts()}/{@code text()}/{@code functionCalls()} convenience
 * accessors on purpose: those call {@code checkFinishReason()} internally, which throws for every
 * finish reason outside {@code {FINISH_REASON_UNSPECIFIED, STOP, MAX_TOKENS}} — i.e. exactly for
 * the truncated and filtered responses this converter most needs to convert.
 */
public class GeminiContentResponseConverter {

  private static final String FINISH_REASON_METADATA_KEY = "finishReason";
  private static final String BLOCK_REASON_METADATA_KEY = "blockReason";

  public ChatResult toResult(GenerateContentResponse response, Duration executionTime) {
    final AssistantMessage assistantMessage = toAssistantMessage(response);
    final AgentMetrics metrics =
        toMetrics(response, assistantMessage.toolCalls().size(), executionTime);

    return new ChatResult.Completed(assistantMessage, metrics);
  }

  AssistantMessage toAssistantMessage(GenerateContentResponse response) {
    // Covers both shapes of "no candidate": the absent Optional a blocked prompt actually produces,
    // and a defensively handled empty list.
    final Candidate candidate =
        response
            .candidates()
            .filter(candidates -> !candidates.isEmpty())
            .map(List::getFirst)
            .orElse(null);
    if (candidate == null) {
      return blockedPromptMessage(response);
    }

    final List<Part> parts =
        candidate.content().flatMap(content -> content.parts()).orElse(List.of());
    final List<Content> content = new ArrayList<>();
    final List<ToolCall> toolCalls = new ArrayList<>();

    for (final Part part : parts) {
      final ToolCall toolCall = toToolCall(part);
      if (toolCall != null) {
        toolCalls.add(toolCall);
      } else {
        content.add(toContent(part));
      }
    }

    final FinishReason finishReason = candidate.finishReason().orElse(null);
    final Map<String, Object> geminiMetadata =
        finishReason != null
            ? Map.of(GOOGLE_GEMINI_ID, Map.of(FINISH_REASON_METADATA_KEY, finishReason.toString()))
            : Map.of();

    return assistantMessageBuilder(response)
        .content(content)
        .toolCalls(toolCalls)
        .stopReason(mapStopReason(finishReason, !toolCalls.isEmpty()))
        .metadata(AssistantMessageMetadata.withDefaults(geminiMetadata))
        .build();
  }

  /**
   * Builds the message for a response Gemini returned without any candidate, which is what a prompt
   * blocked by input-side filtering looks like. Never throws: the block reason (when reported) is
   * lifted into an explanatory {@link TextContent} so the blocked turn is visible in the
   * conversation rather than silently empty.
   */
  private AssistantMessage blockedPromptMessage(GenerateContentResponse response) {
    final String blockReason =
        response
            .promptFeedback()
            .flatMap(GenerateContentResponsePromptFeedback::blockReason)
            .map(BlockedReason::toString)
            .orElse(null);

    final String text =
        blockReason != null
            ? "Prompt blocked: " + blockReason
            : "Prompt blocked (no block reason reported)";
    final Map<String, Object> geminiMetadata =
        blockReason != null
            ? Map.of(GOOGLE_GEMINI_ID, Map.of(BLOCK_REASON_METADATA_KEY, blockReason))
            : Map.of();

    return assistantMessageBuilder(response)
        .content(List.of(TextContent.textContent(text)))
        .toolCalls(List.of())
        .stopReason(StopReason.CONTENT_FILTERED)
        .metadata(AssistantMessageMetadata.withDefaults(geminiMetadata))
        .build();
  }

  private AssistantMessageBuilder assistantMessageBuilder(GenerateContentResponse response) {
    return AssistantMessage.builder()
        .messageId(response.responseId().orElse(null))
        .modelId(response.modelVersion().orElse(null));
  }

  /**
   * Converts a {@code functionCall} part into a {@link ToolCall}, or returns {@code null} when the
   * part is not one the agent loop can execute as a tool call — including a {@code functionCall}
   * without a {@code name}, which the caller then preserves as {@link ProviderContent} instead of
   * emitting a nameless, unexecutable tool call.
   */
  private @Nullable ToolCall toToolCall(Part part) {
    final FunctionCall functionCall = part.functionCall().orElse(null);
    if (functionCall == null) {
      return null;
    }
    final String name = functionCall.name().orElse(null);
    if (name == null) {
      return null;
    }

    return new ToolCall(
        toolCallId(functionCall),
        name,
        functionCall.args().orElse(Map.of()),
        toolCallMetadata(part));
  }

  /**
   * Gemini's Developer API leaves {@code FunctionCall.id} unset (it is populated on other surfaces,
   * e.g. Vertex AI), while the domain {@link ToolCall} requires an id: the agent loop correlates
   * each {@code ToolCallResult} back to its originating call by id, and {@code
   * GeminiContentRequestConverter} echoes that id on every replayed turn of the conversation.
   *
   * <p>A random UUID is therefore synthesized rather than a value derived from the function name
   * and part index: a derived id would be unique within the response but would repeat across turns,
   * putting duplicate {@code functionCall}/{@code functionResponse} ids into a single replayed
   * request. This matches what langchain4j's own {@code google-genai} integration does against the
   * same SDK, and what {@code ToolCallConverterImpl} does for a blank framework-provided id.
   */
  private String toolCallId(FunctionCall functionCall) {
    return functionCall.id().orElseGet(() -> UUID.randomUUID().toString());
  }

  /**
   * Preserves a {@code thoughtSignature} carried on a {@code functionCall} part — Gemini 3 rejects
   * a follow-up tool-calling request whose history dropped it. Namespaced under the provider id,
   * mirroring how the langchain4j path persists the same value onto {@link ToolCall#metadata()}
   * (see {@code ToolCallMetadataDecorator}), so switching a running instance's provider cannot leak
   * one provider's metadata into another. {@link ReasoningContent#metadata()} needs no such
   * namespace: it carries the provider as a first-class field.
   */
  private @Nullable Map<String, Object> toolCallMetadata(Part part) {
    return part.thoughtSignature()
        .<Map<String, Object>>map(
            signature ->
                Map.of(
                    GOOGLE_GEMINI_ID,
                    Map.of(THOUGHT_SIGNATURE_METADATA_KEY, encodeSignature(signature))))
        .orElse(null);
  }

  private Content toContent(Part part) {
    if (part.thought().orElse(false)) {
      return toReasoningContent(part);
    }

    final String text = part.text().orElse(null);
    if (text != null) {
      return TextContent.textContent(text);
    }

    // Fallback for any Gemini part shape not explicitly handled above: preserve it losslessly, in
    // original order, as ProviderContent
    return new ProviderContent(GOOGLE_GEMINI_ID, rawPart(part), null);
  }

  /**
   * Splits a thinking part into the three places {@link GeminiContentConverter#toParts(List)} reads
   * it back from on replay: the thinking text into {@code text}, the {@code thoughtSignature}
   * base64-encoded into {@code metadata}, and whatever remains of the raw part into {@code payload}
   * so nothing is dropped.
   */
  private ReasoningContent toReasoningContent(Part part) {
    final Map<String, Object> raw = new LinkedHashMap<>(rawPart(part));
    raw.remove("thoughtSignature");

    final String text = part.text().filter(StringUtils::hasText).orElse(null);
    if (text != null) {
      raw.remove("text");
    }

    final Map<String, Object> metadata =
        part.thoughtSignature()
            .<Map<String, Object>>map(
                signature -> Map.of(THOUGHT_SIGNATURE_METADATA_KEY, encodeSignature(signature)))
            .orElse(null);

    return new ReasoningContent(GOOGLE_GEMINI_ID, raw, text, metadata);
  }

  /**
   * Serializes a {@link Part} through the Gemini SDK's own {@link JsonSerializable#objectMapper()}
   * rather than an injected {@code ObjectMapper} — {@link Part}'s AutoValue fields are all {@code
   * Optional<...>}, which needs the SDK's {@code Jdk8Module} registration to (de)serialize as bare
   * values. This is the exact mapper {@code GeminiContentConverter} converts the payload back with,
   * and mirrors {@code AnthropicMessageResponseConverter} using the Anthropic SDK's mapper for the
   * equivalent lossless round trip.
   */
  private Map<String, Object> rawPart(Part part) {
    return JsonSerializable.objectMapper()
        .convertValue(part, new TypeReference<Map<String, Object>>() {});
  }

  private String encodeSignature(byte[] signature) {
    return Base64.getEncoder().encodeToString(signature);
  }

  private AgentMetrics toMetrics(
      GenerateContentResponse response, int toolCalls, Duration executionTime) {
    final Optional<GenerateContentResponseUsageMetadata> usage = response.usageMetadata();
    final var tokenUsage =
        AgentMetrics.TokenUsage.builder()
            .inputTokenCount(
                usage.flatMap(GenerateContentResponseUsageMetadata::promptTokenCount).orElse(0))
            .outputTokenCount(
                usage.flatMap(GenerateContentResponseUsageMetadata::candidatesTokenCount).orElse(0))
            // Gemini's implicit caching reports cache reads only; there is no cache-write counter,
            // so cacheCreationTokenCount stays at its default of 0.
            .cacheReadTokenCount(
                usage
                    .flatMap(GenerateContentResponseUsageMetadata::cachedContentTokenCount)
                    .orElse(0))
            .reasoningTokenCount(
                usage.flatMap(GenerateContentResponseUsageMetadata::thoughtsTokenCount).orElse(0))
            .build();

    return AgentMetrics.builder()
        .modelCalls(1)
        .toolCalls(toolCalls)
        .tokenUsage(tokenUsage)
        .executionTime(executionTime)
        .build();
  }

  /**
   * Normalizes Gemini's finish reason, overriding it with {@link StopReason#TOOL_USE} whenever the
   * response produced tool calls (Gemini has no tool-use finish reason of its own). The override is
   * applied before the null check so that a response reporting no finish reason at all still
   * surfaces as {@code TOOL_USE} when it carries function calls.
   */
  private @Nullable StopReason mapStopReason(
      @Nullable FinishReason finishReason, boolean hasToolCalls) {
    if (hasToolCalls) {
      return StopReason.TOOL_USE;
    }
    if (finishReason == null) {
      return null;
    }

    return switch (finishReason.knownEnum()) {
      case STOP -> StopReason.STOP;
      case MAX_TOKENS -> StopReason.LENGTH;
      case SAFETY,
          RECITATION,
          BLOCKLIST,
          PROHIBITED_CONTENT,
          SPII,
          IMAGE_SAFETY,
          IMAGE_PROHIBITED_CONTENT,
          IMAGE_RECITATION ->
          StopReason.CONTENT_FILTERED;
      // LANGUAGE, OTHER, MALFORMED_FUNCTION_CALL, UNEXPECTED_TOOL_CALL, NO_IMAGE and any value the
      // SDK does not recognise (knownEnum() collapses those to FINISH_REASON_UNSPECIFIED, while
      // toString() still returns the raw vendor string)
      default -> new StopReason.UnknownStopReason(finishReason.toString());
    };
  }
}
