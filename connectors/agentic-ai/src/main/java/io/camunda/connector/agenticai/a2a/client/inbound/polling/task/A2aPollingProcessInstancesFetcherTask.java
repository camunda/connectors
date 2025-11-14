/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.a2a.client.inbound.polling.task;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.agenticai.a2a.client.common.A2aAgentCardFetcher;
import io.camunda.connector.agenticai.a2a.client.common.convert.A2aSdkObjectConverter;
import io.camunda.connector.agenticai.a2a.client.common.sdk.A2aSdkClientFactory;
import io.camunda.connector.agenticai.a2a.client.inbound.polling.model.A2aPollingActivationProperties;
import io.camunda.connector.agenticai.a2a.client.inbound.polling.service.A2aPollingExecutorService;
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
public class A2aPollingProcessInstancesFetcherTask implements Runnable {
  private static final Logger LOGGER =
      LoggerFactory.getLogger(A2aPollingProcessInstancesFetcherTask.class);

  private final InboundIntermediateConnectorContext context;
  private final A2aPollingExecutorService executorService;
  private final A2aAgentCardFetcher agentCardFetcher;
  private final A2aSdkClientFactory clientFactory;
  private final A2aSdkObjectConverter objectConverter;
  private final ObjectMapper objectMapper;

  private final A2aPollingActivationProperties activationProperties;

  private final ConcurrentHashMap<String, ScheduledPoll> runningPollTasks =
      new ConcurrentHashMap<>();
  private ScheduledFuture<?> mainTaskFuture;

  public A2aPollingProcessInstancesFetcherTask(
      final InboundIntermediateConnectorContext context,
      final A2aPollingExecutorService executorService,
      final A2aAgentCardFetcher agentCardFetcher,
      final A2aSdkClientFactory clientFactory,
      final A2aSdkObjectConverter objectConverter,
      final ObjectMapper objectMapper) {

    this.context = context;
    this.executorService = executorService;
    this.agentCardFetcher = agentCardFetcher;
    this.clientFactory = clientFactory;
    this.objectConverter = objectConverter;
    this.objectMapper = objectMapper;
    this.activationProperties = context.bindProperties(A2aPollingActivationProperties.class);
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
              new A2aPollingTask(
                  context,
                  processInstanceContext,
                  agentCardFetcher,
                  clientFactory,
                  objectConverter,
                  objectMapper);

          final var future =
              this.executorService.scheduleWithFixedDelay(
                  task, activationProperties.data().taskPollingInterval());

          return new ScheduledPoll(task, future);
        });
  }

  private String getRequestTaskKey(final ProcessInstanceContext processInstanceContext) {
    return context.getDefinition().deduplicationId() + processInstanceContext.getKey();
  }

  public void start() {
    mainTaskFuture =
        executorService.scheduleWithFixedDelay(
            this, activationProperties.data().processPollingInterval());
  }

  public void stop() {
    if (mainTaskFuture != null) {
      mainTaskFuture.cancel(true);
    }

    runningPollTasks.values().forEach(ScheduledPoll::cancel);
    runningPollTasks.clear();
  }

  private record ScheduledPoll(A2aPollingTask task, ScheduledFuture<?> future) {
    public void cancel() {
      future.cancel(true);
      task.close();
    }
  }
}
