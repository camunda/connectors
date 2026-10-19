/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.chatmodel.provider.openai.family.completions;

import com.fasterxml.jackson.core.type.TypeReference;
import com.openai.core.ObjectMappers;
import com.openai.core.http.StreamResponse;
import com.openai.helpers.ChatCompletionAccumulator;
import com.openai.models.chat.completions.ChatCompletion;
import com.openai.models.chat.completions.ChatCompletionChunk;
import com.openai.models.chat.completions.ChatCompletionMessage;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Drives a streamed OpenAI Chat Completions API response to a single, fully-assembled {@link
 * ChatCompletion}, equivalent to what the non-streaming API would have returned. Extracted as its
 * own seam (rather than inlined in the chat model API implementation) so tests can inject a canned
 * {@link ChatCompletion} without needing to feed a full, valid raw chunk sequence through the
 * vendor SDK's {@link ChatCompletionAccumulator}.
 */
@FunctionalInterface
public interface OpenAiCompletionsStreamAssembler {

  ChatCompletion assemble(StreamResponse<ChatCompletionChunk> stream);

  /**
   * Default implementation, backed by the vendor SDK's {@link ChatCompletionAccumulator}. The
   * accumulator drops streamed {@code annotations} (for example OpenRouter {@code url_citation}
   * entries produced by a server tool such as {@code openrouter:web_search}), so they are collected
   * from the raw deltas and re-attached to the assembled message.
   */
  static OpenAiCompletionsStreamAssembler accumulating() {
    return stream -> {
      final ChatCompletionAccumulator accumulator = ChatCompletionAccumulator.create();
      final List<ChatCompletionMessage.Annotation> annotations = new ArrayList<>();
      stream.stream()
          .forEach(
              chunk -> {
                accumulator.accumulate(chunk);
                chunk.choices().stream()
                    .filter(choice -> choice.index() == 0)
                    .forEach(choice -> collectAnnotations(choice.delta(), annotations));
              });
      return withAnnotations(accumulator.chatCompletion(), annotations);
    };
  }

  private static void collectAnnotations(
      ChatCompletionChunk.Choice.Delta delta, List<ChatCompletionMessage.Annotation> target) {
    final var raw = delta._additionalProperties().get("annotations");
    if (raw == null || raw.isMissing() || raw.isNull()) {
      return;
    }
    try {
      final var mapper = ObjectMappers.jsonMapper();
      final List<Map<String, Object>> entries =
          mapper.convertValue(raw, new TypeReference<List<Map<String, Object>>>() {});
      for (Map<String, Object> entry : entries) {
        target.add(mapper.convertValue(entry, ChatCompletionMessage.Annotation.class));
      }
    } catch (IllegalArgumentException e) {
      // a malformed annotation must not fail the model call
    }
  }

  private static ChatCompletion withAnnotations(
      ChatCompletion completion, List<ChatCompletionMessage.Annotation> annotations) {
    if (annotations.isEmpty() || completion.choices().isEmpty()) {
      return completion;
    }
    final var choices = new ArrayList<>(completion.choices());
    final var first = choices.get(0);
    choices.set(
        0,
        first.toBuilder()
            .message(first.message().toBuilder().annotations(annotations).build())
            .build());
    return completion.toBuilder().choices(choices).build();
  }
}
