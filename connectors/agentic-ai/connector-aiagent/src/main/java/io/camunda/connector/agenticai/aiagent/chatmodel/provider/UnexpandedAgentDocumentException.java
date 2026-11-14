/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.chatmodel.provider;

import io.camunda.connector.agenticai.aiagent.model.message.content.AgentDocumentContent;

/**
 * The failure of a LangChain4j converter that is handed an {@link AgentDocumentContent}. The
 * LangChain4j converters read the persisted content types, which include the block; a chat request
 * never holds it.
 */
public class UnexpandedAgentDocumentException extends IllegalStateException {

  public UnexpandedAgentDocumentException(AgentDocumentContent content) {
    super(
        "AgentDocumentContent of document '%s' must be expanded to its handle text and document content before the request"
            .formatted(content.id()));
  }
}
