/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.agentinstance;

import static java.util.Objects.requireNonNullElse;

import io.camunda.client.api.command.AgentInstanceHistoryContent;
import io.camunda.client.api.command.AgentInstanceHistoryMetrics;
import io.camunda.client.api.command.AgentInstanceHistoryToolCall;
import io.camunda.client.api.search.enums.AgentInstanceHistoryRole;
import io.camunda.client.impl.response.DocumentReferenceResponseImpl;
import io.camunda.client.protocol.rest.DocumentMetadataResponse;
import io.camunda.client.protocol.rest.DocumentReference;
import io.camunda.client.protocol.rest.DocumentReference.CamundaDocumentTypeEnum;
import io.camunda.connector.agenticai.aiagent.document.ObjectHandleRenderer;
import io.camunda.connector.agenticai.aiagent.model.AgentMetrics;
import io.camunda.connector.agenticai.aiagent.model.document.AgentDocument;
import io.camunda.connector.agenticai.aiagent.model.document.AgentDocumentHandle;
import io.camunda.connector.agenticai.aiagent.model.document.AgentDocumentId;
import io.camunda.connector.agenticai.aiagent.model.document.DocumentHandling;
import io.camunda.connector.agenticai.aiagent.model.message.AssistantMessage;
import io.camunda.connector.agenticai.aiagent.model.message.Message;
import io.camunda.connector.agenticai.aiagent.model.message.ToolCallResultMessage;
import io.camunda.connector.agenticai.aiagent.model.message.UserMessage;
import io.camunda.connector.agenticai.aiagent.model.message.content.AgentDocumentContent;
import io.camunda.connector.agenticai.aiagent.model.message.content.Content;
import io.camunda.connector.agenticai.aiagent.model.message.content.DocumentContent;
import io.camunda.connector.agenticai.aiagent.model.message.content.ObjectContent;
import io.camunda.connector.agenticai.aiagent.model.message.content.ProviderContent;
import io.camunda.connector.agenticai.aiagent.model.message.content.ReasoningContent;
import io.camunda.connector.agenticai.aiagent.model.message.content.TextContent;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCall;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCallResult;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCallResultContent;
import io.camunda.connector.agenticai.aiagent.tool.GatewayToolHandlerRegistry;
import io.camunda.connector.api.document.Document;
import io.camunda.connector.api.document.DocumentMetadata;
import io.camunda.connector.api.document.DocumentReference.CamundaDocumentReference;
import io.camunda.connector.api.document.DocumentReference.ExternalDocumentReference;
import io.camunda.connector.document.jackson.DocumentReferenceModel.ExternalDocumentReferenceModel;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
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

  private final GatewayToolHandlerRegistry gatewayToolHandlers;
  private final ObjectHandleRenderer objectHandleRenderer;

  public AgentInstanceHistoryMapper(
      GatewayToolHandlerRegistry gatewayToolHandlers, ObjectHandleRenderer objectHandleRenderer) {
    this.gatewayToolHandlers = gatewayToolHandlers;
    this.objectHandleRenderer = objectHandleRenderer;
  }

  /**
   * A single history item to be created for an input message. A {@link UserMessage} maps to one
   * item; a {@link ToolCallResultMessage} maps to one item <em>per</em> tool call result, each
   * carrying a single-entry {@code toolCalls} list correlating it to the originating tool call.
   */
  public record InputHistoryItem(
      AgentInstanceHistoryRole role,
      List<AgentInstanceHistoryContent> content,
      @Nullable List<AgentInstanceHistoryToolCall> toolCalls,
      OffsetDateTime producedAt,
      String historyItemId) {}

  /**
   * Maps a conversation message to the agent instance input history items it produces.
   *
   * @param turnIngestionTimestamp the timestamp for non-tool-result items (e.g. a {@link
   *     UserMessage}), passed in by the caller so this mapper stays clock-free. Tool-call results
   *     use their own resolved {@link ToolCallResult#completedAt()} instead (see ADR 008).
   * @param toolCallsById the tool calls of the previous turn by id; a result of a local tool call
   *     is reported without a BPMN element
   * @param documents how the history of the job shows the documents of the agent
   */
  public List<InputHistoryItem> inputHistoryItems(
      Message message,
      Map<String, ToolCall> toolCallsById,
      OffsetDateTime turnIngestionTimestamp,
      HistoryDocuments documents) {
    return switch (message) {
      case UserMessage userMessage ->
          List.of(
              new InputHistoryItem(
                  AgentInstanceHistoryRole.USER,
                  messageContent(userMessage.content(), documents),
                  null,
                  turnIngestionTimestamp,
                  AgentInstanceHistoryItemIds.forMessage(userMessage)));
      case ToolCallResultMessage toolCallResultMessage ->
          toolCallResultMessage.results().stream()
              .map(result -> toolResultHistoryItem(result, toolCallsById, documents))
              .toList();
      default ->
          throw new IllegalArgumentException(
              "Unsupported input message type for history item: "
                  + message.getClass().getSimpleName());
    };
  }

  private InputHistoryItem toolResultHistoryItem(
      ToolCallResultContent result,
      Map<String, ToolCall> toolCallsById,
      HistoryDocuments documents) {
    // tool-call result id/name are nullable on the model (and partial/malformed results may omit
    // them); default to empty strings, which the client model accepts
    return new InputHistoryItem(
        AgentInstanceHistoryRole.TOOL_RESULT,
        messageContent(result.content(), documents),
        List.of(
            new AgentInstanceHistoryToolCall()
                .toolCallId(StringUtils.defaultString(result.id()))
                .toolName(StringUtils.defaultString(result.name()))
                .elementId(
                    elementIdFor(
                        result.elementId(),
                        result.name(),
                        result.id() == null ? null : toolCallsById.get(result.id())))
                .arguments(argumentsForResult(result, toolCallsById))),
        requireCompletedAt(result),
        AgentInstanceHistoryItemIds.forToolCallResult(result));
  }

  /**
   * Ingestion normalization (ADR 008) must resolve {@code completedAt} on every tool call result
   * before it reaches this mapper (engine timestamp, worker-observed time, or {@code now()} as last
   * resort) — a missing value here is an invariant violation, not a case to silently default.
   */
  private OffsetDateTime requireCompletedAt(ToolCallResultContent result) {
    final var completedAt = result.completedAt();
    if (completedAt == null) {
      throw new IllegalArgumentException(
          "Tool call result with id '%s' has no completedAt"
              .formatted(StringUtils.defaultString(result.id())));
    }
    return completedAt;
  }

  /**
   * Resolves the originating request arguments for a tool-call result. Event results (no id) have
   * no originating tool call and carry empty arguments; a result with an id is expected to
   * correlate to a tool call in the previous turn, so a missing correlation is treated as an
   * invariant violation.
   */
  private Map<String, Object> argumentsForResult(
      ToolCallResultContent result, Map<String, ToolCall> toolCallsById) {
    if (result.id() == null) {
      return Map.of();
    }
    return Optional.ofNullable(toolCallsById.get(result.id()))
        .map(toolCall -> requireNonNullElse(toolCall.arguments(), Map.<String, Object>of()))
        .orElseThrow(() ->
            new IllegalArgumentException(
                "No originating tool call found for tool call result with id '%s'"
                    .formatted(result.id())));
  }

  public List<AgentInstanceHistoryContent> assistantContent(AssistantMessage assistantMessage) {
    // the documents of an assistant message, if any, are live documents, as without tracking
    return contentBlocks(assistantMessage.content(), new DocumentHandling.Untracked());
  }

  public @Nullable List<AgentInstanceHistoryToolCall> assistantToolCalls(
      AssistantMessage assistantMessage) {
    final List<ToolCall> toolCalls = assistantMessage.toolCalls();
    if (toolCalls == null || toolCalls.isEmpty()) {
      return null;
    }
    return toolCalls.stream()
        .map(toolCall ->
            new AgentInstanceHistoryToolCall()
                .toolCallId(toolCall.id())
                .toolName(toolCall.name())
                .elementId(elementIdFor(null, toolCall.name(), toolCall))
                .arguments(toolCall.arguments()))
        .toList();
  }

  /**
   * Resolves the BPMN element id for a tool call history entry. Prefers an already-resolved {@code
   * elementId} carried on the model (tool call results); otherwise derives it from the (namespaced)
   * tool name via the gateway handlers, falling back to the name itself for ad-hoc tools. A call of
   * a runtime tool and a rejected call have no BPMN element: their element id is {@code null}.
   *
   * @param toolCall the originating tool call, {@code null} if it is not known
   */
  private @Nullable String elementIdFor(
      @Nullable String elementId, @Nullable String toolName, @Nullable ToolCall toolCall) {
    if (elementId != null) {
      return elementId;
    }
    if (toolName == null) {
      throw new IllegalArgumentException(
          "Cannot resolve element id for a tool call history entry: both elementId and toolName are null");
    }
    if (toolCall != null && toolCall.isLocal()) {
      return null;
    }
    return gatewayToolHandlers.resolveElementId(toolName).orElse(toolName);
  }

  public AgentInstanceHistoryMetrics historyMetrics(AgentMetrics metrics) {
    final long durationMs =
        metrics.executionTime() != null ? metrics.executionTime().toMillis() : 0;
    return new AgentInstanceHistoryMetrics()
        .inputTokens(metrics.tokenUsage().inputTokenCount())
        .outputTokens(metrics.tokenUsage().outputTokenCount())
        .reasoningTokenCount(metrics.tokenUsage().reasoningTokenCount())
        .cacheCreationTokenCount(metrics.tokenUsage().cacheCreationTokenCount())
        .cacheReadTokenCount(metrics.tokenUsage().cacheReadTokenCount())
        .durationMs(durationMs);
  }

  /**
   * The content of a message as the model reads it. For an agent instance that tracks its
   * documents, a document is its handle as an object block of its own, the same handle the model
   * gets, in a user message, an event and a tool result. Where the document enters the conversation
   * the handle is followed by the document itself (the chip); a document nested in an object is
   * replaced by its handle object, as the model gets it, and the documents follow the object. Text
   * is reported as it is, also when it holds a handle.
   */
  private List<AgentInstanceHistoryContent> messageContent(
      List<Content> contents, HistoryDocuments historyDocuments) {
    final var documentHandling = historyDocuments.handling();
    final var restorer = historyDocuments.restorer();
    return switch (documentHandling) {
      case DocumentHandling.Untracked ignored -> contentBlocks(contents, documentHandling);
      case DocumentHandling.Tracked tracked -> {
        final var documents = tracked.documents();
        final var blocks = new ArrayList<AgentInstanceHistoryContent>();
        for (final var content : contents) {
          switch (content) {
            case TextContent textContent ->
                blocks.add(toHistoryContent(textContent, documentHandling));
            case DocumentContent documentContent ->
                addDocument(
                    blocks,
                    AgentDocumentHandle.of(documentContent.document()),
                    documentContent.document(),
                    tracked);
            case AgentDocumentContent block ->
                addDocument(
                    blocks,
                    AgentDocumentHandle.of(documents, block.id()),
                    restorer.restore(documents, block.id()).orElse(null),
                    tracked);
            case ObjectContent objectContent -> addObject(blocks, objectContent, tracked);
            case ReasoningContent reasoningContent ->
                blocks.add(toHistoryContent(reasoningContent, documentHandling));
            case ProviderContent providerContent ->
                blocks.add(toHistoryContent(providerContent, documentHandling));
          }
        }
        yield List.copyOf(blocks);
      }
    };
  }

  private void addDocument(
      List<AgentInstanceHistoryContent> blocks,
      AgentDocumentHandle handle,
      @Nullable Document document,
      DocumentHandling documentHandling) {
    blocks.add(objectHistoryContent(objectHandleRenderer.tree(handle)));
    if (document != null) {
      blocks.add(
          documentHistoryContent(DocumentContent.documentContent(document), documentHandling));
    }
  }

  private void addObject(
      List<AgentInstanceHistoryContent> blocks,
      ObjectContent objectContent,
      DocumentHandling documentHandling) {
    final var rendered = objectHandleRenderer.render(objectContent.content());
    if (rendered.documents().isEmpty()) {
      blocks.add(objectHistoryContent(objectContent.content()));
      return;
    }

    blocks.add(objectHistoryContent(rendered.tree()));
    final var reportedIds = new HashSet<AgentDocumentId>();
    for (final var document : rendered.documents()) {
      if (reportedIds.add(AgentDocumentId.of(document))) {
        blocks.add(
            documentHistoryContent(DocumentContent.documentContent(document), documentHandling));
      }
    }
  }

  private List<AgentInstanceHistoryContent> contentBlocks(
      List<Content> contents, DocumentHandling documentHandling) {
    return contents.stream().map(content -> toHistoryContent(content, documentHandling)).toList();
  }

  private AgentInstanceHistoryContent toHistoryContent(
      Content content, DocumentHandling documentHandling) {
    return switch (content) {
      case TextContent textContent -> AgentInstanceHistoryContent.text(textContent.text());
      case DocumentContent documentContent ->
          documentHistoryContent(documentContent, documentHandling);
      case AgentDocumentContent block ->
          objectHistoryContent(objectHandleRenderer.tree(AgentDocumentHandle.of(block.id())));
      case ObjectContent objectContent -> objectHistoryContent(objectContent.content());
      case ReasoningContent reasoningContent -> reasoningHistoryContent(reasoningContent);
      case ProviderContent providerContent -> providerHistoryContent(providerContent);
    };
  }

  private AgentInstanceHistoryContent reasoningHistoryContent(ReasoningContent reasoningContent) {
    final var historyObject = new LinkedHashMap<String, Object>();
    historyObject.put("camunda.agenticai.content.type", "reasoning");
    if (StringUtils.isNotBlank(reasoningContent.text())) {
      historyObject.put("text", reasoningContent.text());
    }
    historyObject.put("payload", reasoningContent.payload());
    return objectHistoryContent(historyObject);
  }

  private AgentInstanceHistoryContent providerHistoryContent(ProviderContent providerContent) {
    if (StringUtils.isNotBlank(providerContent.text())) {
      return AgentInstanceHistoryContent.text(providerContent.text());
    }
    return objectHistoryContent(providerContent);
  }

  private AgentInstanceHistoryContent objectHistoryContent(Object value) {
    return AgentInstanceHistoryContent.object(value);
  }

  private AgentInstanceHistoryContent documentHistoryContent(
      DocumentContent documentContent, DocumentHandling documentHandling) {
    final Document document = documentContent.document();
    return switch (document.reference()) {
      case CamundaDocumentReference ref ->
          AgentInstanceHistoryContent.document(
              toDocumentReferenceResponse(ref, document.metadata(), documentHandling));
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
    return objectHistoryContent(new ExternalDocumentReferenceModel(ref.url(), ref.name()));
  }

  private DocumentReferenceResponseImpl toDocumentReferenceResponse(
      CamundaDocumentReference ref,
      @Nullable DocumentMetadata metadata,
      DocumentHandling documentHandling) {
    final DocumentReference protocolRef =
        new DocumentReference()
            .camundaDocumentType(CamundaDocumentTypeEnum.CAMUNDA)
            .documentId(ref.getDocumentId())
            .storeId(ref.getStoreId());
    if (ref.getContentHash() != null) {
      protocolRef.contentHash(ref.getContentHash());
    }
    final var idAsFileName = fileNameOfTheHandle(ref, documentHandling);
    if (metadata != null || idAsFileName != null) {
      protocolRef.metadata(toProtocolDocumentMetadata(metadata, idAsFileName));
    }
    return new DocumentReferenceResponseImpl(protocolRef);
  }

  /**
   * The file name of a document whose handle shows the agent document id in place of a file name,
   * so that the chip shows what the handle shows; {@code null} when the document has a file name or
   * the agent does not track its documents.
   */
  private static @Nullable String fileNameOfTheHandle(
      CamundaDocumentReference ref, DocumentHandling documentHandling) {
    return switch (documentHandling) {
      case DocumentHandling.Tracked ignored ->
          AgentDocument.Descriptors.of(ref).fileName() == null
              ? AgentDocumentId.of(ref).value()
              : null;
      case DocumentHandling.Untracked ignored -> null;
    };
  }

  private DocumentMetadataResponse toProtocolDocumentMetadata(
      @Nullable DocumentMetadata metadata, @Nullable String idAsFileName) {
    final DocumentMetadataResponse protocolMetadata = new DocumentMetadataResponse();
    if (idAsFileName != null) {
      protocolMetadata.fileName(idAsFileName);
    } else if (metadata != null && metadata.getFileName() != null) {
      protocolMetadata.fileName(metadata.getFileName());
    }
    if (metadata == null) {
      return protocolMetadata;
    }
    if (metadata.getContentType() != null) {
      protocolMetadata.contentType(metadata.getContentType());
    }
    if (metadata.getSize() != null) {
      protocolMetadata.size(metadata.getSize());
    }
    if (metadata.getExpiresAt() != null) {
      protocolMetadata.expiresAt(
          metadata.getExpiresAt().withOffsetSameInstant(ZoneOffset.UTC).toString());
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
