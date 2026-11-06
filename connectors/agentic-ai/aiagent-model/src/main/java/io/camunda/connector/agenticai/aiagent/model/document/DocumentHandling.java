/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model.document;

/**
 * How an agent instance handles the documents of its conversation, fixed when the instance is
 * created.
 */
public sealed interface DocumentHandling {

  /**
   * The agent instance does not track its documents: a document is the {@code DocumentContent} it
   * entered the conversation with, and the model sees the XML tag format of the first release.
   */
  record Untracked() implements DocumentHandling {}

  /**
   * The agent instance tracks its documents: the conversation holds an {@code AgentDocumentContent}
   * for each of them and {@code documents} holds what the references stand for.
   *
   * @param documents the documents the agent knows
   */
  record Tracked(AgentDocuments documents) implements DocumentHandling {}
}
