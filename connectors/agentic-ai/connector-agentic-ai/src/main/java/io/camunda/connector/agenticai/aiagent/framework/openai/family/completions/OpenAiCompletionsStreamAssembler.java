/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.framework.openai.family.completions;

import com.openai.core.http.StreamResponse;
import com.openai.helpers.ChatCompletionAccumulator;
import com.openai.models.chat.completions.ChatCompletion;
import com.openai.models.chat.completions.ChatCompletionChunk;

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

  /** Default implementation, backed by the vendor SDK's {@link ChatCompletionAccumulator}. */
  static OpenAiCompletionsStreamAssembler accumulating() {
    return stream -> {
      final ChatCompletionAccumulator accumulator = ChatCompletionAccumulator.create();
      stream.stream().forEach(accumulator::accumulate);
      return accumulator.chatCompletion();
    };
  }
}
