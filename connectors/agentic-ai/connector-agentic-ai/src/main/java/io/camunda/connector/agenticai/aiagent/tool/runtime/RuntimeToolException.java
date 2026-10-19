/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.tool.runtime;

/**
 * Thrown by a runtime tool to report a failure the model can act on (a wrong argument, a missing
 * entry, a rejected operation). The framework answers the call with an error result carrying the
 * message; the message is what the model reads, so it should say what to change.
 *
 * <p>Every other exception a tool throws is unexpected and propagates, so the job fails instead of
 * the model reading an internal error.
 */
public class RuntimeToolException extends RuntimeException {

  public RuntimeToolException(String message) {
    super(message);
  }

  public RuntimeToolException(String message, Throwable cause) {
    super(message, cause);
  }

  @Override
  public String getMessage() {
    return String.valueOf(super.getMessage());
  }
}
