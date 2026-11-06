/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model.document;

/** Two different documents get the same {@link AgentDocumentId}. */
public class DocumentIdCollisionException extends RuntimeException {

  public DocumentIdCollisionException(AgentDocumentId id) {
    super(
        "The document id '%s' is shared by two different documents, so the document cannot be told apart from the one the agent already knows."
            .formatted(id));
  }
}
