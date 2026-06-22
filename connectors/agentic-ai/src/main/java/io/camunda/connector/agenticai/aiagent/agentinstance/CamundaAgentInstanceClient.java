/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.agentinstance;

import static io.camunda.connector.agenticai.aiagent.agent.AgentErrorCodes.ERROR_CODE_AGENT_INSTANCE_CREATION_FAILED;
import static io.camunda.connector.agenticai.aiagent.agent.AgentErrorCodes.ERROR_CODE_AGENT_INSTANCE_HISTORY_ITEM_FAILED;
import static io.camunda.connector.agenticai.aiagent.agent.AgentErrorCodes.ERROR_CODE_AGENT_INSTANCE_UPDATE_FAILED;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.CreateAgentHistoryItemCommandStep1.AgentHistoryContent;
import io.camunda.client.api.command.CreateAgentHistoryItemCommandStep1.AgentHistoryMetrics;
import io.camunda.client.api.command.CreateAgentHistoryItemCommandStep1.AgentHistoryRole;
import io.camunda.client.api.command.CreateAgentHistoryItemCommandStep1.AgentHistoryToolCall;
import io.camunda.client.api.command.CreateAgentHistoryItemCommandStep1.CreateAgentHistoryItemFinalCommandStep;
import io.camunda.client.api.command.UpdateAgentInstanceCommandStep1.UpdateAgentInstanceCommandStep2;
import io.camunda.connector.agenticai.aiagent.model.AgentConversationTurn;
import io.camunda.connector.agenticai.aiagent.model.AgentExecutionContext;
import io.camunda.connector.agenticai.aiagent.tool.GatewayToolHandlerRegistry;
import io.camunda.connector.agenticai.autoconfigure.AgenticAiConnectorsConfigurationProperties.RetriesProperties;
import io.camunda.connector.agenticai.model.message.AssistantMessage;
import io.camunda.connector.agenticai.model.message.Message;
import io.camunda.connector.agenticai.util.retry.CamundaApiRetry;
import io.camunda.connector.agenticai.util.retry.CamundaApiRetry.FailureReason;
import io.camunda.connector.agenticai.util.retry.CamundaApiRetry.Sleeper;
import io.camunda.connector.api.error.ConnectorException;
import java.time.OffsetDateTime;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CamundaAgentInstanceClient implements AgentInstanceClient {

  private static final Logger LOGGER = LoggerFactory.getLogger(CamundaAgentInstanceClient.class);

  private final CamundaClient camundaClient;
  private final RetriesProperties retriesProperties;
  private final Sleeper sleeper;
  private final AgentInstanceHistoryMapper historyMapper;

  public CamundaAgentInstanceClient(
      CamundaClient camundaClient,
      RetriesProperties retriesProperties,
      Sleeper sleeper,
      ObjectMapper objectMapper,
      GatewayToolHandlerRegistry gatewayToolHandlers) {
    this.camundaClient = camundaClient;
    this.retriesProperties = retriesProperties;
    this.sleeper = sleeper;
    this.historyMapper = new AgentInstanceHistoryMapper(objectMapper, gatewayToolHandlers);
  }

  @Override
  public AgentInstanceKey create(AgentExecutionContext agentExecutionContext) {
    return CamundaApiRetry.execute(
        () -> executeCreate(agentExecutionContext),
        AgentInstanceErrorClassifier.FOR_CREATE,
        retriesProperties.maxRetries(),
        retriesProperties.initialRetryDelay(),
        this::buildCreateException,
        sleeper);
  }

  private AgentInstanceKey executeCreate(AgentExecutionContext agentExecutionContext) {
    final long elementInstanceKey = agentExecutionContext.jobContext().getElementInstanceKey();
    final var configuration = agentExecutionContext.configuration();
    LOGGER.debug(
        "Creating agent instance for element instance {}: model={}, provider={}",
        elementInstanceKey,
        configuration.provider().model(),
        configuration.provider().providerType());

    var command =
        camundaClient
            .newCreateAgentInstanceCommand()
            .elementInstanceKey(elementInstanceKey)
            .model(configuration.provider().model())
            .provider(configuration.provider().providerType())
            .systemPrompt(configuration.systemPrompt().prompt());

    final var limits = configuration.limits();
    if (limits != null && limits.maxModelCalls() != null) {
      command = command.maxModelCalls(limits.maxModelCalls());
    }
    final var key = AgentInstanceKey.of(command.execute().getAgentInstanceKey());
    LOGGER.debug(
        "Created agent instance {} for element instance {}", key.value(), elementInstanceKey);
    return key;
  }

  @Override
  public void update(
      AgentExecutionContext executionContext,
      @Nullable AgentInstanceKey agentInstanceKey,
      AgentInstanceUpdateRequest request) {
    if (agentInstanceKey == null) {
      LOGGER.debug("Skipping agent instance update: no agent instance key");
      return;
    }
    CamundaApiRetry.execute(
        () -> {
          executeUpdate(executionContext, agentInstanceKey.value(), request);
          return null;
        },
        AgentInstanceErrorClassifier.FOR_UPDATE,
        retriesProperties.maxRetries(),
        retriesProperties.initialRetryDelay(),
        this::buildUpdateException,
        sleeper);
  }

  private void executeUpdate(
      AgentExecutionContext executionContext,
      long agentInstanceKey,
      AgentInstanceUpdateRequest request) {
    LOGGER.debug(
        "Updating agent instance {}: status={}, delta={}",
        agentInstanceKey,
        request.status(),
        request.delta());
    UpdateAgentInstanceCommandStep2 cmd =
        camundaClient
            .newUpdateAgentInstanceCommand(agentInstanceKey)
            .elementInstanceKey(executionContext.jobContext().getElementInstanceKey());

    if (request.status() != null) {
      cmd = cmd.status(request.status());
    }

    final var delta = request.delta();
    if (delta != null) {
      if (delta.modelCalls() != 0) {
        cmd = cmd.modelCalls(delta.modelCalls());
      }
      if (delta.tokenUsage().inputTokenCount() != 0) {
        cmd = cmd.inputTokens(delta.tokenUsage().inputTokenCount());
      }
      if (delta.tokenUsage().outputTokenCount() != 0) {
        cmd = cmd.outputTokens(delta.tokenUsage().outputTokenCount());
      }
      if (delta.toolCalls() != 0) {
        cmd = cmd.toolCalls(delta.toolCalls());
      }
    }

    cmd.execute();
  }

  @Override
  public void createHistoryForInputMessages(
      AgentExecutionContext executionContext,
      @Nullable AgentInstanceKey agentInstanceKey,
      AgentConversationTurn turn) {
    if (agentInstanceKey == null) {
      LOGGER.debug("Skipping agent instance history items (before chat): no agent instance key");
      return;
    }
    for (final Message message : turn.inputMessages()) {
      for (final var item : historyMapper.inputHistoryItems(message)) {
        createHistoryItem(
            executionContext,
            agentInstanceKey.value(),
            item.role(),
            item.content(),
            turn.iterationKey(),
            item.toolCalls(),
            null);
      }
    }
  }

  @Override
  public void createHistoryForAssistantMessage(
      AgentExecutionContext executionContext,
      @Nullable AgentInstanceKey agentInstanceKey,
      AgentConversationTurn turn) {
    if (agentInstanceKey == null) {
      LOGGER.debug("Skipping agent instance history item (after chat): no agent instance key");
      return;
    }
    final AssistantMessage assistantMessage = turn.assistantMessage();
    if (assistantMessage == null) {
      throw new IllegalArgumentException(
          "Cannot create assistant history item for a turn without an assistant message");
    }
    final var content = historyMapper.assistantContent(assistantMessage);
    final var toolCalls = historyMapper.assistantToolCalls(assistantMessage);
    if (content.isEmpty() && (toolCalls == null || toolCalls.isEmpty())) {
      throw new IllegalArgumentException(
          "Cannot create assistant history item with neither content nor tool calls");
    }
    createHistoryItem(
        executionContext,
        agentInstanceKey.value(),
        AgentHistoryRole.ASSISTANT,
        content,
        turn.iterationKey(),
        toolCalls,
        historyMapper.historyMetrics(turn.metrics()));
  }

  private void createHistoryItem(
      AgentExecutionContext executionContext,
      long agentInstanceKey,
      AgentHistoryRole role,
      List<AgentHistoryContent> content,
      int iteration,
      @Nullable List<AgentHistoryToolCall> toolCalls,
      @Nullable AgentHistoryMetrics metrics) {
    CamundaApiRetry.execute(
        () -> {
          executeCreateHistoryItem(
              executionContext, agentInstanceKey, role, content, iteration, toolCalls, metrics);
          return null;
        },
        AgentInstanceErrorClassifier.FOR_HISTORY_ITEM,
        retriesProperties.maxRetries(),
        retriesProperties.initialRetryDelay(),
        this::buildHistoryItemException,
        sleeper);
  }

  private void executeCreateHistoryItem(
      AgentExecutionContext executionContext,
      long agentInstanceKey,
      AgentHistoryRole role,
      List<AgentHistoryContent> content,
      int iteration,
      @Nullable List<AgentHistoryToolCall> toolCalls,
      @Nullable AgentHistoryMetrics metrics) {
    LOGGER.debug(
        "Creating agent instance {} history item: role={}, iteration={}, contentBlocks={}",
        agentInstanceKey,
        role,
        iteration,
        content.size());
    CreateAgentHistoryItemFinalCommandStep cmd =
        camundaClient
            .newCreateAgentHistoryItemCommand(agentInstanceKey)
            .elementInstanceKey(executionContext.jobContext().getElementInstanceKey())
            .jobKey(executionContext.jobContext().getJobKey())
            .role(role)
            .content(content)
            .producedAt(OffsetDateTime.now())
            .iteration(iteration);

    if (toolCalls != null && !toolCalls.isEmpty()) {
      cmd = cmd.toolCalls(toolCalls);
    }
    if (metrics != null) {
      cmd = cmd.metrics(metrics);
    }

    cmd.execute();
  }

  private ConnectorException buildCreateException(
      Throwable cause, int attempt, FailureReason reason) {
    final String message =
        switch (reason) {
          case PERMANENT_ERROR ->
              "Failed to create agent instance: %s".formatted(cause.getMessage());
          case RETRIES_EXHAUSTED ->
              "Failed to create agent instance after %d attempt(s): %s"
                  .formatted(attempt, cause.getMessage());
          case INTERRUPTED -> "Interrupted while waiting to retry agent instance creation";
        };
    return new ConnectorException(ERROR_CODE_AGENT_INSTANCE_CREATION_FAILED, message, cause);
  }

  private ConnectorException buildUpdateException(
      Throwable cause, int attempt, FailureReason reason) {
    final String message =
        switch (reason) {
          case PERMANENT_ERROR ->
              "Failed to update agent instance: %s".formatted(cause.getMessage());
          case RETRIES_EXHAUSTED ->
              "Failed to update agent instance after %d attempt(s): %s"
                  .formatted(attempt, cause.getMessage());
          case INTERRUPTED -> "Interrupted while waiting to retry agent instance update";
        };
    return new ConnectorException(ERROR_CODE_AGENT_INSTANCE_UPDATE_FAILED, message, cause);
  }

  private ConnectorException buildHistoryItemException(
      Throwable cause, int attempt, FailureReason reason) {
    final String message =
        switch (reason) {
          case PERMANENT_ERROR ->
              "Failed to create agent instance history item: %s".formatted(cause.getMessage());
          case RETRIES_EXHAUSTED ->
              "Failed to create agent instance history item after %d attempt(s): %s"
                  .formatted(attempt, cause.getMessage());
          case INTERRUPTED ->
              "Interrupted while waiting to retry agent instance history item creation";
        };
    return new ConnectorException(ERROR_CODE_AGENT_INSTANCE_HISTORY_ITEM_FAILED, message, cause);
  }
}
