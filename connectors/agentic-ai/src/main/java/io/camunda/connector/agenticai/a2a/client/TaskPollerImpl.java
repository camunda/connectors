/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.a2a.client;

import io.a2a.client.Client;
import io.a2a.spec.TaskQueryParams;
import io.camunda.connector.agenticai.a2a.client.model.result.A2aSendMessageResult;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class TaskPollerImpl implements TaskPoller {
  private final ScheduledExecutorService scheduler;
  private final A2aSendMessageResponseHandler sendMessageResponseHandler;

  public TaskPollerImpl(
      ScheduledExecutorService scheduler,
      A2aSendMessageResponseHandler sendMessageResponseHandler) {
    this.scheduler = scheduler;
    this.sendMessageResponseHandler = sendMessageResponseHandler;
  }

  @Override
  public CompletableFuture<A2aSendMessageResult> poll(
      String taskId, Client client, Duration pollInterval, Duration timeout) {
    CompletableFuture<A2aSendMessageResult> future = new CompletableFuture<>();
    Instant startTime = Instant.now();

    Runnable pollTask =
        new Runnable() {
          @Override
          public void run() {
            if (Duration.between(startTime, Instant.now()).compareTo(timeout) >= 0) {
              future.completeExceptionally(new TimeoutException("Polling timed out"));
              return;
            }
            try {
              var task = client.getTask(new TaskQueryParams(taskId));
              A2aSendMessageResult.A2aTaskResult result =
                  sendMessageResponseHandler.handleTask(task);
              if (result.task().status().state().isSubmittedOrWorking()) {
                // schedule next poll
                scheduler.schedule(this, pollInterval.toMillis(), TimeUnit.MILLISECONDS);
              } else if (result.task().status().state().isCompleted()) {
                future.complete(result);
              }
            } catch (Exception e) {
              future.completeExceptionally(e);
            }
          }
        };

    pollTask.run();
    return future;
  }
}
