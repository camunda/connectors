/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.agentinstance;

import io.camunda.client.api.command.ClientHttpException;
import io.camunda.connector.agenticai.util.retry.ErrorClassifier;
import java.io.IOException;

public final class AgentInstanceErrorClassifier implements ErrorClassifier {

  /** For create: 404 is retryable (idempotent re-create against a transient lookup race). */
  public static final AgentInstanceErrorClassifier FOR_CREATE =
      new AgentInstanceErrorClassifier(true);

  /**
   * For update: 404 is retryable. A just-created agent instance may not yet be visible to follow-up
   * API calls due to eventual consistency, so a transient 404 should be retried rather than failing
   * the job.
   */
  public static final AgentInstanceErrorClassifier FOR_UPDATE =
      new AgentInstanceErrorClassifier(true);

  /**
   * For history item creation: 404 is retryable for the same eventual-consistency reason as {@link
   * #FOR_UPDATE}.
   */
  public static final AgentInstanceErrorClassifier FOR_HISTORY_ITEM =
      new AgentInstanceErrorClassifier(true);

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
