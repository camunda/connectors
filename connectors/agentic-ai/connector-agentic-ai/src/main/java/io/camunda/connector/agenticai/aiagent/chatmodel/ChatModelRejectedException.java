/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.chatmodel;

import io.camunda.connector.agenticai.aiagent.model.AgentMetrics;
import io.camunda.connector.agenticai.aiagent.model.message.AssistantMessage;
import org.jspecify.annotations.Nullable;

/**
 * A {@link ChatModel} recognized a known, unrecoverable-for-now condition while producing a result
 * for a turn and could not return a normal {@link ChatResult}. Thrown directly by whichever
 * provider detects it, at the point of detection, instead of being carried as {@link
 * io.camunda.connector.agenticai.aiagent.model.message.StopReason} data for a caller to inspect
 * afterwards.
 *
 * <p>Sealed so callers can catch this by its concrete subtype and {@code switch} over it
 * exhaustively, rather than inspecting an error code or stop reason string.
 */
public abstract sealed class ChatModelRejectedException extends RuntimeException
    permits ContextWindowExceededException, ContentFilteredException {

  private final @Nullable PartialResult partialResult;

  protected ChatModelRejectedException(String message, @Nullable PartialResult partialResult) {
    super(message);
    this.partialResult = partialResult;
  }

  protected ChatModelRejectedException(
      String message, Throwable cause, @Nullable PartialResult partialResult) {
    super(message, cause);
    this.partialResult = partialResult;
  }

  /**
   * The assistant message and metrics the provider had already built when it recognized the
   * rejection, if any. {@code null} when the provider rejected the request outright before any
   * content could be produced (e.g. an HTTP error with no response body to convert).
   */
  public @Nullable PartialResult partialResult() {
    return partialResult;
  }

  /** An {@link AssistantMessage} and {@link AgentMetrics} pair salvaged before the rejection. */
  public record PartialResult(AssistantMessage assistantMessage, AgentMetrics metrics) {}
}
