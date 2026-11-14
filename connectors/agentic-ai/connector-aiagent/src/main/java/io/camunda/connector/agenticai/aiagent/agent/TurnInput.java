/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.agent;

import io.camunda.connector.agenticai.aiagent.model.AgentContext;
import io.camunda.connector.agenticai.aiagent.model.document.AgentDocument;
import io.camunda.connector.agenticai.aiagent.model.document.AgentDocumentIdCollisionException;
import io.camunda.connector.agenticai.aiagent.model.document.ResponseDocumentSelection;
import io.camunda.connector.agenticai.aiagent.model.message.Message;
import java.util.ArrayList;
import java.util.List;

/**
 * Input messages of the next turn, ready to be applied to the conversation.
 *
 * @param documents the documents that enter the conversation with the messages, in order, to be
 *     registered in the documents of the agent context; empty for an agent instance that does not
 *     track its documents
 * @param responseDocuments the response documents the tool call results select, in call order
 */
public record TurnInput(
    List<Message> messages,
    List<AgentDocument> documents,
    List<ResponseDocumentSelection> responseDocuments) {

  public TurnInput {
    messages = List.copyOf(messages);
    documents = List.copyOf(documents);
    responseDocuments = List.copyOf(responseDocuments);
  }

  public TurnInput(List<Message> messages, List<AgentDocument> documents) {
    this(messages, documents, List.of());
  }

  public TurnInput(List<Message> messages) {
    this(messages, List.of());
  }

  public static TurnInput empty() {
    return new TurnInput(List.of());
  }

  /**
   * The agent context the turn continues with: {@code agentContext} with the response documents
   * selected, in order, and the documents of this input registered. It depends on the context and
   * the input only, so a job that runs again computes the same context.
   *
   * @throws AgentDocumentIdCollisionException if a document gets the id of a different document the
   *     context already holds, or of another document of this input
   */
  public AgentContext applyTo(AgentContext agentContext) {
    var updated = agentContext;
    for (final var selection : responseDocuments) {
      updated = updated.withResponseDocument(selection);
    }
    if (documents.isEmpty()) {
      return updated;
    }
    return updated.withDocuments(updated.documents().register(documents));
  }

  public boolean isEmpty() {
    return messages.isEmpty();
  }

  /** This input followed by {@code next}. */
  public TurnInput followedBy(TurnInput next) {
    final var allMessages = new ArrayList<>(messages);
    allMessages.addAll(next.messages());
    final var allDocuments = new ArrayList<>(documents);
    allDocuments.addAll(next.documents());
    final var allResponseDocuments = new ArrayList<>(responseDocuments);
    allResponseDocuments.addAll(next.responseDocuments());
    return new TurnInput(allMessages, allDocuments, allResponseDocuments);
  }
}
