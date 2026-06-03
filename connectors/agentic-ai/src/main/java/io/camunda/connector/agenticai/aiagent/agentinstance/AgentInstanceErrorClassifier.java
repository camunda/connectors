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

public final class AgentInstanceErrorClassifier implements ErrorClassifier {

  /** For create: 404 is retryable (idempotent re-create against a transient lookup race). */
  public static final AgentInstanceErrorClassifier FOR_CREATE =
      new AgentInstanceErrorClassifier(true);

  /** For update: 404 is permanent (the instance must exist; 404 = not found = bug). */
  public static final AgentInstanceErrorClassifier FOR_UPDATE =
      new AgentInstanceErrorClassifier(false);

  private final boolean notFoundIsRetryable;

  private AgentInstanceErrorClassifier(boolean notFoundIsRetryable) {
    this.notFoundIsRetryable = notFoundIsRetryable;
  }

  @Override
  public Decision classify(Throwable t) {
    Throwable current = t;
    while (current != null) {
      if (current instanceof ClientHttpException httpEx) {
        int status = httpEx.code();
        if (status == 404) {
          return notFoundIsRetryable ? Decision.RETRYABLE : Decision.PERMANENT;
        }
        if (status >= 400 && status < 500) {
          return Decision.PERMANENT;
        }
        if (status >= 500) {
          return Decision.RETRYABLE;
        }
      }

      if (current instanceof IOException) {
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
}
