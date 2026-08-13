/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.chatmodel;

import org.jspecify.annotations.Nullable;

/** The model's total context window (input + output tokens) was exceeded. */
public final class ContextWindowExceededException extends ChatModelRejectedException {

  public ContextWindowExceededException(String message, @Nullable PartialResult partialResult) {
    super(message, partialResult);
  }

  public ContextWindowExceededException(
      String message, Throwable cause, @Nullable PartialResult partialResult) {
    super(message, cause, partialResult);
  }
}
