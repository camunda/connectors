/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.a2a.inbound.service;

import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;

public class A2aPollingExecutorService {
  private static final Logger LOGGER = LoggerFactory.getLogger(A2aPollingExecutorService.class);

  private final ScheduledExecutorService executorService;

  public A2aPollingExecutorService(final int threadPoolSize) {
    LOGGER.debug(
        "Creating A2A polling executor service with a thread pool size of {}", threadPoolSize);
    this.executorService = Executors.newScheduledThreadPool(threadPoolSize);
  }

  public ScheduledFuture<?> scheduleWithFixedDelay(Runnable command, Duration delay) {
    return executorService.scheduleWithFixedDelay(
        command, 0, delay.toMillis(), TimeUnit.MILLISECONDS);
  }

  /**
   * Will automatically be called by the Spring context on shutdown (see @{@link
   * Bean#destroyMethod()}) - do not rename.
   */
  public void shutdown() {
    LOGGER.debug("Shutting down A2A polling executor service");
    executorService.shutdownNow();
  }
}
