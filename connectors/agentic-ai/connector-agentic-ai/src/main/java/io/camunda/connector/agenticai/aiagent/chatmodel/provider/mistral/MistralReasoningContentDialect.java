/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.chatmodel.provider.mistral;

import io.camunda.connector.agenticai.aiagent.chatmodel.provider.openai.family.completions.OpenAiCompletionsContentDialect;
import io.camunda.connector.agenticai.aiagent.model.message.content.Content;
import io.camunda.connector.agenticai.aiagent.model.message.content.ProviderContent;
import io.camunda.connector.agenticai.aiagent.model.message.content.ReasoningContent;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * Mistral's chunked {@code content} vocabulary for its reasoning-capable models: a {@code thinking}
 * chunk (Mistral's own reasoning type), plus verbatim replay of any other chunk type this
 * endpoint's {@code ContentChunk} union additionally allows ({@code reference}/{@code
 * image_url}/{@code document_url}/{@code file}/{@code input_audio}) when it was produced by this
 * same provider instance.
 */
public final class MistralReasoningContentDialect implements OpenAiCompletionsContentDialect {

  /**
   * The chunk types a response can wrap as a replayable {@link ProviderContent}: this endpoint's
   * {@code ContentChunk} union minus {@code thinking}/{@code text}, which are never wrapped as
   * {@link ProviderContent} in the first place.
   */
  private static final Set<String> REPLAYABLE_PROVIDER_CONTENT_CHUNK_TYPES =
      Set.of("reference", "image_url", "document_url", "file", "input_audio");

  @Override
  public Optional<Map<String, Object>> toReplayableChunk(Content content, String providerId) {
    if (content instanceof ReasoningContent reasoning
        && isChunkedReasoningContent(reasoning, providerId)) {
      return Optional.of(toChunkedThinkingChunk(reasoning));
    }
    if (content instanceof ProviderContent providerContent
        && isReplayableProviderContent(providerContent, providerId)) {
      return Optional.of(toChunkedProviderChunk(providerContent));
    }
    return Optional.empty();
  }

  @Override
  public Optional<Content> mapChunk(Map<String, Object> raw, String providerId) {
    if ("thinking".equals(raw.get("type"))) {
      return Optional.of(toReasoningContent(raw, providerId));
    }
    return Optional.empty();
  }

  /**
   * A {@link ReasoningContent} is only replayable as a chunked {@code thinking} chunk if it was
   * produced by this same provider ({@code provider} tag matches {@code providerId}) <em>and</em>
   * its payload is shaped the way {@link #toReasoningContent} produces it. Both checks are
   * required: payload shape alone isn't a unique signal -- Anthropic's raw thinking-block payload
   * also carries a {@code type: "thinking"} field after its own text extraction, so a
   * provider-tag-only or shape-only check would misclassify reasoning content replayed after a
   * mid-conversation provider switch.
   */
  private boolean isChunkedReasoningContent(ReasoningContent reasoning, String providerId) {
    return providerId.equals(reasoning.provider())
        && reasoning.payload() instanceof Map<?, ?> payload
        && "thinking".equals(payload.get("type"));
  }

  /**
   * A {@link ProviderContent} is only replayable as its original chunk if it was produced by this
   * same provider instance (mirrors {@link #isChunkedReasoningContent}) <em>and</em> its payload's
   * {@code type} is one of the other chunk types this dialect can wrap as {@link ProviderContent}
   * -- excluding {@code thinking}/{@code text}, which always become {@link ReasoningContent}/a
   * plain text content instead and never reach here as {@link ProviderContent}. The explicit
   * allow-list (rather than "any non-thinking/text type string") matters: the response converter
   * also produces a {@link ProviderContent} for a custom tool call, whose raw payload shape is
   * unrelated and must never be sent back as a content chunk.
   */
  private boolean isReplayableProviderContent(ProviderContent providerContent, String providerId) {
    return providerId.equals(providerContent.provider())
        && providerContent.payload() instanceof Map<?, ?> payload
        && payload.get("type") instanceof String type
        && REPLAYABLE_PROVIDER_CONTENT_CHUNK_TYPES.contains(type);
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> toChunkedProviderChunk(ProviderContent providerContent) {
    return new LinkedHashMap<>((Map<String, Object>) providerContent.payload());
  }

  /**
   * If {@link #toReasoningContent} left {@code thinking} in the payload (not byte-identically
   * reconstructible from {@link ReasoningContent#text()} alone), it is replayed verbatim instead.
   * Otherwise reinserts the lifted-out {@code text()} back into the payload's {@code thinking}
   * field, reconstructing the single-item chunk it was extracted from.
   */
  @SuppressWarnings("unchecked")
  private Map<String, Object> toChunkedThinkingChunk(ReasoningContent reasoning) {
    final Map<String, Object> payload = (Map<String, Object>) reasoning.payload();
    if (payload.containsKey("thinking")) {
      return new LinkedHashMap<>(payload);
    }
    final Map<String, Object> chunk = new LinkedHashMap<>(payload);
    chunk.put(
        "thinking",
        List.of(Map.of("type", "text", "text", reasoning.text() == null ? "" : reasoning.text())));
    return chunk;
  }

  /**
   * Lifts the readable text out of a {@code thinking} chunk's nested {@code thinking} array into
   * {@link ReasoningContent#text()}. Whether {@code thinking} is also stripped from {@code payload}
   * depends on {@link #isThinkingReconstructible}: if it holds, {@link #toChunkedThinkingChunk}
   * rebuilds {@code thinking} from {@code text()} before replay; otherwise {@code thinking} is left
   * untouched in {@code payload} -- deliberately duplicated with {@code text()} -- since
   * reconstructing it from a single joined string would silently drop extra items or per-item
   * fields a multi-item {@code thinking} array may carry.
   */
  private ReasoningContent toReasoningContent(Map<String, Object> raw, String providerId) {
    final Map<String, Object> payload = new LinkedHashMap<>(raw);
    final Object thinking = payload.get("thinking");
    final String text = extractThinkingText(thinking);
    if (isThinkingReconstructible(thinking)) {
      payload.remove("thinking");
    }
    return new ReasoningContent(providerId, payload, text, null);
  }

  private @Nullable String extractThinkingText(@Nullable Object thinking) {
    if (!(thinking instanceof List<?> items)) {
      return null;
    }
    final StringBuilder text = new StringBuilder();
    for (final Object item : items) {
      if (item instanceof Map<?, ?> map && map.get("text") instanceof String fragment) {
        text.append(fragment);
      }
    }
    return text.isEmpty() ? null : text.toString();
  }

  /**
   * Holds only when {@code thinking} can be reconstructed byte-identical from {@link
   * #extractThinkingText}'s joined result alone: exactly one item, itself exactly {@code
   * {"type":"text","text":...}} with no extra fields -- the single-chunk shape both the streaming
   * accumulator and a plain non-streaming response produce for the common case.
   */
  private boolean isThinkingReconstructible(@Nullable Object thinking) {
    if (!(thinking instanceof List<?> items) || items.size() != 1) {
      return false;
    }
    return items.get(0) instanceof Map<?, ?> item
        && item.size() == 2
        && "text".equals(item.get("type"))
        && item.get("text") instanceof String;
  }
}
