/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.conversation;

import io.camunda.connector.agenticai.aiagent.model.document.DocumentDelivery;
import org.jspecify.annotations.Nullable;

/**
 * How the agent delivers the documents of a user prompt to the model of an agent instance that
 * tracks its documents. It is the domain form of the optional document delivery of the user prompt
 * configuration: a process that does not set the option has its own case, so no code has to read a
 * missing value as a delivery.
 */
public enum UserPromptDocumentDelivery {
  /** No delivery is configured: the content of every document goes to the model. */
  UNSET,
  /** The model reads the handle of a document; a tool can load the content on request. */
  REFERENCE,
  /** The agent adds the content of every document, unless the model reads it already. */
  CONTENT;

  /**
   * The delivery for the document delivery of the user prompt configuration, {@code null} when the
   * process sets none.
   */
  public static UserPromptDocumentDelivery of(@Nullable DocumentDelivery documentDelivery) {
    if (documentDelivery == null) {
      return UNSET;
    }
    return switch (documentDelivery) {
      case REFERENCE -> REFERENCE;
      case CONTENT -> CONTENT;
    };
  }
}
