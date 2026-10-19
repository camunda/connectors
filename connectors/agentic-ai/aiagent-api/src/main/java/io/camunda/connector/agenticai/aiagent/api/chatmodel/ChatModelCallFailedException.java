/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.api.chatmodel;

import io.camunda.connector.api.error.ConnectorException;

/**
 * A {@link ChatModel} call failed at the provider, for example with an HTTP error. Behaves like the
 * plain coded {@link ConnectorException} it replaces, and additionally carries a low-cardinality
 * classification of the failure that observers such as tracing can use without knowing the
 * provider's own exception types.
 */
public class ChatModelCallFailedException extends ConnectorException {

  private final String errorType;

  /**
   * @param errorType low-cardinality classification of the failure, for example the error type
   *     reported by the provider API or an HTTP status code
   */
  public ChatModelCallFailedException(
      String errorCode, String message, Throwable cause, String errorType) {
    super(errorCode, message, cause);
    this.errorType = errorType;
  }

  public String errorType() {
    return errorType;
  }
}
