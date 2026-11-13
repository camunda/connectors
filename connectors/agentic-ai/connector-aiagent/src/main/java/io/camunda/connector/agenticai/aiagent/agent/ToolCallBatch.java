/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.agent;

import io.camunda.connector.agenticai.aiagent.api.tool.ToolCallEffect;
import io.camunda.connector.agenticai.aiagent.document.ObjectHandleRenderer;
import io.camunda.connector.agenticai.aiagent.model.document.AgentDocument;
import io.camunda.connector.agenticai.aiagent.model.document.AgentDocumentId;
import io.camunda.connector.agenticai.aiagent.model.document.AgentDocuments;
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

  /**
   * A tool call result of the batch, with the documents the agent takes from it and what the result
   * asks of the agent.
   *
   * @param effects what the result asks of the agent besides its content, in the order they were
   *     added
   */
  public record Entry(
      ToolCallResultContent result, List<Document> documents, List<ToolCallEffect> effects) {

    public Entry {
      Objects.requireNonNull(result.id(), "The tool call result of an entry must have an id");
      documents = List.copyOf(documents);
      effects = List.copyOf(effects);
    }

    /**
     * The entry of a result as the process returned it, for an agent instance that does not track
     * its documents. The documents are extracted from the result, by its gateway tool handler if it
     * has one.
     */
    public static Entry from(ToolCallResult result, ToolCallResultDocumentExtractor extractor) {
      final var documents =
          extractor.extractDocuments(List.of(result)).stream()
              .flatMap(group -> group.documents().stream())
              .toList();
      return new Entry(ToolCallResultContent.from(result), documents, List.of());
    }

    /**
     * The entry of a result of any source (a process tool or a runtime tool), for an agent instance
     * that tracks its documents. The documents are the ones the model reads as handles: every
     * document the renderer finds in the result, at any depth and also inside a typed record,
     * whatever gateway tool handler manages the result.
     */
    public static Entry withRenderedDocuments(
        ToolCallResultContent result, ObjectHandleRenderer objectHandleRenderer) {
      return withRenderedDocuments(result, objectHandleRenderer, List.of());
    }

    /**
     * The entry of a result that asks something of the agent, for an agent instance that tracks its
     * documents; the documents are the ones the model reads as handles, as in {@link
     * #withRenderedDocuments(ToolCallResultContent, ObjectHandleRenderer)}.
     */
    static Entry withRenderedDocuments(
        ToolCallResultContent result,
        ObjectHandleRenderer objectHandleRenderer,
        List<ToolCallEffect> effects) {
      return new Entry(
          result,
          ToolCallResultDocumentExtractor.renderedDocumentsOf(
              result.content(), objectHandleRenderer),
          effects);
    }

    /**
     * The entry of a result with structured content, for an agent instance that does not track its
     * documents, or a result that holds none, such as the rejection of a call; the documents are
     * the ones the generic walker finds in its content.
     */
    public static Entry from(ToolCallResultContent result) {
      return new Entry(
          result, ToolCallResultDocumentExtractor.documentsOf(result.content()), List.of());
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

    /**
     * Checks a result of a runtime tool for an agent instance that tracks its documents: its
     * documents are Camunda documents, and its effects name documents the agent knows or the result
     * returns. A result that breaks this is a defect of its tool.
     *
     * @param known the documents the agent knows as of the start of the batch
     * @throws UnsupportedDocumentReferenceException if a document of the result is not a Camunda
     *     document
     * @throws IllegalArgumentException if an effect names a document that is neither known nor
     *     returned by the result
     */
    void requireWellFormed(AgentDocuments known) {
      requireCamundaDocuments();
      final var returned = documents.stream().map(AgentDocumentId::of).collect(Collectors.toSet());
      for (final var effect : effects) {
        final var id =
            switch (effect) {
              case ToolCallEffect.AddDocumentContent add -> add.id();
            };
        if (known.find(id).isEmpty() && !returned.contains(id)) {
          throw new IllegalArgumentException(
              "The runtime tool '%s' refers to the document '%s', which the agent does not know and the result does not return"
                  .formatted(result.name(), id));
        }
      }
    }
  }
}
