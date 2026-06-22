/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.framework.langchain4j;

import dev.langchain4j.model.chat.response.ChatResponse;
import io.camunda.connector.agenticai.aiagent.framework.AiFrameworkChatResponse;
import io.camunda.connector.agenticai.aiagent.model.AgentMetrics;
import io.camunda.connector.agenticai.model.message.AssistantMessage;
import java.time.Duration;

public record Langchain4JAiFrameworkChatResponse(
    AssistantMessage assistantMessage, AgentMetrics metrics, ChatResponse rawChatResponse)
    implements AiFrameworkChatResponse<ChatResponse> {

  @Override
  public Langchain4JAiFrameworkChatResponse withExecutionTimeMetrics(Duration executionTime) {
    return new Langchain4JAiFrameworkChatResponse(
        assistantMessage, metrics.withExecutionTime(executionTime), rawChatResponse);
  }
}
