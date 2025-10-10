/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.a2a.inbound.task;

import io.camunda.connector.agenticai.a2a.client.api.A2aSdkClientFactory;
import io.camunda.connector.agenticai.a2a.client.convert.A2aSdkObjectConverter;
import io.camunda.connector.agenticai.a2a.inbound.model.A2aPollingRequest;
import io.camunda.connector.agenticai.a2a.inbound.service.A2aTaskPollingExecutorService;
import io.camunda.connector.api.inbound.Health;
import io.camunda.connector.api.inbound.InboundIntermediateConnectorContext;
import io.camunda.connector.api.inbound.ProcessInstanceContext;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Responsible for handling A2A polling operations. Each instance of this class is used for one
 * flowNode and manages polling across all its corresponding processInstances.
 */
public class A2aProcessInstancesFetcherTask implements Runnable {
  private static final Logger LOGGER =
      LoggerFactory.getLogger(A2aProcessInstancesFetcherTask.class);

  private final InboundIntermediateConnectorContext context;
  private final A2aTaskPollingExecutorService executorService;
  private final A2aSdkClientFactory clientFactory;
  private final A2aSdkObjectConverter objectConverter;
  private final A2aPollingRequest pollingRequest;
  private final ConcurrentHashMap<String, ScheduledPoll> runningPollTasks;
  private ScheduledFuture<?> mainTaskFuture;

  public A2aProcessInstancesFetcherTask(
      final InboundIntermediateConnectorContext context,
      final A2aTaskPollingExecutorService executorService,
      final A2aSdkClientFactory clientFactory,
      A2aSdkObjectConverter objectConverter) {
    this.context = context;
    this.executorService = executorService;
    this.clientFactory = clientFactory;
    this.pollingRequest = context.bindProperties(A2aPollingRequest.class);
    this.objectConverter = objectConverter;
    this.runningPollTasks = new ConcurrentHashMap<>();
  }

  @Override
  public void run() {
    try {
      List<ProcessInstanceContext> processInstanceContexts = context.getProcessInstanceContexts();
      removeInactiveTasks(processInstanceContexts);
      processInstanceContexts.forEach(this::scheduleRequest);
      context.reportHealth(Health.up("Process instances", processInstanceContexts.size()));
    } catch (Exception e) {
      LOGGER.error("An error occurred: {}", e.getMessage(), e);
      context.reportHealth(Health.down(e));
    }
  }

  private void removeInactiveTasks(final List<ProcessInstanceContext> processInstanceContexts) {
    List<String> activeTasks =
        processInstanceContexts.stream().map(this::getRequestTaskKey).toList();

    List<Map.Entry<String, ScheduledPoll>> inactiveTasks =
        runningPollTasks.entrySet().stream()
            .filter(entry -> !activeTasks.contains(entry.getKey()))
            .toList();

    inactiveTasks.forEach(
        entry -> {
          entry.getValue().cancel();
          runningPollTasks.remove(entry.getKey());
        });
  }

  private void scheduleRequest(ProcessInstanceContext processInstanceContext) {
    String taskKey = getRequestTaskKey(processInstanceContext);
    runningPollTasks.computeIfAbsent(
        taskKey,
        (key) -> {
          final var task =
              new A2aTaskPollingTask(
                  context, processInstanceContext, pollingRequest, clientFactory, objectConverter);

          final var future =
              this.executorService.scheduleWithFixedDelay(
                  task, pollingRequest.data().taskPollingInterval());

          return new ScheduledPoll(task, future);
        });
  }

  private String getRequestTaskKey(final ProcessInstanceContext processInstanceContext) {
    return context.getDefinition().deduplicationId() + processInstanceContext.getKey();
  }

  public void start() {
    mainTaskFuture =
        executorService.scheduleWithFixedDelay(
            this, pollingRequest.data().processPollingInterval());
  }

  public void stop() {
    if (mainTaskFuture != null) {
      mainTaskFuture.cancel(true);
    }

    runningPollTasks.values().forEach(ScheduledPoll::cancel);
    runningPollTasks.clear();
  }

  private record ScheduledPoll(A2aTaskPollingTask task, ScheduledFuture<?> future) {
    public void cancel() {
      future.cancel(true);
      task.close();
    }
  }
}
