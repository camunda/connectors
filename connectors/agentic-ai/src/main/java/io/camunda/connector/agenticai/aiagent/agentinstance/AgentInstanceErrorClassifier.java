/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.agentinstance;

import io.camunda.client.api.command.ClientHttpException;
import io.camunda.connector.agenticai.util.retry.ErrorClassifier;
import io.camunda.connector.agenticai.util.retry.ErrorClassifier.Decision;
import java.io.IOException;

public final class AgentInstanceErrorClassifier {

  public static ErrorClassifier.Decision classify(Throwable t) {
    Throwable current = t;
    while (current != null) {
      if (current instanceof ClientHttpException httpEx) {
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

      if (isIoException(current)) {
        return Decision.RETRYABLE;
      }

      Throwable cause = current.getCause();
      if (cause == current) {
        break;
      }
      current = cause;
    }

    return Decision.PERMANENT;
  }

  private static boolean isIoException(Throwable t) {
    return t instanceof IOException;
  }

  private AgentInstanceErrorClassifier() {}
}
