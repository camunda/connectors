/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.agent;

import io.camunda.connector.agenticai.aiagent.document.ObjectHandleRenderer;
import io.camunda.connector.agenticai.aiagent.model.document.AgentDocument;
import io.camunda.connector.agenticai.aiagent.model.document.UnsupportedDocumentReferenceException;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCall;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCallResult;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCallResultContent;
import io.camunda.connector.api.document.Document;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * The tool calls of one assistant message and their results as they arrive. The calls belong to one
 * turn, the results form the input of the next one, so a batch spans two turns. The results come
 * from different sources (see {@link ToolCall.ResultSource}), the batch does not distinguish them.
 *
 * @param entriesById the entries of the calls that have a result, by tool call id
 */
public record ToolCallBatch(List<ToolCall> toolCalls, Map<String, Entry> entriesById) {

  /**
   * @throws IllegalArgumentException if an entry is not the result of one of the calls
   */
  public ToolCallBatch {
    toolCalls = List.copyOf(toolCalls);
    entriesById = Map.copyOf(entriesById);
    final var ids = toolCalls.stream().map(ToolCall::id).toList();
    entriesById.keySet().stream()
        .filter(id -> !ids.contains(id))
        .findFirst()
        .ifPresent(id -> {
          throw new IllegalArgumentException(
              "The result '%s' is not the result of a call of the batch %s".formatted(id, ids));
        });
  }

  /**
   * A batch of the calls of an assistant message. A rejected call has its result from the start:
   * its rejection, decided when the call was admitted. Every other call is unanswered.
   */
  public static ToolCallBatch of(List<ToolCall> toolCalls) {
    final var now = OffsetDateTime.now(ZoneId.systemDefault());
    final var rejections =
        toolCalls.stream()
            .filter(call -> call.resultSource() == ToolCall.ResultSource.REJECTED)
            .collect(
                Collectors.toMap(
                    ToolCall::id,
                    call -> Entry.from(ToolCallResultContent.forRejectedToolCall(call, now))));
    return new ToolCallBatch(toolCalls, rejections);
  }

  /** The calls without a result, in call order. */
  public List<ToolCall> unanswered() {
    return toolCalls.stream().filter(call -> !entriesById.containsKey(call.id())).toList();
  }

  /**
   * Adds the results of calls that have none yet.
   *
   * @throws IllegalArgumentException if an entry is not the result of an unanswered call
   */
  public ToolCallBatch withEntries(List<Entry> entries) {
    final var updated = new HashMap<>(entriesById);
    for (final var entry : entries) {
      if (updated.putIfAbsent(entry.result().id(), entry) != null) {
        throw new IllegalArgumentException(
            "The call '%s' has a result already".formatted(entry.result().id()));
      }
    }
    return new ToolCallBatch(toolCalls, updated);
  }

  /**
   * The entries in call order.
   *
   * @throws IllegalStateException if a call has no result
   */
  public List<Entry> entriesInCallOrder() {
    final var unanswered = unanswered();
    if (!unanswered.isEmpty()) {
      throw new IllegalStateException(
          "The calls %s have no result".formatted(unanswered.stream().map(ToolCall::id).toList()));
    }
    return toolCalls.stream().map(call -> entriesById.get(call.id())).toList();
  }

  /** A tool call result of the batch, with the documents the agent takes from it. */
  public record Entry(ToolCallResultContent result, List<Document> documents) {

    public Entry {
      Objects.requireNonNull(result.id(), "The tool call result of an entry must have an id");
      documents = List.copyOf(documents);
    }

    /**
     * The entry of a result as the process returned it. The documents are extracted from the
     * result, by its gateway tool handler if it has one.
     */
    public static Entry from(ToolCallResult result, ToolCallResultDocumentExtractor extractor) {
      final var documents =
          extractor.extractDocuments(List.of(result)).stream()
              .flatMap(group -> group.documents().stream())
              .toList();
      return new Entry(ToolCallResultContent.from(result), documents);
    }

    /**
     * The entry of a result as the process returned it, for an agent instance that tracks its
     * documents. The documents are the ones the model reads as handles: every document the renderer
     * finds in the result, at any depth, whatever gateway tool handler manages the result.
     */
    public static Entry withRenderedDocuments(
        ToolCallResult result, ObjectHandleRenderer objectHandleRenderer) {
      final var content = ToolCallResultContent.from(result);
      return new Entry(
          content,
          ToolCallResultDocumentExtractor.renderedDocumentsOf(
              content.content(), objectHandleRenderer));
    }

    /** The entry of a result with structured content; the documents are the ones of its content. */
    public static Entry from(ToolCallResultContent result) {
      return new Entry(result, ToolCallResultDocumentExtractor.documentsOf(result.content()));
    }

    /**
     * Checks that the result holds Camunda documents only, which an agent instance that tracks its
     * documents requires.
     *
     * @throws UnsupportedDocumentReferenceException if a document of the result is not a Camunda
     *     document
     */
    public void requireCamundaDocuments() {
      documents.stream()
          .filter(document -> !AgentDocument.isSupported(document))
          .findFirst()
          .ifPresent(document -> {
            throw UnsupportedDocumentReferenceException.inToolCallResult(result.name(), document);
          });
    }
  }
}
