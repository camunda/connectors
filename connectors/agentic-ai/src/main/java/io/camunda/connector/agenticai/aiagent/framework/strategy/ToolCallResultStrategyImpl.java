/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.framework.strategy;

import static io.camunda.connector.agenticai.aiagent.agent.AgentErrorCodes.ERROR_CODE_FAILED_MODEL_CALL;
import static io.camunda.connector.agenticai.model.message.content.DocumentContent.documentContent;
import static io.camunda.connector.agenticai.model.message.content.TextContent.textContent;

import io.camunda.connector.agenticai.aiagent.agent.ToolCallResultDocumentExtractor;
import io.camunda.connector.agenticai.aiagent.agent.ToolCallResultDocumentExtractor.ToolCallDocuments;
import io.camunda.connector.agenticai.aiagent.framework.api.ChatRequest;
import io.camunda.connector.agenticai.aiagent.framework.api.ModelCapabilities;
import io.camunda.connector.agenticai.aiagent.framework.multimodal.DocumentModality;
import io.camunda.connector.agenticai.model.message.DocumentXmlTag;
import io.camunda.connector.agenticai.model.message.Message;
import io.camunda.connector.agenticai.model.message.ToolCallResultMessage;
import io.camunda.connector.agenticai.model.message.UserMessage;
import io.camunda.connector.agenticai.model.message.content.Content;
import io.camunda.connector.agenticai.model.message.content.DocumentContent;
import io.camunda.connector.agenticai.model.tool.ToolCallResult;
import io.camunda.connector.api.document.Document;
import io.camunda.connector.api.error.ConnectorException;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ToolCallResultStrategyImpl implements ToolCallResultStrategy {

  private static final Logger LOGGER = LoggerFactory.getLogger(ToolCallResultStrategyImpl.class);

  private static final String FALLBACK_HEADER = "Documents extracted from tool call results:";

  private final ToolCallResultDocumentExtractor documentExtractor;

  public ToolCallResultStrategyImpl(ToolCallResultDocumentExtractor documentExtractor) {
    this.documentExtractor = documentExtractor;
  }

  @Override
  public Result apply(ChatRequest request, ModelCapabilities capabilities) {
    if (LOGGER.isDebugEnabled()) {
      LOGGER.debug(
          "Applying tool-call-result strategy with resolved capabilities — "
              + "userMessageModalities={}, toolResultModalities={}",
          capabilities.userMessageModalities(),
          capabilities.toolResultModalities());
    }
    final var rewrittenMessages = new ArrayList<Message>(request.messages().size());
    final var syntheticContextMessages = new ArrayList<UserMessage>();

    for (var message : request.messages()) {
      switch (message) {
        case ToolCallResultMessage toolCallResultMessage -> {
          final var routed = routeToolCallResultMessage(toolCallResultMessage, capabilities);
          rewrittenMessages.add(routed.message());
          if (routed.synthetic() != null) {
            rewrittenMessages.add(routed.synthetic());
            syntheticContextMessages.add(routed.synthetic());
          }
        }
        case UserMessage userMessage -> {
          validateUserMessageModalities(userMessage, capabilities);
          rewrittenMessages.add(userMessage);
        }
        default -> rewrittenMessages.add(message);
      }
    }

    return new Result(
        new ChatRequest(rewrittenMessages, request.toolDefinitions(), request.responseFormat()),
        List.copyOf(syntheticContextMessages));
  }

  private record RoutedToolCallResultMessage(
      ToolCallResultMessage message, UserMessage synthetic) {}

  private RoutedToolCallResultMessage routeToolCallResultMessage(
      ToolCallResultMessage toolCallResultMessage, ModelCapabilities capabilities) {

    final var extractedByToolCall =
        documentExtractor.extractDocuments(toolCallResultMessage.results());
    if (extractedByToolCall.isEmpty()) {
      return new RoutedToolCallResultMessage(toolCallResultMessage, null);
    }

    // Index extracted documents by tool call id for O(1) lookup while iterating results in order.
    final var docsByToolCallId = new HashMap<String, ToolCallDocuments>();
    for (var entry : extractedByToolCall) {
      docsByToolCallId.put(entry.toolCallId(), entry);
    }

    final var fallbackEntries = new ArrayList<ToolCallDocuments>();
    final var rewrittenResults =
        new ArrayList<ToolCallResult>(toolCallResultMessage.results().size());

    for (var result : toolCallResultMessage.results()) {
      final var entry = docsByToolCallId.get(result.id());
      if (entry == null) {
        rewrittenResults.add(result);
        continue;
      }

      final var inline = new ArrayList<Document>();
      final var fallback = new ArrayList<Document>();
      for (var doc : entry.documents()) {
        final var modality = DocumentModality.of(doc);
        if (capabilities.toolResultModalities().contains(modality)) {
          inline.add(doc);
        } else {
          fallback.add(doc);
        }
      }
      if (LOGGER.isDebugEnabled()) {
        LOGGER.debug(
            "Routing tool result id={} name={}: documents={}, inlined={}, fallback={}, "
                + "toolResultModalities={}",
            result.id(),
            result.name(),
            entry.documents().size(),
            inline.size(),
            fallback.size(),
            capabilities.toolResultModalities());
      }

      rewrittenResults.add(inline.isEmpty() ? result : appendInlineContentBlocks(result, inline));
      if (!fallback.isEmpty()) {
        fallbackEntries.add(
            new ToolCallDocuments(entry.toolCallId(), entry.toolCallName(), fallback));
      }
    }

    final var rewrittenMessage =
        ToolCallResultMessage.builder()
            .results(rewrittenResults)
            .metadata(toolCallResultMessage.metadata())
            .build();
    final var synthetic =
        fallbackEntries.isEmpty() ? null : buildSyntheticUserMessage(fallbackEntries);

    return new RoutedToolCallResultMessage(rewrittenMessage, synthetic);
  }

  private static ToolCallResult appendInlineContentBlocks(
      ToolCallResult result, List<Document> inlineDocs) {
    final var blocks = new ArrayList<Content>();
    if (result.contentBlocks() != null) {
      blocks.addAll(result.contentBlocks());
    }
    for (var doc : inlineDocs) {
      blocks.add(documentContent(doc));
    }
    return result.withContentBlocks(blocks);
  }

  private static UserMessage buildSyntheticUserMessage(List<ToolCallDocuments> fallbackEntries) {
    final var content = new ArrayList<Content>();
    content.add(textContent(FALLBACK_HEADER));
    for (var entry : fallbackEntries) {
      for (var doc : entry.documents()) {
        content.add(
            textContent(
                DocumentXmlTag.from(doc, entry.toolCallId(), entry.toolCallName()).toXml()));
        content.add(documentContent(doc));
      }
    }

    final Map<String, Object> metadata = new HashMap<>();
    metadata.put("timestamp", ZonedDateTime.now());
    metadata.put(UserMessage.METADATA_TOOL_CALL_DOCUMENTS, true);

    return UserMessage.builder().content(content).metadata(metadata).build();
  }

  private void validateUserMessageModalities(
      UserMessage userMessage, ModelCapabilities capabilities) {
    if (userMessage.content() == null) {
      return;
    }
    for (var contentBlock : userMessage.content()) {
      if (!(contentBlock instanceof DocumentContent documentContent)) {
        continue;
      }
      final var doc = documentContent.document();
      final var modality = DocumentModality.of(doc);
      if (!capabilities.userMessageModalities().contains(modality)) {
        throw new ConnectorException(
            ERROR_CODE_FAILED_MODEL_CALL,
            "Document with reference '%s' has modality %s but the resolved model does not "
                    .formatted(doc.reference(), modality)
                + "support that modality in user messages (supported: %s)."
                    .formatted(capabilities.userMessageModalities()));
      }
    }
    LOGGER.trace(
        "Validated user message modalities against capabilities {}",
        capabilities.userMessageModalities());
  }
}
