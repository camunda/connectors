/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.agentinstance;

import io.camunda.client.api.command.ClientHttpException;
import java.io.IOException;
import java.io.InterruptedIOException;

public final class AgentInstanceErrorClassifier {

  public enum Decision {
    RETRYABLE,
    PERMANENT
  }

  public static Decision classify(Throwable t) {
    // 1. HTTP errors via ClientHttpException (parent of ProblemException)
    if (t instanceof ClientHttpException httpEx) {
      int status = httpEx.code();
      if (status == 404) {
        return Decision.RETRYABLE;
      }
      if (status >= 400 && status < 500) {
        return Decision.PERMANENT;
      }
      if (status >= 500) {
        return Decision.RETRYABLE;
      }
    }

    // Also check cause chain for ClientHttpException
    Throwable cause = t.getCause();
    if (cause instanceof ClientHttpException httpCause) {
      int status = httpCause.code();
      if (status == 404) {
        return Decision.RETRYABLE;
      }
      if (status >= 400 && status < 500) {
        return Decision.PERMANENT;
      }
      if (status >= 500) {
        return Decision.RETRYABLE;
      }
    }

    // 2. IO-level transport errors
    if (isIoException(t)) {
      return Decision.RETRYABLE;
    }

    // 3. RuntimeException wrapping a transport-level exception
    if (t instanceof RuntimeException && isIoException(t.getCause())) {
      return Decision.RETRYABLE;
    }

    // 4. Default: fail-safe permanent
    return Decision.PERMANENT;
  }

  private static boolean isIoException(Throwable t) {
    return t instanceof InterruptedIOException || t instanceof IOException;
  }

  private AgentInstanceErrorClassifier() {}
}
