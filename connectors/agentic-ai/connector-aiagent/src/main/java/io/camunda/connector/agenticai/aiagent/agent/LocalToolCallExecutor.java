/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.agent;

import io.camunda.connector.agenticai.aiagent.api.tool.RuntimeToolResult;
import io.camunda.connector.agenticai.aiagent.document.ObjectHandleRenderer;
import io.camunda.connector.agenticai.aiagent.model.AgentContext;
import io.camunda.connector.agenticai.aiagent.model.AgentExecutionContext;
import io.camunda.connector.agenticai.aiagent.model.document.AgentDocumentId;
import io.camunda.connector.agenticai.aiagent.model.document.AgentDocuments;
import io.camunda.connector.agenticai.aiagent.model.document.DocumentDelivery;
import io.camunda.connector.agenticai.aiagent.model.document.DocumentHandling;
import io.camunda.connector.agenticai.aiagent.model.message.content.AgentDocumentContent;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCall;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCallEffect;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCallResultContent;
import io.camunda.connector.agenticai.aiagent.tool.runtime.RuntimeToolExecution;
import io.camunda.connector.agenticai.aiagent.tool.runtime.RuntimeTools;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Produces the tool call results of the local tool calls that are executed: the calls of runtime
 * tools. A rejected call is answered by its rejection when its batch is created (see {@link
 * ToolCallBatch#of}), so it never reaches this step.
 */
public class LocalToolCallExecutor {

  private final RuntimeTools runtimeTools;
  private final ObjectHandleRenderer objectHandleRenderer;

  public LocalToolCallExecutor(
      RuntimeTools runtimeTools, ObjectHandleRenderer objectHandleRenderer) {
    this.runtimeTools = runtimeTools;
    this.objectHandleRenderer = objectHandleRenderer;
  }

  /**
   * Returns one entry per call. An agent instance that tracks its documents registers the documents
   * of a result that the model reads as handles, also those inside a typed record.
   *
   * @param documentIdsWithContent the ids of the documents whose content the model reads as of the
   *     start of the batch
   * @throws IllegalStateException if a call is not a call of a runtime tool
   * @throws IllegalArgumentException if the result of a runtime tool breaks the rules of its
   *     documents, a defect of the tool
   */
  public List<ToolCallBatch.Entry> execute(
      AgentExecutionContext executionContext,
      AgentContext agentContext,
      Set<AgentDocumentId> documentIdsWithContent,
      List<ToolCall> calls) {
    for (final var call : calls) {
      if (!isExecutedByTheAgent(call)) {
        throw new IllegalStateException(
            "The call '%s' of the tool '%s' is not executed by the agent, its result source is %s"
                .formatted(call.id(), call.name(), call.resultSource()));
      }
    }
    final var entries = new ArrayList<ToolCallBatch.Entry>();
    if (!calls.isEmpty()) {
      for (final var execution :
          runtimeTools.execute(executionContext, agentContext, documentIdsWithContent, calls)) {
        final var entry = entryOf(execution);
        switch (agentContext.documentHandling()) {
          case DocumentHandling.Tracked tracked -> requireWellFormed(entry, tracked.documents());
          case DocumentHandling.Untracked ignored -> requireNoDocumentIds(entry);
        }
        entries.add(entry);
      }
    }
    return List.copyOf(entries);
  }

  private static ToolCallBatch.Entry entryOf(RuntimeToolExecution execution) {
    final var result = execution.toolCallResult();
    return switch (execution.result()) {
      case RuntimeToolResult.Success success -> ToolCallBatch.Entry.from(result, success.effects());
      case RuntimeToolResult.Error ignored -> ToolCallBatch.Entry.from(result);
    };
  }

  /**
   * An agent instance that does not track its documents gives no document an id: a result names no
   * document by its id and asks nothing of the agent. Its documents are {@code DocumentContent}
   * blocks of any kind of document.
   *
   * @throws IllegalArgumentException if the result holds an {@code AgentDocumentContent} block or
   *     an effect
   */
  private static void requireNoDocumentIds(ToolCallBatch.Entry entry) {
    final var tool = entry.result().name();
    if (entry.result().content().stream().anyMatch(AgentDocumentContent.class::isInstance)
        || !entry.effects().isEmpty()) {
      throw new IllegalArgumentException(
          "The runtime tool '%s' refers to a document by its id, which an agent instance that does not track its documents does not support"
              .formatted(tool));
    }
  }

  /**
   * A new document is a {@code DocumentContent}, a known one an {@code AgentDocumentContent}, and
   * content comes only through an effect. A result that breaks this is a defect of its tool.
   *
   * @param known the documents the agent knows as of the start of the batch
   * @throws IllegalArgumentException if the result holds a block with the delivery content, or
   *     refers to a document that is neither known nor returned by the result
   */
  private static void requireWellFormed(ToolCallBatch.Entry entry, AgentDocuments known) {
    final var tool = entry.result().name();
    final var returned =
        entry.documents().stream().map(AgentDocumentId::of).collect(Collectors.toSet());
    final Consumer<AgentDocumentId> requireKnown =
        id -> {
          if (known.find(id).isEmpty() && !returned.contains(id)) {
            throw new IllegalArgumentException(
                "The runtime tool '%s' refers to the document '%s', which the agent does not know and the result does not return"
                    .formatted(tool, id));
          }
        };

    entry.result().content().stream()
        .filter(AgentDocumentContent.class::isInstance)
        .map(AgentDocumentContent.class::cast)
        .forEach(reference -> {
          if (reference.delivery() == DocumentDelivery.CONTENT) {
            throw new IllegalArgumentException(
                "The runtime tool '%s' returned the content of the document '%s'; the content of a document is added with an effect only"
                    .formatted(tool, reference.id()));
          }
          requireKnown.accept(reference.id());
        });
    for (final var effect : entry.effects()) {
      switch (effect) {
        case ToolCallEffect.AddDocumentContent add -> requireKnown.accept(add.id());
      }
    }
  }

  private ToolCallBatch.Entry entry(
      ToolCallResultContent result, DocumentHandling documentHandling) {
    return switch (documentHandling) {
      case DocumentHandling.Tracked ignored ->
          ToolCallBatch.Entry.withRenderedDocuments(result, objectHandleRenderer);
      case DocumentHandling.Untracked ignored -> ToolCallBatch.Entry.from(result);
    };
  }

  private static boolean isExecutedByTheAgent(ToolCall call) {
    return switch (call.resultSource()) {
      case RUNTIME -> true;
      case PROCESS, REJECTED -> false;
    };
  }
}
