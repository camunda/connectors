/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.embeddingstore;

import io.camunda.connector.api.error.ConnectorException;
import java.time.Duration;
import java.util.Optional;
import java.util.function.Supplier;

final class AwsWait {

  private static final Duration POLL_INTERVAL = Duration.ofSeconds(1);

  private AwsWait() {}

  /** Polls until the check returns a value, failing with the given message after the timeout. */
  static <T> T until(Supplier<Optional<T>> check, Duration timeout, String timeoutMessage) {
    long deadline = System.nanoTime() + timeout.toNanos();
    while (true) {
      var result = check.get();
      if (result.isPresent()) {
        return result.get();
      }
      if (System.nanoTime() > deadline) {
        throw new ConnectorException(timeoutMessage);
      }
      try {
        Thread.sleep(POLL_INTERVAL);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new ConnectorException(timeoutMessage);
      }
    }
  }
}
