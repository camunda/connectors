/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.agent;

import io.camunda.connector.agenticai.aiagent.agent.AgentInitializationResult.AgentContextInitializationResult;
import io.camunda.connector.agenticai.aiagent.agent.AgentInitializationResult.AgentDiscoveryInProgressInitializationResult;
import io.camunda.connector.agenticai.aiagent.agent.AgentInitializationResult.AgentResponseInitializationResult;
import io.camunda.connector.agenticai.aiagent.memory.conversation.ConversationSession;
import io.camunda.connector.agenticai.aiagent.model.AgentContext;
import io.camunda.connector.agenticai.aiagent.model.AgentExecutionContext;
import io.camunda.connector.agenticai.aiagent.model.AgentResponse;
import io.camunda.connector.agenticai.model.tool.ToolCallResult;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.Nullable;

public abstract class BaseAgentRequestHandler<C extends AgentExecutionContext, R>
    implements AgentRequestHandler<C, R> {

  private static final Logger LOGGER = LoggerFactory.getLogger(BaseAgentRequestHandler.class);

  private final AgentInitializer agentInitializer;
  private final AgentExecutor agentExecutor;

  public BaseAgentRequestHandler(AgentInitializer agentInitializer, AgentExecutor agentExecutor) {
    this.agentInitializer = agentInitializer;
    this.agentExecutor = agentExecutor;
  }

  @Override
  public R handleRequest(final C executionContext) {
    final var agentInitializationResult = agentInitializer.initializeAgent(executionContext);
    return switch (agentInitializationResult) {
      // directly return agent response if needed (e.g. tool discovery tool calls before calling the
      // LLM)
      case AgentResponseInitializationResult(AgentResponse agentResponse) -> {
        LOGGER.debug(
            "AI Agent initialization returned direct response including {} tool calls. Completing job without further processing.",
            agentResponse.toolCalls().size());
        yield completeJob(executionContext, agentResponse, null);
      }

      // discovery still in progress (not all tool call results present)
      case AgentDiscoveryInProgressInitializationResult ignored -> {
        LOGGER.debug(
            "AI Agent initialization tool discovery is still in progress. Completing job without further processing.");
        yield completeJob(executionContext, null, null);
      }

      case AgentContextInitializationResult(
              AgentContext agentContext,
              List<ToolCallResult> toolCallResults) -> {
        LOGGER.debug(
            "Handling agent request with {} tool call results",
            toolCallResults != null ? toolCallResults.size() : 0);

        final var result = agentExecutor.execute(executionContext, agentContext, toolCallResults);

        LOGGER.debug(
            "Request processing completed {} agent response, completing job",
            result.agentResponse() == null ? "without" : "with");

        yield completeJob(executionContext, result.agentResponse(), result.session());
      }
    };
  }

  /** Handles job completion if needed. Agent response and conversation session may be null. */
  protected abstract R completeJob(
      final C executionContext,
      @Nullable final AgentResponse agentResponse,
      @Nullable final ConversationSession session);
}
