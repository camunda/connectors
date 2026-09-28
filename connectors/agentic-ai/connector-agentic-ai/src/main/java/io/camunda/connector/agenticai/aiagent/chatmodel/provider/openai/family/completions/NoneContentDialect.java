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

enum NoneContentDialect implements OpenAiCompletionsContentDialect {
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
