/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.lifecycle;

import org.slf4j.Logger;

/**
 * Logs failures of the lifecycle layer without leaking content. Events and exception messages can
 * contain prompts and tool results, so the warning names only the listener, the callback and the
 * exception class; the throwable itself is logged at debug level.
 */
final class LifecycleFailureLog {

  private LifecycleFailureLog() {}

  static void listenerFailed(Logger logger, String listenerId, String callback, Throwable e) {
    logger.warn(
        "Agent lifecycle listener '{}' failed on {}: {}",
        listenerId,
        callback,
        e.getClass().getName());
    logger.debug("Agent lifecycle listener '{}' failure on {}", listenerId, callback, e);
  }

  static void eventFailed(Logger logger, String callback, Throwable e) {
    logger.warn(
        "Agent lifecycle event for {} could not be built: {}", callback, e.getClass().getName());
    logger.debug("Agent lifecycle event failure for {}", callback, e);
  }
}
