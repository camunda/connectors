/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.api.tool;

import io.camunda.connector.agenticai.common.annotation.Experimental;

/**
 * Thrown by a runtime tool to report a failure the model can act on (a wrong argument, a missing
 * entry, a rejected operation). It is a shorthand for returning {@link RuntimeToolResult#error}:
 * the framework gives the call an error result carrying the message. The message is what the model
 * reads, so it says what to change and holds nothing internal.
 *
 * <p>Every other exception a tool throws is unexpected and propagates, so the job fails and is
 * retried instead of the model reading an internal error.
 *
 * <p>{@link Experimental}, see {@link RuntimeTool}.
 */
@Experimental
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
