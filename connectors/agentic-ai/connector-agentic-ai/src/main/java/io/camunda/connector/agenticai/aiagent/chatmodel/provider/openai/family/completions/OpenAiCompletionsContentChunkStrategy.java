/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.chatmodel.provider.openai.family.completions;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openai.models.chat.completions.ChatCompletionContentPart;
import io.camunda.connector.agenticai.aiagent.model.message.content.Content;
import java.util.List;

/**
 * Converts the domain {@link Content} model to Chat Completions content parts ({@link
 * ChatCompletionContentPart}) for a user/assistant message body. One implementation per provider
 * rather than one shared method with a provider check inside it: this is a genuine, outbound
 * wire-format choice each implementation makes, with no self-detectable signal to key off the way
 * the chunked reasoning-content case has.
 */
@FunctionalInterface
public interface OpenAiCompletionsContentChunkStrategy {

  List<ChatCompletionContentPart> toContentParts(List<Content> content);

  static OpenAiCompletionsContentChunkStrategy openAi(ObjectMapper objectMapper) {
    return new OpenAiFileContentChunkStrategy(objectMapper);
  }
}
