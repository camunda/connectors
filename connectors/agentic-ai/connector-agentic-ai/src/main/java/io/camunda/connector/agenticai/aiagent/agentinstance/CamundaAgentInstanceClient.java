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

import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.AgentInstanceHistoryContent;
import io.camunda.client.api.command.AgentInstanceHistoryMetrics;
import io.camunda.client.api.command.AgentInstanceHistoryToolCall;
import io.camunda.client.api.command.CreateAgentHistoryItemCommandStep1.CreateAgentHistoryItemFinalCommandStep;
import io.camunda.client.api.command.ProblemException;
import io.camunda.client.api.command.UpdateAgentInstanceCommandStep1.UpdateAgentInstanceCommandStep2;
import io.camunda.client.api.search.enums.AgentInstanceHistoryRole;
import io.camunda.connector.agenticai.aiagent.model.AgentConversationTurn;
import io.camunda.connector.agenticai.aiagent.model.AgentExecutionContext;
import io.camunda.connector.agenticai.aiagent.model.message.AssistantMessage;
import io.camunda.connector.agenticai.aiagent.model.message.Message;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCall;
import io.camunda.connector.agenticai.autoconfigure.AgenticAiConnectorsConfigurationProperties.RetriesProperties;
import io.camunda.connector.agenticai.common.util.retry.CamundaApiRetry;
import io.camunda.connector.agenticai.common.util.retry.CamundaApiRetry.FailureReason;
import io.camunda.connector.agenticai.common.util.retry.CamundaApiRetry.Sleeper;
import io.camunda.connector.api.error.ConnectorException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CamundaAgentInstanceClient implements AgentInstanceClient {

  private static final Logger LOGGER = LoggerFactory.getLogger(CamundaAgentInstanceClient.class);

  private static final int HTTP_STATUS_CONFLICT = 409;

  /**
   * Matches the {@code detail} message of a {@code 409 ALREADY_EXISTS} response from {@code POST
   * /agent-instances}, capturing the existing agent instance key.
   *
   * <p>The engine's {@code AgentInstanceCreateProcessor} documents this message as a stable
   * contract: callers may parse it to extract the existing agent instance key, and wording changes
   * that alter the position of the embedded keys are a breaking contract change that must be kept
   * in sync with this pattern. The whole message is matched (not just a fragment), so an
   * unparseable or unexpectedly worded detail fails to match rather than risking extraction of the
   * wrong number.
   */
  private static final Pattern ALREADY_EXISTS_DETAIL_PATTERN =
      Pattern.compile(
          "Command 'CREATE' rejected with code 'ALREADY_EXISTS': Expected to associate element "
              + "instance with key '\\d+' with an agent instance, but it is already associated "
              + "with agent instance with key '(?<existingAgentInstanceKey>\\d+)'\\.");

  private final CamundaClient camundaClient;
  private final RetriesProperties retriesProperties;
  private final Sleeper sleeper;
  private final AgentInstanceHistoryMapper historyMapper;
  private final AgentInstanceToolMapper toolMapper;

  public CamundaAgentInstanceClient(
      CamundaClient camundaClient,
      RetriesProperties retriesProperties,
      Sleeper sleeper,
      AgentInstanceHistoryMapper historyMapper,
      AgentInstanceToolMapper toolMapper) {
    this.camundaClient = camundaClient;
    this.retriesProperties = retriesProperties;
    this.sleeper = sleeper;
    this.historyMapper = historyMapper;
    this.toolMapper = toolMapper;
  }

  @Override
  public AgentInstanceKey create(AgentExecutionContext agentExecutionContext) {
    return CamundaApiRetry.execute(
        () -> executeCreate(agentExecutionContext),
        AgentInstanceErrorClassifier.INSTANCE,
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
        configuration.chatModel().model(),
        configuration.chatModel().provider());

    var command =
        camundaClient
            .newCreateAgentInstanceCommand()
            .elementInstanceKey(elementInstanceKey)
            .model(configuration.chatModel().model())
            .provider(configuration.chatModel().provider())
            .systemPrompt(configuration.systemPrompt().prompt());

    final var limits = configuration.limits();
    if (limits != null && limits.maxModelCalls() != null) {
      command = command.maxModelCalls(limits.maxModelCalls());
    }

    try {
      final var key = AgentInstanceKey.of(command.execute().getAgentInstanceKey());
      LOGGER.debug(
          "Created agent instance {} for element instance {}", key.value(), elementInstanceKey);
      return key;
    } catch (ProblemException e) {
      if (e.code() == HTTP_STATUS_CONFLICT) {
        return handleAgentInstanceCreationConflict(elementInstanceKey, e);
      }
      throw e;
    }
  }

  /**
   * Handles a {@code 409 ALREADY_EXISTS} response from a create attempt by falling back to the
   * existing agent instance key embedded in the response {@code detail} message (there is no other
   * way to recover it from this response). If the detail cannot be parsed, the conflict cannot be
   * resolved and is raised as a failure instead of silently dropping the agent instance link.
   */
  private AgentInstanceKey handleAgentInstanceCreationConflict(
      long elementInstanceKey, ProblemException e) {
    final String detail = e.details().getDetail();
    return parseExistingAgentInstanceKey(detail)
        .map(
            existingAgentInstanceKey -> {
              LOGGER.debug(
                  "Agent instance creation conflicted for element instance {}, reusing existing agent instance {}",
                  elementInstanceKey,
                  existingAgentInstanceKey);
              return AgentInstanceKey.of(existingAgentInstanceKey);
            })
        .orElseThrow(
            () ->
                new IllegalStateException(
                    "Agent instance creation for element instance %d conflicted (%d ALREADY_EXISTS), but the existing agent instance key could not be parsed from the response detail: %s"
                        .formatted(elementInstanceKey, HTTP_STATUS_CONFLICT, detail),
                    e));
  }

  private Optional<Long> parseExistingAgentInstanceKey(@Nullable String detail) {
    if (detail == null) {
      return Optional.empty();
    }
    final var matcher = ALREADY_EXISTS_DETAIL_PATTERN.matcher(detail);
    return matcher.matches()
        ? Optional.of(Long.parseLong(matcher.group("existingAgentInstanceKey")))
        : Optional.empty();
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
        AgentInstanceErrorClassifier.INSTANCE,
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
        "Updating agent instance {}: status={}, delta={}, tools={}",
        agentInstanceKey,
        request.status(),
        request.delta(),
        request.tools() != null ? request.tools().size() : "null");
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

    final var tools = request.tools();
    if (tools != null && !tools.isEmpty()) {
      cmd = cmd.tools(toolMapper.mapTools(tools));
    }

    cmd.execute();
  }

  @Override
  public void createHistoryForInputMessages(
      AgentExecutionContext executionContext,
      @Nullable AgentInstanceKey agentInstanceKey,
      AgentConversationTurn turn,
      Optional<AgentConversationTurn> previousTurn,
      OffsetDateTime turnIngestionTimestamp) {
    if (agentInstanceKey == null) {
      LOGGER.debug("Skipping agent instance history items (before chat): no agent instance key");
      return;
    }
    final Map<String, ToolCall> toolCallsById =
        previousTurn.map(AgentConversationTurn::toolCallsById).orElse(Map.of());
    for (final Message message : turn.inputMessages()) {
      for (final var item :
          historyMapper.inputHistoryItems(message, toolCallsById, turnIngestionTimestamp)) {
        createHistoryItem(
            executionContext,
            agentInstanceKey.value(),
            item.role(),
            item.content(),
            turn.iterationKey(),
            item.toolCalls(),
            null,
            item.producedAt());
      }
    }
  }

  @Override
  public void createHistoryForAssistantMessage(
      AgentExecutionContext executionContext,
      @Nullable AgentInstanceKey agentInstanceKey,
      AgentConversationTurn turn,
      OffsetDateTime producedAt) {
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
        AgentInstanceHistoryRole.ASSISTANT,
        content,
        turn.iterationKey(),
        toolCalls,
        historyMapper.historyMetrics(turn.metrics()),
        producedAt);
  }

  private void createHistoryItem(
      AgentExecutionContext executionContext,
      long agentInstanceKey,
      AgentInstanceHistoryRole role,
      List<AgentInstanceHistoryContent> content,
      int iteration,
      @Nullable List<AgentInstanceHistoryToolCall> toolCalls,
      @Nullable AgentInstanceHistoryMetrics metrics,
      OffsetDateTime producedAt) {
    CamundaApiRetry.execute(
        () -> {
          executeCreateHistoryItem(
              executionContext,
              agentInstanceKey,
              role,
              content,
              iteration,
              toolCalls,
              metrics,
              producedAt);
          return null;
        },
        AgentInstanceErrorClassifier.INSTANCE,
        retriesProperties.maxRetries(),
        retriesProperties.initialRetryDelay(),
        this::buildHistoryItemException,
        sleeper);
  }

  private void executeCreateHistoryItem(
      AgentExecutionContext executionContext,
      long agentInstanceKey,
      AgentInstanceHistoryRole role,
      List<AgentInstanceHistoryContent> content,
      int iteration,
      @Nullable List<AgentInstanceHistoryToolCall> toolCalls,
      @Nullable AgentInstanceHistoryMetrics metrics,
      OffsetDateTime producedAt) {
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
            .producedAt(producedAt)
            .loopIteration(iteration);

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
