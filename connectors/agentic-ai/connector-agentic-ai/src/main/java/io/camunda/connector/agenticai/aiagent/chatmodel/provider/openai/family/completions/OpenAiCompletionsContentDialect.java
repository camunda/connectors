/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.chatmodel.provider.openai.family.completions;

import io.camunda.connector.agenticai.aiagent.model.message.content.Content;
import java.util.Map;
import java.util.Optional;

/**
 * Provider-specific hook for a Chat Completions {@code content} shape beyond OpenAI's own plain
 * string: some OpenAI-compatible endpoints send, and expect to have replayed, a {@code content}
 * array of typed chunks instead. Whether a given message's {@code content} is a plain string or a
 * chunked array is detected from the raw wire shape itself, unconditionally, by {@link
 * OpenAiCompletionsRequestConverter} and {@link OpenAiCompletionsResponseConverter}; this interface
 * only supplies what a provider that uses such chunks needs in order to build and interpret its own
 * chunk types. The default, {@link #none()}, never recognizes anything, matching plain OpenAI's
 * always-string content.
 */
public interface OpenAiCompletionsContentDialect {

  /**
   * If {@code content} is one this dialect replays verbatim as a chunk when rebuilding an assistant
   * message's {@code content} as a chunked array (see {@link
   * OpenAiCompletionsRequestConverter#assistantMessage}), the chunk to replay it as; empty if this
   * dialect doesn't recognize it, so the caller falls back to a generic text chunk.
   */
  Optional<Map<String, Object>> toReplayableChunk(Content content, String providerId);

  /**
   * Maps one decoded chunk of a response's chunked {@code content} array (see {@link
   * OpenAiCompletionsResponseConverter#mapChunkedContent}) to domain {@link Content} if it is one
   * of this dialect's own chunk types; empty if this dialect doesn't recognize it, so the caller
   * falls back to the generic {@code text}/other handling.
   */
  Optional<Content> mapChunk(Map<String, Object> raw, String providerId);

  /** Never recognizes anything: plain OpenAI's {@code content} is always a string. */
  static OpenAiCompletionsContentDialect none() {
    return None.INSTANCE;
  }

  /** No-op implementation backing {@link #none()}. */
  enum None implements OpenAiCompletionsContentDialect {
    INSTANCE;

    @Override
    public Optional<Map<String, Object>> toReplayableChunk(Content content, String providerId) {
      return Optional.empty();
    }

    @Override
    public Optional<Content> mapChunk(Map<String, Object> raw, String providerId) {
      return Optional.empty();
    }
  }
}
