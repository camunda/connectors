/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.framework;

import io.camunda.connector.agenticai.aiagent.framework.api.ChatClient;
import io.camunda.connector.agenticai.aiagent.framework.api.ChatClientResult;
import io.camunda.connector.agenticai.aiagent.framework.api.ChatModelApiRegistry;
import io.camunda.connector.agenticai.aiagent.framework.api.ChatOptions;
import io.camunda.connector.agenticai.aiagent.framework.api.ChatRequest;
import io.camunda.connector.agenticai.aiagent.framework.api.ChatStreamListener;
import io.camunda.connector.agenticai.aiagent.memory.runtime.RuntimeMemory;
import io.camunda.connector.agenticai.aiagent.model.AgentContext;
import io.camunda.connector.agenticai.aiagent.model.AgentExecutionContext;
import io.camunda.connector.agenticai.aiagent.model.AgentMetrics;
import io.camunda.connector.agenticai.aiagent.model.request.ResponseConfiguration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletionException;

public class ChatClientImpl implements ChatClient {

  private final ChatModelApiRegistry registry;

  public ChatClientImpl(ChatModelApiRegistry registry) {
    this.registry = registry;
  }

  @Override
  public ChatClientResult chat(
      AgentExecutionContext executionContext,
      AgentContext agentContext,
      RuntimeMemory runtimeMemory,
      ChatStreamListener listener) {
    final var api = registry.resolve(executionContext.provider());
    final var request =
        new ChatRequest(
            runtimeMemory.filteredMessages(),
            agentContext.toolDefinitions(),
            Optional.ofNullable(executionContext.response())
                .map(ResponseConfiguration::format)
                .orElse(null));
    final var options = new ChatOptions(null, null, null, Map.of());

    final var chatResponse =
        joinChat(
            api.complete(request, options, listener != null ? listener : ChatStreamListener.NOOP));

    final var assistantMessage = chatResponse.assistantMessage();
    final var usage =
        assistantMessage.usage() != null
            ? assistantMessage.usage()
            : AgentMetrics.TokenUsage.empty();
    final var updatedAgentContext =
        agentContext.withMetrics(
            agentContext.metrics().incrementModelCalls(1).incrementTokenUsage(usage));

    return new ChatClientResult(updatedAgentContext, assistantMessage);
  }

  private static io.camunda.connector.agenticai.aiagent.framework.api.ChatResponse joinChat(
      java.util.concurrent.CompletableFuture<
              io.camunda.connector.agenticai.aiagent.framework.api.ChatResponse>
          future) {
    try {
      return future.join();
    } catch (CompletionException e) {
      // unwrap so callers see the original ConnectorException (or other RuntimeException) rather
      // than the CompletableFuture wrapper
      if (e.getCause() instanceof RuntimeException re) {
        throw re;
      }
      throw e;
    }
  }
}
