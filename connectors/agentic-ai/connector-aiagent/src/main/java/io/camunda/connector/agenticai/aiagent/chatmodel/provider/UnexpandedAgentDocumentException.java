/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.chatmodel.provider;

import io.camunda.connector.agenticai.aiagent.model.message.content.AgentDocumentContent;

/**
 * The failure of a provider chat model that is handed an {@link AgentDocumentContent}: the agent
 * expands it to its tag text and document content before the request is built, so it never reaches
 * a provider.
 */
public class UnexpandedAgentDocumentException extends IllegalStateException {

  public UnexpandedAgentDocumentException(AgentDocumentContent content) {
    super(
        "AgentDocumentContent of document '%s' must be expanded to its tag text and document content before the request"
            .formatted(content.id()));
  }
}
