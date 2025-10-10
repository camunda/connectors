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

public class A2aTaskPollingExecutorService {
  private static final Logger LOG = LoggerFactory.getLogger(A2aTaskPollingExecutorService.class);

  private final ScheduledExecutorService executorService;

  public A2aTaskPollingExecutorService(final int threadPoolSize) {
    LOG.debug(
        "Creating A2A task polling executor service with a thread pool size of {}", threadPoolSize);
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
    LOG.debug("Shutting down A2A task polling executor service");
    executorService.shutdownNow();
  }
}
