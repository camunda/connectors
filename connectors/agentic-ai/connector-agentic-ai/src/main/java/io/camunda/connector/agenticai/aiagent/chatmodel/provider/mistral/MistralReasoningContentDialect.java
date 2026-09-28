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
 * Mistral's chunked {@code content} vocabulary: a {@code thinking} chunk, plus verbatim replay of
 * any other chunk type this endpoint's {@code ContentChunk} union additionally allows ({@code
 * reference}/{@code image_url}/{@code document_url}/{@code file}/{@code input_audio}) when it was
 * produced by this same provider instance.
 */
public final class MistralReasoningContentDialect implements OpenAiCompletionsContentDialect {

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

  // Payload shape alone isn't a unique signal across providers (Anthropic's raw thinking-block
  // payload also carries type=thinking after its own text extraction), so both the provider tag
  // and the shape must match.
  private boolean isChunkedReasoningContent(ReasoningContent reasoning, String providerId) {
    return providerId.equals(reasoning.provider())
        && reasoning.payload() instanceof Map<?, ?> payload
        && "thinking".equals(payload.get("type"));
  }

  // The allow-list (rather than "any non-thinking/text type") matters: a custom tool call is also
  // wrapped as ProviderContent elsewhere with an unrelated payload shape that must never be
  // replayed as a content chunk.
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

  // Whether `thinking` stays in the payload (see isThinkingReconstructible) depends on whether it
  // can be rebuilt byte-identical from the lifted-out text alone.
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

  // Only a single {"type":"text","text":...} item is safely reconstructible; anything else (extra
  // items or fields) must be kept verbatim instead of collapsed.
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
