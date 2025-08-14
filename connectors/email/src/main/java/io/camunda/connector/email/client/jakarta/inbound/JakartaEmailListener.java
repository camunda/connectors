/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.email.client.jakarta.inbound;

import dev.failsafe.Failsafe;
import dev.failsafe.RetryPolicy;
import io.camunda.connector.api.inbound.Health;
import io.camunda.connector.api.inbound.InboundConnectorContext;
import io.camunda.connector.api.inbound.Severity;
import io.camunda.connector.email.client.EmailListener;
import io.camunda.connector.email.client.jakarta.utils.JakartaUtils;
import io.camunda.connector.email.exception.EmailConnectorException;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.*;

public class JakartaEmailListener implements EmailListener {

  private static final int INFINITE_RETRIES = -1;
  private final ScheduledExecutorService scheduledExecutorService =
      Executors.newSingleThreadScheduledExecutor(r -> new Thread(r, "Jakarta Email Listener"));
  private CompletableFuture<PollingManager> pollingManagerFuture;
  private CompletableFuture<ScheduledFuture<?>> scheduledPollingManagerFuture;

  public JakartaEmailListener() {}

  public static JakartaEmailListener create() {
    return new JakartaEmailListener();
  }

  @Override
  public void startListener(InboundConnectorContext context) {
    RetryPolicy<Object> retryPolicy =
        RetryPolicy.builder()
            .handle(EmailConnectorException.class)
            .withBackoff(Duration.of(5, ChronoUnit.SECONDS), Duration.of(1, ChronoUnit.HOURS))
            .withMaxAttempts(INFINITE_RETRIES)
            .onRetry(
                event ->
                    context.log(
                        activity ->
                            activity
                                .withSeverity(Severity.WARNING)
                                .withTag("Context creation")
                                .withMessage(
                                    "Retrying after attempt %s failed ..."
                                        .formatted(event.getAttemptCount()))))
            .build();
    this.pollingManagerFuture =
        Failsafe.with(retryPolicy)
            .with(scheduledExecutorService)
            .getAsync(() -> PollingManager.create(context, new JakartaUtils()));
    this.scheduledPollingManagerFuture =
        this.pollingManagerFuture.thenApply(
            pollingManager ->
                scheduledExecutorService.scheduleWithFixedDelay(
                    pollingManager::poll, 0, pollingManager.delay(), TimeUnit.SECONDS));
    this.scheduledPollingManagerFuture.whenComplete(
        (pollingManager, throwable) -> {
          if (throwable != null) {
            context.reportHealth(Health.down(Health.Error.from(throwable)));
            context.log(
                activity ->
                    activity
                        .withSeverity(Severity.ERROR)
                        .withTag("Context creation")
                        .withMessage(throwable.getMessage()));
          } else context.reportHealth(Health.up());
        });
  }

  @Override
  public void stopListener() {
    if (this.scheduledPollingManagerFuture.isDone()) {
      this.scheduledPollingManagerFuture.join().cancel(true);
    }
    this.scheduledPollingManagerFuture.join().cancel(true);
    if (this.pollingManagerFuture.isDone() && !pollingManagerFuture.isCompletedExceptionally()) {
      this.pollingManagerFuture.join().stop();
    } else {
      this.pollingManagerFuture.cancel(true);
    }
  }
}
