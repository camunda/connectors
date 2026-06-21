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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.CreateAgentHistoryItemCommandStep1.AgentHistoryContent;
import io.camunda.client.api.command.CreateAgentHistoryItemCommandStep1.AgentHistoryMetrics;
import io.camunda.client.api.command.CreateAgentHistoryItemCommandStep1.AgentHistoryRole;
import io.camunda.client.api.command.CreateAgentHistoryItemCommandStep1.AgentHistoryToolCall;
import io.camunda.client.api.command.CreateAgentHistoryItemCommandStep1.CreateAgentHistoryItemFinalCommandStep;
import io.camunda.client.api.command.UpdateAgentInstanceCommandStep1.UpdateAgentInstanceCommandStep2;
import io.camunda.client.impl.response.DocumentReferenceResponseImpl;
import io.camunda.client.protocol.rest.DocumentMetadataResponse;
import io.camunda.client.protocol.rest.DocumentReference;
import io.camunda.client.protocol.rest.DocumentReference.CamundaDocumentTypeEnum;
import io.camunda.connector.agenticai.aiagent.model.AgentConversationTurn;
import io.camunda.connector.agenticai.aiagent.model.AgentExecutionContext;
import io.camunda.connector.agenticai.aiagent.model.AgentMetrics;
import io.camunda.connector.agenticai.autoconfigure.AgenticAiConnectorsConfigurationProperties.RetriesProperties;
import io.camunda.connector.agenticai.model.message.AssistantMessage;
import io.camunda.connector.agenticai.model.message.Message;
import io.camunda.connector.agenticai.model.message.ToolCallResultMessage;
import io.camunda.connector.agenticai.model.message.UserMessage;
import io.camunda.connector.agenticai.model.message.content.Content;
import io.camunda.connector.agenticai.model.message.content.DocumentContent;
import io.camunda.connector.agenticai.model.message.content.ObjectContent;
import io.camunda.connector.agenticai.model.message.content.TextContent;
import io.camunda.connector.agenticai.model.tool.ToolCall;
import io.camunda.connector.agenticai.model.tool.ToolCallResult;
import io.camunda.connector.agenticai.util.retry.CamundaApiRetry;
import io.camunda.connector.agenticai.util.retry.CamundaApiRetry.FailureReason;
import io.camunda.connector.agenticai.util.retry.CamundaApiRetry.Sleeper;
import io.camunda.connector.api.document.Document;
import io.camunda.connector.api.document.DocumentMetadata;
import io.camunda.connector.api.document.DocumentReference.CamundaDocumentReference;
import io.camunda.connector.api.document.DocumentReference.ExternalDocumentReference;
import io.camunda.connector.api.error.ConnectorException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CamundaAgentInstanceClient implements AgentInstanceClient {

  private static final Logger LOGGER = LoggerFactory.getLogger(CamundaAgentInstanceClient.class);

  private static final String NO_CONTENT_PLACEHOLDER = "No content";

  private final CamundaClient camundaClient;
  private final RetriesProperties retriesProperties;
  private final Sleeper sleeper;
  private final ObjectMapper objectMapper;

  public CamundaAgentInstanceClient(
      CamundaClient camundaClient,
      RetriesProperties retriesProperties,
      Sleeper sleeper,
      ObjectMapper objectMapper) {
    this.camundaClient = camundaClient;
    this.retriesProperties = retriesProperties;
    this.sleeper = sleeper;
    this.objectMapper = objectMapper;
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
      final AgentHistoryRole role = roleForInputMessage(message);
      final List<AgentHistoryContent> content = inputMessageContent(message);
      createHistoryItem(
          executionContext,
          agentInstanceKey.value(),
          role,
          content,
          turn.iterationKey(),
          null,
          null);
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
    createHistoryItem(
        executionContext,
        agentInstanceKey.value(),
        AgentHistoryRole.ASSISTANT,
        assistantContent(assistantMessage),
        turn.iterationKey(),
        assistantToolCalls(assistantMessage),
        historyMetrics(turn.metrics()));
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

  private AgentHistoryRole roleForInputMessage(Message message) {
    return switch (message) {
      case UserMessage ignored -> AgentHistoryRole.USER;
      case ToolCallResultMessage ignored -> AgentHistoryRole.TOOL_RESULT;
      default ->
          throw new IllegalArgumentException(
              "Unsupported input message type for history item: "
                  + message.getClass().getSimpleName());
    };
  }

  private List<AgentHistoryContent> inputMessageContent(Message message) {
    final List<AgentHistoryContent> content =
        switch (message) {
          case UserMessage userMessage -> contentBlocks(userMessage.content());
          case ToolCallResultMessage toolCallResultMessage ->
              toolResultContent(toolCallResultMessage.results());
          default ->
              throw new IllegalArgumentException(
                  "Unsupported input message type for history item: "
                      + message.getClass().getSimpleName());
        };
    return ensureNonEmpty(content);
  }

  private List<AgentHistoryContent> assistantContent(AssistantMessage assistantMessage) {
    // Tool-only turns yield an assistant message with empty content; the API rejects empty content,
    // so fall back to a placeholder block. Follow-up: confirm with engine team whether empty
    // content
    // is acceptable for tool-only assistant items.
    return ensureNonEmpty(contentBlocks(assistantMessage.content()));
  }

  private List<AgentHistoryContent> contentBlocks(@Nullable List<Content> contents) {
    if (contents == null) {
      return new ArrayList<>();
    }
    final List<AgentHistoryContent> blocks = new ArrayList<>(contents.size());
    for (final Content content : contents) {
      blocks.add(toHistoryContent(content));
    }
    return blocks;
  }

  private AgentHistoryContent toHistoryContent(Content content) {
    return switch (content) {
      case TextContent textContent -> AgentHistoryContent.text(textContent.text());
      case ObjectContent objectContent -> objectOrText(objectContent.content());
      case DocumentContent documentContent -> documentHistoryContent(documentContent);
    };
  }

  private List<AgentHistoryContent> toolResultContent(List<ToolCallResult> results) {
    final List<AgentHistoryContent> blocks = new ArrayList<>(results.size());
    for (final ToolCallResult result : results) {
      blocks.add(toolResultBlock(result.content()));
    }
    return blocks;
  }

  private AgentHistoryContent toolResultBlock(@Nullable Object resultContent) {
    if (resultContent == null) {
      return AgentHistoryContent.text(ToolCallResult.CONTENT_NO_RESULT);
    }
    if (resultContent instanceof String s) {
      return AgentHistoryContent.text(
          StringUtils.isBlank(s) ? ToolCallResult.CONTENT_NO_RESULT : s);
    }
    return objectOrText(resultContent);
  }

  @SuppressWarnings("unchecked")
  private AgentHistoryContent objectOrText(Object value) {
    if (value instanceof Map<?, ?> map) {
      return AgentHistoryContent.object((Map<String, Object>) map);
    }
    return AgentHistoryContent.text(toJson(value));
  }

  private String toJson(Object value) {
    if (value instanceof String s) {
      return s;
    }
    try {
      return objectMapper.writeValueAsString(value);
    } catch (JsonProcessingException e) {
      throw new ConnectorException(
          ERROR_CODE_AGENT_INSTANCE_HISTORY_ITEM_FAILED,
          "Failed to serialize history item content: " + e.getMessage(),
          e);
    }
  }

  private AgentHistoryContent documentHistoryContent(DocumentContent documentContent) {
    final Document document = documentContent.document();
    return switch (document.reference()) {
      case CamundaDocumentReference ref ->
          AgentHistoryContent.document(toDocumentReferenceResponse(ref, document.metadata()));
      // External document references are not yet representable as a document content block on the
      // engine; fall back to an object reference block (follow-up: team decision).
      case ExternalDocumentReference ref -> externalDocumentReferenceContent(ref);
      case null, default -> AgentHistoryContent.text("[document]");
    };
  }

  private AgentHistoryContent externalDocumentReferenceContent(ExternalDocumentReference ref) {
    // Build the map null-safely: AgentHistoryContent.object rejects a null map, and Map.of rejects
    // null values.
    final Map<String, Object> reference = new LinkedHashMap<>();
    if (ref.url() != null) {
      reference.put("url", ref.url());
    }
    if (ref.name() != null) {
      reference.put("name", ref.name());
    }
    return reference.isEmpty()
        ? AgentHistoryContent.text("[document]")
        : AgentHistoryContent.object(reference);
  }

  private DocumentReferenceResponseImpl toDocumentReferenceResponse(
      CamundaDocumentReference ref, @Nullable DocumentMetadata metadata) {
    final DocumentReference protocolRef =
        new DocumentReference()
            .camundaDocumentType(CamundaDocumentTypeEnum.CAMUNDA)
            .documentId(ref.getDocumentId())
            .storeId(ref.getStoreId());
    if (ref.getContentHash() != null) {
      protocolRef.contentHash(ref.getContentHash());
    }
    if (metadata != null) {
      protocolRef.metadata(toProtocolDocumentMetadata(metadata));
    }
    return new DocumentReferenceResponseImpl(protocolRef);
  }

  private DocumentMetadataResponse toProtocolDocumentMetadata(DocumentMetadata metadata) {
    final DocumentMetadataResponse protocolMetadata = new DocumentMetadataResponse();
    if (metadata.getFileName() != null) {
      protocolMetadata.fileName(metadata.getFileName());
    }
    if (metadata.getContentType() != null) {
      protocolMetadata.contentType(metadata.getContentType());
    }
    if (metadata.getSize() != null) {
      protocolMetadata.size(metadata.getSize());
    }
    if (metadata.getExpiresAt() != null) {
      protocolMetadata.expiresAt(metadata.getExpiresAt().toString());
    }
    if (metadata.getProcessDefinitionId() != null) {
      protocolMetadata.processDefinitionId(metadata.getProcessDefinitionId());
    }
    if (metadata.getProcessInstanceKey() != null) {
      protocolMetadata.processInstanceKey(String.valueOf(metadata.getProcessInstanceKey()));
    }
    if (metadata.getCustomProperties() != null && !metadata.getCustomProperties().isEmpty()) {
      protocolMetadata.customProperties(metadata.getCustomProperties());
    }
    return protocolMetadata;
  }

  private @Nullable List<AgentHistoryToolCall> assistantToolCalls(
      AssistantMessage assistantMessage) {
    final List<ToolCall> toolCalls = assistantMessage.toolCalls();
    if (toolCalls == null || toolCalls.isEmpty()) {
      return null;
    }
    final List<AgentHistoryToolCall> historyToolCalls = new ArrayList<>(toolCalls.size());
    for (final ToolCall toolCall : toolCalls) {
      historyToolCalls.add(
          new AgentHistoryToolCall()
              .toolCallId(toolCall.id())
              .toolName(toolCall.name())
              .arguments(toolCall.arguments()));
    }
    return historyToolCalls;
  }

  private AgentHistoryMetrics historyMetrics(AgentMetrics metrics) {
    final long durationMs =
        metrics.executionTime() != null ? metrics.executionTime().toMillis() : 0;
    return new AgentHistoryMetrics()
        .inputTokens(metrics.tokenUsage().inputTokenCount())
        .outputTokens(metrics.tokenUsage().outputTokenCount())
        .durationMs(durationMs);
  }

  private List<AgentHistoryContent> ensureNonEmpty(List<AgentHistoryContent> content) {
    if (content.isEmpty()) {
      content.add(AgentHistoryContent.text(NO_CONTENT_PLACEHOLDER));
    }
    return content;
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
