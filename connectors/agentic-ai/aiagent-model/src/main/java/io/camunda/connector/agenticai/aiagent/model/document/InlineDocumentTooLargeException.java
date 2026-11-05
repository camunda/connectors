/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model.document;

/** The content of an inline document exceeds {@link AgentDocuments#MAX_INLINE_CONTENT_LENGTH}. */
public class InlineDocumentTooLargeException extends RuntimeException {

  public InlineDocumentTooLargeException(String documentName, int length) {
    super(
        ("The inline document '%s' is too large: its content has %d characters, the limit is %d."
                + " Model the process so that the file is returned as a Camunda document instead of"
                + " inline content.")
            .formatted(documentName, length, AgentDocuments.MAX_INLINE_CONTENT_LENGTH));
  }
}
