/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.framework;

import io.camunda.connector.agenticai.aiagent.framework.api.ChatClient;
import io.camunda.connector.agenticai.aiagent.framework.api.ChatModelApiRegistry;
import io.camunda.connector.agenticai.aiagent.framework.api.ChatOptions;
import io.camunda.connector.agenticai.aiagent.framework.api.ChatRequest;
import io.camunda.connector.agenticai.aiagent.framework.api.ChatResponse;
import io.camunda.connector.agenticai.aiagent.framework.api.ChatStreamListener;
import io.camunda.connector.agenticai.aiagent.framework.api.ResponseFormat;
import io.camunda.connector.agenticai.aiagent.memory.runtime.RuntimeMemory;
import io.camunda.connector.agenticai.aiagent.model.AgentContext;
import io.camunda.connector.agenticai.aiagent.model.AgentExecutionContext;
import io.camunda.connector.agenticai.aiagent.model.request.ResponseConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.ResponseFormatConfiguration.JsonResponseFormatConfiguration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.springframework.lang.Nullable;

public class ChatClientImpl implements ChatClient {

  private final ChatModelApiRegistry registry;

  public ChatClientImpl(ChatModelApiRegistry registry) {
    this.registry = registry;
  }

  @Override
  public CompletableFuture<ChatResponse> chat(
      AgentExecutionContext executionContext,
      AgentContext agentContext,
      RuntimeMemory runtimeMemory,
      ChatStreamListener listener) {
    try {
      final var api = registry.resolve(executionContext.provider());
      final var request =
          new ChatRequest(runtimeMemory.filteredMessages(), null, agentContext.toolDefinitions());
      final var options =
          new ChatOptions(
              null, null, null, toResponseFormat(executionContext.response()), Map.of());
      return api.complete(request, options, listener != null ? listener : ChatStreamListener.NOOP);
    } catch (RuntimeException e) {
      return CompletableFuture.failedFuture(e);
    }
  }

  private static @Nullable ResponseFormat toResponseFormat(
      @Nullable ResponseConfiguration response) {
    if (response == null || response.format() == null) {
      return null;
    }
    if (response.format() instanceof JsonResponseFormatConfiguration json) {
      return new ResponseFormat.Json(json.schemaName(), json.schema());
    }
    // TextResponseFormatConfiguration → null preserves "no explicit format on the wire"
    // behaviour. Implementations choose their default text mode.
    return null;
  }
}
