/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.agent;

import io.camunda.connector.agenticai.aiagent.document.IngestedDocument;
import io.camunda.connector.agenticai.aiagent.model.message.Message;
import java.util.ArrayList;
import java.util.List;

/**
 * Input messages of the next turn, ready to be applied to the conversation.
 *
 * @param documents the documents that enter the conversation with the messages, in order, to be
 *     registered in the document workspace; empty for an agent instance without the documents
 *     feature
 */
public record TurnInput(List<Message> messages, List<IngestedDocument> documents) {

  public TurnInput {
    messages = List.copyOf(messages);
    documents = List.copyOf(documents);
  }

  public TurnInput(List<Message> messages) {
    this(messages, List.of());
  }

  public static TurnInput empty() {
    return new TurnInput(List.of());
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
    return new TurnInput(allMessages, allDocuments);
  }
}
