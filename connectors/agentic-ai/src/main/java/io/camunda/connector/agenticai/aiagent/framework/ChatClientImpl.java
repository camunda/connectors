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
import io.camunda.connector.agenticai.aiagent.framework.strategy.ToolCallResultStrategy;
import io.camunda.connector.agenticai.aiagent.memory.runtime.RuntimeMemory;
import io.camunda.connector.agenticai.aiagent.model.AgentContext;
import io.camunda.connector.agenticai.aiagent.model.AgentExecutionContext;
import io.camunda.connector.agenticai.aiagent.model.AgentMetrics;
import io.camunda.connector.agenticai.aiagent.model.request.ResponseConfiguration;
import io.camunda.connector.agenticai.model.message.ToolCallResultMessage;
import io.camunda.connector.agenticai.model.message.UserMessage;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletionException;

public class ChatClientImpl implements ChatClient {

  private final ChatModelApiRegistry registry;
  private final ToolCallResultStrategy toolCallResultStrategy;

  public ChatClientImpl(
      ChatModelApiRegistry registry, ToolCallResultStrategy toolCallResultStrategy) {
    this.registry = registry;
    this.toolCallResultStrategy = toolCallResultStrategy;
  }

  @Override
  public ChatClientResult chat(
      AgentExecutionContext executionContext,
      AgentContext agentContext,
      RuntimeMemory runtimeMemory,
      ChatStreamListener listener) {
    final var api = registry.resolve(executionContext.provider());
    final var capabilities = api.capabilities();

    final var initialRequest =
        new ChatRequest(
            runtimeMemory.filteredMessages(),
            agentContext.toolDefinitions(),
            Optional.ofNullable(executionContext.response())
                .map(ResponseConfiguration::format)
                .orElse(null));

    final var routed = toolCallResultStrategy.apply(initialRequest, capabilities);
    persistSyntheticContextMessages(runtimeMemory, routed.syntheticContextMessages());

    final var options = new ChatOptions(null, null, null, Map.of());

    final var chatResponse =
        joinChat(
            api.complete(
                routed.request(), options, listener != null ? listener : ChatStreamListener.NOOP));

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

  /**
   * Inserts synthetic context messages into {@link RuntimeMemory} immediately after the most recent
   * {@link ToolCallResultMessage}. Preserves the {@code [TCRM, syntheticUM, eventUMs]} order that
   * {@code MessageWindowRuntimeMemory}'s eviction relies on (synthetic UMs immediately follow their
   * anchor TCR so eviction co-removes them).
   *
   * <p>TODO(adr-005): the {@code clear() + addMessages(all)} dance is ugly. Replace with either an
   * {@code insertAfter(predicate, messages)} method on {@link RuntimeMemory}, or move synthetic-UM
   * insertion ownership to {@code AgentMessagesHandler} (with a deferred slot the strategy fills),
   * or fold {@code ChatClient}'s routing responsibility into {@code BaseAgentRequestHandler}. See
   * the {@code ChatClient}↔{@code BARQ} TODO in ADR-005 §"Tool Call Result Routing".
   */
  private static void persistSyntheticContextMessages(
      RuntimeMemory runtimeMemory, List<UserMessage> syntheticContextMessages) {
    if (syntheticContextMessages.isEmpty()) {
      return;
    }
    final var all = new ArrayList<>(runtimeMemory.allMessages());
    int anchorIdx = -1;
    for (int i = all.size() - 1; i >= 0; i--) {
      if (all.get(i) instanceof ToolCallResultMessage) {
        anchorIdx = i;
        break;
      }
    }
    if (anchorIdx < 0) {
      throw new IllegalStateException(
          "Strategy produced synthetic context messages but no ToolCallResultMessage exists "
              + "in runtime memory — strategy invariant violated");
    }
    all.addAll(anchorIdx + 1, syntheticContextMessages);
    runtimeMemory.clear();
    runtimeMemory.addMessages(all);
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
