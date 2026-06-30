/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.agentinstance;

import static io.camunda.connector.agenticai.aiagent.agent.AgentErrorCodes.ERROR_CODE_AGENT_INSTANCE_HISTORY_ITEM_FAILED;
import static java.util.Objects.requireNonNullElse;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.client.api.command.AgentInstanceHistoryContent;
import io.camunda.client.api.command.AgentInstanceHistoryMetrics;
import io.camunda.client.api.command.AgentInstanceHistoryToolCall;
import io.camunda.client.api.search.enums.AgentInstanceHistoryRole;
import io.camunda.client.impl.response.DocumentReferenceResponseImpl;
import io.camunda.client.protocol.rest.DocumentMetadataResponse;
import io.camunda.client.protocol.rest.DocumentReference;
import io.camunda.client.protocol.rest.DocumentReference.CamundaDocumentTypeEnum;
import io.camunda.connector.agenticai.aiagent.model.AgentMetrics;
import io.camunda.connector.agenticai.aiagent.model.message.AssistantMessage;
import io.camunda.connector.agenticai.aiagent.model.message.Message;
import io.camunda.connector.agenticai.aiagent.model.message.ToolCallResultMessage;
import io.camunda.connector.agenticai.aiagent.model.message.UserMessage;
import io.camunda.connector.agenticai.aiagent.model.message.content.Content;
import io.camunda.connector.agenticai.aiagent.model.message.content.DocumentContent;
import io.camunda.connector.agenticai.aiagent.model.message.content.ObjectContent;
import io.camunda.connector.agenticai.aiagent.model.message.content.TextContent;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCall;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCallResult;
import io.camunda.connector.agenticai.aiagent.tool.GatewayToolHandlerRegistry;
import io.camunda.connector.api.document.Document;
import io.camunda.connector.api.document.DocumentMetadata;
import io.camunda.connector.api.document.DocumentReference.CamundaDocumentReference;
import io.camunda.connector.api.document.DocumentReference.ExternalDocumentReference;
import io.camunda.connector.api.error.ConnectorException;
import io.camunda.connector.document.jackson.DocumentReferenceModel.ExternalDocumentReferenceModel;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.Nullable;

/**
 * Maps agentic-ai conversation messages ({@link Message}/{@link AssistantMessage}) into the Camunda
 * client agent-instance history item components ({@code AgentInstanceHistory*}). Pure
 * transformation logic: no API calls, retries or logging — those remain the responsibility of
 * {@link CamundaAgentInstanceClient}.
 */
public class AgentInstanceHistoryMapper {

  private final ObjectMapper objectMapper;
  private final GatewayToolHandlerRegistry gatewayToolHandlers;

  public AgentInstanceHistoryMapper(
      ObjectMapper objectMapper, GatewayToolHandlerRegistry gatewayToolHandlers) {
    this.objectMapper = objectMapper;
    this.gatewayToolHandlers = gatewayToolHandlers;
  }

  /**
   * A single history item to be created for an input message. A {@link UserMessage} maps to one
   * item; a {@link ToolCallResultMessage} maps to one item <em>per</em> tool call result, each
   * carrying a single-entry {@code toolCalls} list correlating it to the originating tool call.
   */
  public record InputHistoryItem(
      AgentInstanceHistoryRole role,
      List<AgentInstanceHistoryContent> content,
      @Nullable List<AgentInstanceHistoryToolCall> toolCalls) {}

  public List<InputHistoryItem> inputHistoryItems(
      Message message, Map<String, ToolCall> toolCallsById) {
    return switch (message) {
      case UserMessage userMessage ->
          List.of(
              new InputHistoryItem(
                  AgentInstanceHistoryRole.USER, contentBlocks(userMessage.content()), null));
      case ToolCallResultMessage toolCallResultMessage ->
          toolCallResultMessage.results().stream()
              .map(result -> toolResultHistoryItem(result, toolCallsById))
              .toList();
      default ->
          throw new IllegalArgumentException(
              "Unsupported input message type for history item: "
                  + message.getClass().getSimpleName());
    };
  }

  private InputHistoryItem toolResultHistoryItem(
      ToolCallResult result, Map<String, ToolCall> toolCallsById) {
    // tool-call result id/name are nullable on the model (and partial/malformed results may omit
    // them); default to empty strings, which the client model accepts
    return new InputHistoryItem(
        AgentInstanceHistoryRole.TOOL_RESULT,
        toolResultContent(result.content()),
        List.of(
            new AgentInstanceHistoryToolCall()
                .toolCallId(StringUtils.defaultString(result.id()))
                .toolName(StringUtils.defaultString(result.name()))
                .elementId(elementIdFor(result.elementId(), result.name()))
                .arguments(argumentsForResult(result, toolCallsById))));
  }

