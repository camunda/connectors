/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.agent;

import io.camunda.connector.agenticai.aiagent.model.message.content.DocumentContent;
import io.camunda.connector.agenticai.aiagent.model.message.content.ObjectContent;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCallResult;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCallResultContent;
import io.camunda.connector.agenticai.aiagent.tool.GatewayToolHandlerRegistry;
import io.camunda.connector.api.document.Document;
import io.camunda.connector.api.document.DocumentReference;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Extracts {@link Document} instances from a list of tool call results, grouped by tool call.
 *
 * <p>For each result, the extractor walks the {@code content()} tree using {@link
 * ContentTreeDocumentWalker} by default. When a result belongs to a {@link
 * io.camunda.connector.agenticai.aiagent.tool.GatewayToolHandler} that overrides {@link
 * io.camunda.connector.agenticai.aiagent.tool.GatewayToolHandler#extractDocuments(ToolCallResult)
 * extractDocuments}, the handler's typed extraction is used instead — gateway handlers contribute
 * extraction for the results they manage; the generic walker handles everything else.
 */
public class ToolCallResultDocumentExtractor {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(ToolCallResultDocumentExtractor.class);

  /** Documents extracted from a single tool call result, grouped with the tool call identity. */
  public record ToolCallDocuments(
      @Nullable String toolCallId, @Nullable String toolCallName, List<Document> documents) {}

  private final GatewayToolHandlerRegistry gatewayToolHandlers;

  public ToolCallResultDocumentExtractor(GatewayToolHandlerRegistry gatewayToolHandlers) {
    this.gatewayToolHandlers = gatewayToolHandlers;
  }

  /**
   * Extracts all {@link Document} instances from the given tool call results, grouped by tool call.
   * Tool calls without documents are excluded from the result. Order is preserved.
   */
  public List<ToolCallDocuments> extractDocuments(List<ToolCallResult> toolCallResults) {
    final var result = new ArrayList<ToolCallDocuments>();
    int totalDocuments = 0;

    for (ToolCallResult toolCallResult : toolCallResults) {
      final var documents = extractFromToolCallResult(toolCallResult);
      if (!documents.isEmpty()) {
        result.add(new ToolCallDocuments(toolCallResult.id(), toolCallResult.name(), documents));
        totalDocuments += documents.size();
      }
    }

    LOGGER.debug(
        "Tool call document extraction: processed {} result(s), extracted documents from {} ({} document(s) total)",
        toolCallResults.size(),
        result.size(),
        totalDocuments);

    return result;
  }

  /**
   * Extracts the {@link Document} instances of a persisted tool call result: the documents of its
   * {@link DocumentContent} blocks and the ones nested in its {@link ObjectContent} blocks. The
   * result is the one of a runtime tool, which no gateway tool handler manages.
   *
   * @return the documents with the tool call identity of the result, empty when it has none
   */
  public List<ToolCallDocuments> extractDocuments(ToolCallResultContent result) {
    final var documents = new ArrayList<Document>();
    for (final var block : result.content()) {
      switch (block) {
        case DocumentContent documentContent -> documents.add(documentContent.document());
        case ObjectContent objectContent ->
            documents.addAll(
                ContentTreeDocumentWalker.extractDocumentsFromContent(objectContent.content()));
        default -> {
          // only these blocks hold live documents
        }
      }
    }
    final var distinct = distinct(documents);
    return distinct.isEmpty()
        ? List.of()
        : List.of(new ToolCallDocuments(result.id(), result.name(), distinct));
  }

  private List<Document> extractFromToolCallResult(ToolCallResult toolCallResult) {
    final var documents =
        Optional.ofNullable(toolCallResult.name())
            .flatMap(gatewayToolHandlers::handlerForToolDefinition)
            .map(handler -> handler.extractDocuments(toolCallResult))
            .orElseGet(
                () ->
                    ContentTreeDocumentWalker.extractDocumentsFromContent(
                        toolCallResult.content()));
    return distinct(documents);
  }

  /**
   * Defensive: a third-party handler might return null entries; also dedupes documents that appear
   * multiple times in a single result (e.g. the same attachment referenced from different paths in
   * the content tree), keyed by DocumentReference, which is a record / has structural equality for
   * the standard reference types.
   */
  private static List<Document> distinct(List<Document> documents) {
    final var seen = new HashSet<DocumentReference>();
    return documents.stream()
        .filter(Objects::nonNull)
        .filter(doc -> seen.add(doc.reference()))
        .toList();
  }
}
