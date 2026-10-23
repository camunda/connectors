/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.agent;

import io.camunda.connector.agenticai.aiagent.api.tool.RuntimeToolResult;
import io.camunda.connector.agenticai.aiagent.document.IngestedDocument;
import io.camunda.connector.agenticai.aiagent.model.AgentContext;
import io.camunda.connector.agenticai.aiagent.model.document.DocumentId;
import io.camunda.connector.agenticai.aiagent.model.message.Message;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCallResultContent;
import io.camunda.connector.api.document.Document;
import java.util.List;
import java.util.Set;

/**
 * Builds the input of the turn that follows a turn with tool calls from the tool call results of
 * all of its calls, in the order of the calls. The composer uses it for the results of a turn with
 * process tool calls, the request handler for a turn with runtime tool calls only, so the input and
 * the documents that enter the conversation with it are the same for both.
 *
 * <p>This is the one place that applies what the results of runtime tools ask of the agent, after
 * all calls ran, in the order of the calls: it registers the documents they return and adds the
 * content of the documents they name (see {@link RuntimeToolResult.Success#withDocumentContent}).
 * The input is a function of the agent context, the results and the documents whose content the
 * model reads already, so a job that runs again composes the same input.
 */
public interface ToolCallResultsInputBuilder {

  /**
   * A tool call result with the documents extracted from it and the effects it asks for.
   *
   * @param result the tool call result that becomes part of the conversation
   * @param documents the documents of the result, in order of appearance
   * @param documentContent the documents whose content the result asks to add, in the order they
   *     were named; always empty for the result of a process tool
   */
  record Entry(
      ToolCallResultContent result, List<Document> documents, List<DocumentId> documentContent) {

    public Entry {
      documents = List.copyOf(documents);
      documentContent = List.copyOf(documentContent);
    }

    /** An entry whose result asks for no effect. */
    public Entry(ToolCallResultContent result, List<Document> documents) {
      this(result, documents, List.of());
    }

    /**
     * The entry for the result of a runtime tool; its documents are the ones of its content, so the
     * result of a failed call, whose content is text, has none. An error result asks for no effect.
     */
    public static Entry of(RuntimeToolExecution execution) {
      final var result = execution.toolCallResult();
      return new Entry(
          result,
          ToolCallResultDocumentExtractor.documentsOf(result.content()),
          execution.result().documentContent());
    }
  }

  /**
   * The input of the turn that follows the results.
   *
   * @param messages the messages of the input, in order
   * @param documents the documents of the results to register in the document registry, in the
   *     order of the tool calls; empty for an agent instance without the documents feature
   */
  record Input(List<Message> messages, List<IngestedDocument> documents) {

    public Input {
      messages = List.copyOf(messages);
      documents = List.copyOf(documents);
    }
  }

  /**
   * The input for the results of all tool calls of a turn. An agent instance with the documents
   * feature shows a document as its tag inside the tool result and reports it for the document
   * registry. An agent instance without it keeps the document in a message of its own, next to its
   * tag.
   *
   * <p>A result that asks for the content of a document the content guard rejects becomes an error
   * result, and the documents it returned are not reported. A document whose content the model
   * reads already, or that an earlier result of the turn asked for, is not added again. The content
   * follows the tool call result message in a message of its own.
   *
   * @param agentContext the agent context the results were computed with; its document registry
   *     does not hold the documents of the results yet
   * @param entries the results in the order of the tool calls
   * @param documentIdsWithContent the ids of the documents whose content the model reads as of the
   *     start of the batch
   * @throws IllegalArgumentException if a result asks for the content of a document that neither
   *     {@code agentContext} nor the result knows
   */
  Input build(
      AgentContext agentContext, List<Entry> entries, Set<DocumentId> documentIdsWithContent);
}
