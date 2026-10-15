/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.agentinstance;

import io.camunda.client.api.command.ClientHttpException;
import io.camunda.connector.agenticai.common.util.retry.ErrorClassifier;
import java.io.IOException;

/**
 * Shared classifier for the agent-instance write endpoints (create, update, history item creation).
 * All three are {@code x-eventually-consistent: false} and are validated against primary processing
 * state, with Zeebe's key-based partition routing guaranteeing a create is visible to the same
 * partition before its key is ever returned to the caller. A {@code 404} from update/history means
 * the referenced agent instance genuinely doesn't exist (wrong/stale key, or since
 * completed/terminated); from create it means the referenced element instance doesn't exist. Either
 * way it reflects a genuinely missing entity rather than a not-yet-visible record, so it is
 * classified as permanent along with the other 4xx responses. {@code 429} is the exception: it
 * signals rate limiting rather than a semantic error, so it is retried like a 5xx.
 */
public enum AgentInstanceErrorClassifier implements ErrorClassifier {
  INSTANCE;

  @Override
  public Decision classify(Throwable t) {
    Throwable current = t;
    while (current != null) {
      if (current instanceof ClientHttpException httpEx) {
        int status = httpEx.code();
        if (status == 429 || status >= 500) {
          return Decision.RETRYABLE;
        }
        if (status >= 400) {
          return Decision.PERMANENT;
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