  /**
   * Resolves the originating request arguments for a tool-call result. Event results (no id) have
   * no originating tool call and carry empty arguments; a result with an id is expected to
   * correlate to a tool call in the previous turn, so a missing correlation is treated as an
   * invariant violation.
   */
  private Map<String, Object> argumentsForResult(
      ToolCallResult result, Map<String, ToolCall> toolCallsById) {
    if (result.id() == null) {
      return Map.of();
    }
    return Optional.ofNullable(toolCallsById.get(result.id()))
        .map(toolCall -> requireNonNullElse(toolCall.arguments(), Map.<String, Object>of()))
        .orElseThrow(
            () ->
                new IllegalArgumentException(
                    "No originating tool call found for tool call result with id '%s'"
                        .formatted(result.id())));
  }

  public List<AgentInstanceHistoryContent> assistantContent(AssistantMessage assistantMessage) {
    return contentBlocks(assistantMessage.content());
  }

  public @Nullable List<AgentInstanceHistoryToolCall> assistantToolCalls(
      AssistantMessage assistantMessage) {
    final List<ToolCall> toolCalls = assistantMessage.toolCalls();
    if (toolCalls == null || toolCalls.isEmpty()) {
      return null;
    }
    return toolCalls.stream()
        .map(
            toolCall ->
                new AgentInstanceHistoryToolCall()
                    .toolCallId(toolCall.id())
                    .toolName(toolCall.name())
                    .elementId(elementIdFor(null, toolCall.name()))
                    .arguments(toolCall.arguments()))
        .toList();
  }

  /**
   * Resolves the BPMN element id for a tool call history entry. Prefers an already-resolved {@code
   * elementId} carried on the model (tool call results); otherwise derives it from the (namespaced)
   * tool name via the gateway handlers, falling back to the name itself for ad-hoc tools.
   */
  private String elementIdFor(@Nullable String elementId, @Nullable String toolName) {
    if (elementId != null) {
      return elementId;
    }
    if (toolName == null) {
      throw new IllegalArgumentException(
          "Cannot resolve element id for a tool call history entry: both elementId and toolName are null");
    }
    return gatewayToolHandlers.resolveElementId(toolName).orElse(toolName);
  }

  public AgentInstanceHistoryMetrics historyMetrics(AgentMetrics metrics) {
    final long durationMs =
        metrics.executionTime() != null ? metrics.executionTime().toMillis() : 0;
    return new AgentInstanceHistoryMetrics()
        .inputTokens(metrics.tokenUsage().inputTokenCount())
        .outputTokens(metrics.tokenUsage().outputTokenCount())
        .durationMs(durationMs);
  }

  private List<AgentInstanceHistoryContent> contentBlocks(List<Content> contents) {
    return contents.stream().map(this::toHistoryContent).toList();
  }

  private AgentInstanceHistoryContent toHistoryContent(Content content) {
    return switch (content) {
      case TextContent textContent -> AgentInstanceHistoryContent.text(textContent.text());
      case ObjectContent objectContent -> objectOrText(objectContent.content());
      case DocumentContent documentContent -> documentHistoryContent(documentContent);
    };
  }

  private List<AgentInstanceHistoryContent> toolResultContent(@Nullable Object resultContent) {
    if (resultContent == null) {
      return List.of();
    }
    if (resultContent instanceof String s) {
      return StringUtils.isBlank(s) ? List.of() : List.of(AgentInstanceHistoryContent.text(s));
    }
    return List.of(objectOrText(resultContent));
  }

  @SuppressWarnings("unchecked")
  private AgentInstanceHistoryContent objectOrText(Object value) {
    if (value instanceof Map<?, ?> map) {
      return AgentInstanceHistoryContent.object((Map<String, Object>) map);
    }
    try {
      return AgentInstanceHistoryContent.object(objectMapper.convertValue(value, Map.class));
    } catch (IllegalArgumentException e) {
      return AgentInstanceHistoryContent.text(toJson(value));
    }
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

  private AgentInstanceHistoryContent documentHistoryContent(DocumentContent documentContent) {
    final Document document = documentContent.document();
    return switch (document.reference()) {
      case CamundaDocumentReference ref ->
          AgentInstanceHistoryContent.document(
              toDocumentReferenceResponse(ref, document.metadata()));
      // External document references are not yet representable as a document content block on the
      // engine; fall back to an object reference block (follow-up: team decision).
      case ExternalDocumentReference ref -> externalDocumentReferenceContent(ref);
      case null, default ->
          throw new IllegalArgumentException(
              "Unsupported document reference type for history item: "
                  + (document.reference() == null
                      ? "null"
                      : document.reference().getClass().getSimpleName()));
    };
  }

  private AgentInstanceHistoryContent externalDocumentReferenceContent(
      ExternalDocumentReference ref) {
    if (ref.url() == null || ref.name() == null) {
      throw new IllegalArgumentException(
          "External document reference requires both url and name for a history item");
    }
    return objectOrText(new ExternalDocumentReferenceModel(ref.url(), ref.name()));
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
}
