/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.systemprompt;

import io.camunda.connector.agenticai.aiagent.chatmodel.ChatModelConfiguration;
import io.camunda.connector.agenticai.aiagent.model.AgentContext;
import io.camunda.connector.agenticai.aiagent.model.AgentExecutionContext;
import io.camunda.connector.agenticai.aiagent.model.request.ResponseConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.ResponseFormatConfiguration.JsonResponseFormatConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.v2.AnthropicChatModelConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.v2.BedrockConverseChatModelConfiguration;
import org.jspecify.annotations.Nullable;

/**
 * Adds a JSON instruction to the system prompt when JSON response format is requested without a
 * schema on a provider with no native schema-less JSON mode (Anthropic, Bedrock Converse).
 */
public class SchemaOnlyJsonResponseFormatSystemPromptContributor
    implements SystemPromptContributor {

  static final String INSTRUCTION =
      "Respond only with a single valid JSON value. Do not include any explanatory text, "
          + "markdown code fences, or commentary outside the JSON.";

  @Override
  public @Nullable String contribute(
      AgentExecutionContext executionContext, AgentContext agentContext) {
    final var configuration = executionContext.configuration();
    if (!requestsSchemaLessJson(configuration.response())
        || !isSchemaOnlyProvider(configuration.chatModel())) {
      return null;
    }
    return INSTRUCTION;
  }

  private static boolean requestsSchemaLessJson(@Nullable ResponseConfiguration response) {
    return response != null
        && response.format() instanceof JsonResponseFormatConfiguration json
        && !json.hasSchema();
  }

  private static boolean isSchemaOnlyProvider(ChatModelConfiguration chatModel) {
    return chatModel instanceof AnthropicChatModelConfiguration
        || chatModel instanceof BedrockConverseChatModelConfiguration;
  }

  /** Runs after every other contributor, so its instruction always closes the system prompt. */
  @Override
  public int getOrder() {
    return Integer.MAX_VALUE;
  }
}
