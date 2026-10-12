/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.agentinstance;

import io.camunda.connector.agenticai.aiagent.model.document.DocumentRegistry;
import java.util.Objects;

/**
 * What the agent instance history needs to show the documents of the messages: the registry of the
 * documents the agent knows, which the history expands document tags with, and the features of the
 * agent instance.
 *
 * <p>The history of an agent instance created without the documents feature has no document
 * registry: its messages are reported as they are stored. The history of an agent instance created
 * without the runtime tools feature has no runtime tools either.
 *
 * @param documents the documents known to the agent at the time of the write
 * @param documentFlow whether the agent instance has the documents feature
 * @param runtimeTools whether the agent instance has the runtime tools feature
 */
public record HistoryDocuments(
    DocumentRegistry documents, boolean documentFlow, boolean runtimeTools) {

  public HistoryDocuments {
    Objects.requireNonNull(documents);
  }

  /** The documents of an agent instance with the documents feature. */
  public HistoryDocuments(DocumentRegistry documents) {
    this(documents, true, true);
  }

  /**
   * The documents of an agent instance created without the documents feature; {@code runtimeTools}
   * tells whether it has the runtime tools feature.
   */
  public static HistoryDocuments withoutDocumentFlow(boolean runtimeTools) {
    return new HistoryDocuments(DocumentRegistry.empty(), false, runtimeTools);
  }
}
