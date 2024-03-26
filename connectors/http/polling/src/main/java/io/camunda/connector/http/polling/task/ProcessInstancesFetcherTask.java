/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.http.polling.task;

import io.camunda.connector.api.inbound.Health;
import io.camunda.connector.api.inbound.InboundIntermediateConnectorContext;
import io.camunda.connector.api.inbound.ProcessInstanceContext;
import io.camunda.connector.http.base.services.HttpService;
import io.camunda.connector.http.polling.model.PollingIntervalConfiguration;
import io.camunda.connector.http.polling.service.SharedExecutorService;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * PollingOperateTask is responsible for handling HTTP polling operations. Each instance of this
 * class is used for one flowNode and manages polling across all its corresponding processInstances.
 */
public class ProcessInstancesFetcherTask implements Runnable {
  private static final Logger LOGGER = LoggerFactory.getLogger(ProcessInstancesFetcherTask.class);

  private final InboundIntermediateConnectorContext context;
  private final HttpService httpService;
  private final SharedExecutorService executorService;
  private final PollingIntervalConfiguration config;
  private final ConcurrentHashMap<String, ScheduledFuture<?>> runningHttpRequestTaskIds;

  public ProcessInstancesFetcherTask(
      final InboundIntermediateConnectorContext context,
      final HttpService httpService,
      final SharedExecutorService executorService) {
    this.config = context.bindProperties(PollingIntervalConfiguration.class);
    this.context = context;
    this.httpService = httpService;
    this.executorService = executorService;
    this.runningHttpRequestTaskIds = new ConcurrentHashMap<>();
  }

  @Override
  public void run() {
    try {
      List<ProcessInstanceContext> processInstanceContexts = context.getProcessInstanceContexts();
      if (processInstanceContexts != null) {
        removeInactiveTasks(processInstanceContexts);
        processInstanceContexts.forEach(this::scheduleRequest);
        context.reportHealth(Health.up("Process instances", processInstanceContexts.size()));
      }
    } catch (Exception e) {
      LOGGER.error("An error occurred: {}", e.getMessage(), e);
      context.reportHealth(Health.down(e));
    }
  }

  private void removeInactiveTasks(final List<ProcessInstanceContext> processInstanceContexts) {
    List<String> activeTasks =
        processInstanceContexts.stream().map(this::getRequestTaskKey).toList();

    List<Map.Entry<String, ScheduledFuture<?>>> inactiveTasks =
        runningHttpRequestTaskIds.entrySet().stream()
            .filter(entry -> !activeTasks.contains(entry.getKey()))
            .toList();

    inactiveTasks.forEach(
        entry -> {
          entry.getValue().cancel(true);
          runningHttpRequestTaskIds.remove(entry.getKey());
        });
  }

  private void scheduleRequest(ProcessInstanceContext processInstanceContext) {
    String taskKey = getRequestTaskKey(processInstanceContext);
    runningHttpRequestTaskIds.computeIfAbsent(
        taskKey,
        (key) -> {
          var task = new HttpRequestTask(httpService, processInstanceContext, this.context);
          return this.executorService
              .getExecutorService()
              .scheduleWithFixedDelay(
                  task, 0, config.getHttpRequestInterval().toMillis(), TimeUnit.MILLISECONDS);
        });
  }

  private String getRequestTaskKey(final ProcessInstanceContext processInstanceContext) {
    return context.getDefinition().deduplicationId() + processInstanceContext.getKey();
  }

  public void start() {
    executorService
        .getExecutorService()
        .scheduleWithFixedDelay(
            this, 0, config.getOperatePollingInterval().toMillis(), TimeUnit.MILLISECONDS);
  }

  public void stop() {
    runningHttpRequestTaskIds.values().forEach(task -> task.cancel(true));
  }
}
