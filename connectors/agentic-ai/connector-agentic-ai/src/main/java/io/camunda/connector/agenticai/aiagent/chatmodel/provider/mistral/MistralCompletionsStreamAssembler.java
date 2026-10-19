/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.chatmodel.provider.mistral;

import com.openai.core.http.StreamResponse;
import com.openai.models.chat.completions.ChatCompletion;
import com.openai.models.chat.completions.ChatCompletionChunk;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.openai.family.completions.OpenAiCompletionsStreamAssembler;

/**
 * {@link OpenAiCompletionsStreamAssembler} backed by {@link
 * ChunkedContentChatCompletionAccumulator}, which additionally accumulates a chunked {@code
 * content} array instead of failing on it.
 */
public final class MistralCompletionsStreamAssembler implements OpenAiCompletionsStreamAssembler {

  @Override
  public ChatCompletion assemble(StreamResponse<ChatCompletionChunk> stream) {
    final ChunkedContentChatCompletionAccumulator accumulator =
        ChunkedContentChatCompletionAccumulator.create();
    stream.stream().forEach(accumulator::accumulate);
    return accumulator.chatCompletion();
  }
}
