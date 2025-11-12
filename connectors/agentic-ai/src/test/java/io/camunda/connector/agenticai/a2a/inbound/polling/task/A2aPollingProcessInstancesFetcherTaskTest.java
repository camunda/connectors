/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.a2a.inbound.polling.task;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.agenticai.a2a.common.api.A2aAgentCardFetcher;
import io.camunda.connector.agenticai.a2a.common.api.A2aClientFactory;
import io.camunda.connector.agenticai.a2a.common.convert.A2aPartToContentConverterImpl;
import io.camunda.connector.agenticai.a2a.common.convert.A2aSdkObjectConverter;
import io.camunda.connector.agenticai.a2a.common.convert.A2aSdkObjectConverterImpl;
import io.camunda.connector.agenticai.a2a.inbound.polling.model.A2aPollingActivationProperties;
import io.camunda.connector.agenticai.a2a.inbound.polling.model.A2aPollingActivationProperties.A2aPollingActivationPropertiesData;
import io.camunda.connector.agenticai.a2a.inbound.polling.service.A2aPollingExecutorService;
import io.camunda.connector.api.inbound.Health;
import io.camunda.connector.api.inbound.InboundConnectorDefinition;
import io.camunda.connector.api.inbound.InboundIntermediateConnectorContext;
import io.camunda.connector.api.inbound.ProcessInstanceContext;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockedConstruction;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class A2aPollingProcessInstancesFetcherTaskTest {

  private static final Duration PROCESS_POLLING_INTERVAL = Duration.ofSeconds(5);
  private static final Duration TASK_POLLING_INTERVAL = Duration.ofSeconds(30);
  private static final String DEDUPLICATION_ID = "dedup-123";

  @Mock private InboundIntermediateConnectorContext context;
  @Mock private InboundConnectorDefinition inboundConnectorDefinition;
  @Mock private A2aPollingExecutorService executorService;
  @Mock private A2aAgentCardFetcher agentCardFetcher;
  @Mock private A2aClientFactory clientFactory;
  @Mock private ScheduledFuture<?> scheduledFuture;

  @Captor private ArgumentCaptor<A2aPollingTask> pollingTaskCaptor;

  private final A2aSdkObjectConverter objectConverter =
      new A2aSdkObjectConverterImpl(new A2aPartToContentConverterImpl());
  private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

  private A2aPollingProcessInstancesFetcherTask fetcherTask;

  @BeforeEach
  void setUp() {
    final var activationProperties =
        new A2aPollingActivationProperties(
            new A2aPollingActivationPropertiesData(
                PROCESS_POLLING_INTERVAL, TASK_POLLING_INTERVAL));

    when(context.bindProperties(A2aPollingActivationProperties.class))
        .thenReturn(activationProperties);

    fetcherTask =
        new A2aPollingProcessInstancesFetcherTask(
            context,
            executorService,
            agentCardFetcher,
            clientFactory,
            objectConverter,
            objectMapper);
  }

  @Test
  void schedulesNoTasksWhenNoProcessInstances() {
    when(context.getProcessInstanceContexts()).thenReturn(List.of());

    fetcherTask.run();

    verify(executorService, never())
        .scheduleWithFixedDelay(any(Runnable.class), any(Duration.class));
    verify(context)
        .reportHealth(
            argThat(
                health ->
                    health.getStatus() == Health.Status.UP
                        && health.getDetails().get("Process instances").equals(0)));
  }

  @Test
  void schedulesTaskForSingleProcessInstance() {
    ProcessInstanceContext processInstanceContext = mockProcessInstanceContext(1L);

    when(context.getProcessInstanceContexts()).thenReturn(List.of(processInstanceContext));
    when(context.getDefinition()).thenReturn(inboundConnectorDefinition);
    when(inboundConnectorDefinition.deduplicationId()).thenReturn(DEDUPLICATION_ID);

    doReturn(scheduledFuture)
        .when(executorService)
        .scheduleWithFixedDelay(any(A2aPollingTask.class), eq(TASK_POLLING_INTERVAL));

    fetcherTask.run();

    verify(executorService)
        .scheduleWithFixedDelay(pollingTaskCaptor.capture(), eq(TASK_POLLING_INTERVAL));

    assertThat(pollingTaskCaptor.getValue()).isInstanceOf(A2aPollingTask.class);
    verify(context)
        .reportHealth(
            argThat(
                health ->
                    health.getStatus() == Health.Status.UP
                        && health.getDetails().get("Process instances").equals(1)));
  }

  @Test
  void schedulesTasksForMultipleProcessInstances() {
    ProcessInstanceContext pi1 = mockProcessInstanceContext(1L);
    ProcessInstanceContext pi2 = mockProcessInstanceContext(2L);
    ProcessInstanceContext pi3 = mockProcessInstanceContext(3L);

    when(context.getProcessInstanceContexts()).thenReturn(List.of(pi1, pi2, pi3));
    when(context.getDefinition()).thenReturn(inboundConnectorDefinition);
    when(inboundConnectorDefinition.deduplicationId()).thenReturn(DEDUPLICATION_ID);

    ScheduledFuture<?> future1 = mock(ScheduledFuture.class);
    ScheduledFuture<?> future2 = mock(ScheduledFuture.class);
    ScheduledFuture<?> future3 = mock(ScheduledFuture.class);

    doReturn(future1)
        .doReturn(future2)
        .doReturn(future3)
        .when(executorService)
        .scheduleWithFixedDelay(any(A2aPollingTask.class), eq(TASK_POLLING_INTERVAL));

    List<ProcessInstanceContext> constructedProcessInstanceContexts = new ArrayList<>();
    try (MockedConstruction<A2aPollingTask> mockedConstruction =
        mockConstruction(
            A2aPollingTask.class,
            (mock, context) ->
                constructedProcessInstanceContexts.add(
                    (ProcessInstanceContext) context.arguments().get(1)))) {

      fetcherTask.run();

      assertThat(mockedConstruction.constructed()).hasSize(3);
      assertThat(constructedProcessInstanceContexts).containsExactly(pi1, pi2, pi3);

      verify(executorService)
          .scheduleWithFixedDelay(
              eq(mockedConstruction.constructed().get(0)), eq(TASK_POLLING_INTERVAL));
      verify(executorService)
          .scheduleWithFixedDelay(
              eq(mockedConstruction.constructed().get(1)), eq(TASK_POLLING_INTERVAL));
      verify(executorService)
          .scheduleWithFixedDelay(
              eq(mockedConstruction.constructed().get(2)), eq(TASK_POLLING_INTERVAL));

      verify(context)
          .reportHealth(
              argThat(
                  health ->
                      health.getStatus() == Health.Status.UP
                          && health.getDetails().get("Process instances").equals(3)));
    }
  }

  @Test
  void schedulesTaskOnlyOnceForSameProcessInstance() {
    ProcessInstanceContext processInstanceContext = mockProcessInstanceContext(1L);

    when(context.getProcessInstanceContexts()).thenReturn(List.of(processInstanceContext));
    when(context.getDefinition()).thenReturn(inboundConnectorDefinition);
    when(inboundConnectorDefinition.deduplicationId()).thenReturn(DEDUPLICATION_ID);

    doReturn(scheduledFuture)
        .when(executorService)
        .scheduleWithFixedDelay(any(A2aPollingTask.class), eq(TASK_POLLING_INTERVAL));

    List<ProcessInstanceContext> constructedProcessInstanceContexts = new ArrayList<>();
    try (MockedConstruction<A2aPollingTask> mockedConstruction =
        mockConstruction(
            A2aPollingTask.class,
            (mock, context) ->
                constructedProcessInstanceContexts.add(
                    (ProcessInstanceContext) context.arguments().get(1)))) {

      fetcherTask.run();
      fetcherTask.run();

      assertThat(mockedConstruction.constructed()).hasSize(1);
      assertThat(constructedProcessInstanceContexts).containsExactly(processInstanceContext);

      verify(executorService, times(1))
          .scheduleWithFixedDelay(
              mockedConstruction.constructed().getFirst(), TASK_POLLING_INTERVAL);
    }
  }

  @Test
  void removesInactiveTasksWhenProcessInstanceNoLongerActive() {
    ProcessInstanceContext pi1 = mockProcessInstanceContext(1L);
    ProcessInstanceContext pi2 = mockProcessInstanceContext(2L);

    when(context.getDefinition()).thenReturn(inboundConnectorDefinition);
    when(inboundConnectorDefinition.deduplicationId()).thenReturn(DEDUPLICATION_ID);

    ScheduledFuture<?> future1 = mock(ScheduledFuture.class);
    ScheduledFuture<?> future2 = mock(ScheduledFuture.class);

    doReturn(future1)
        .doReturn(future2)
        .when(executorService)
        .scheduleWithFixedDelay(pollingTaskCaptor.capture(), eq(TASK_POLLING_INTERVAL));

    try (MockedConstruction<A2aPollingTask> mockedConstruction =
        mockConstruction(A2aPollingTask.class)) {
      when(context.getProcessInstanceContexts()).thenReturn(List.of(pi1, pi2));
      fetcherTask.run();

      when(context.getProcessInstanceContexts()).thenReturn(List.of(pi1));
      fetcherTask.run();

      final var pollingTasks = pollingTaskCaptor.getAllValues();
      verify(executorService).scheduleWithFixedDelay(pollingTasks.get(0), TASK_POLLING_INTERVAL);
      verify(executorService).scheduleWithFixedDelay(pollingTasks.get(1), TASK_POLLING_INTERVAL);

      verify(pollingTasks.get(1)).close();
      verify(future2).cancel(true);
    }
  }

  @Test
  void removesMultipleInactiveTasks() {
    ProcessInstanceContext pi1 = mockProcessInstanceContext(1L);
    ProcessInstanceContext pi2 = mockProcessInstanceContext(2L);
    ProcessInstanceContext pi3 = mockProcessInstanceContext(3L);

    ScheduledFuture<?> future1 = mock(ScheduledFuture.class);
    ScheduledFuture<?> future2 = mock(ScheduledFuture.class);
    ScheduledFuture<?> future3 = mock(ScheduledFuture.class);

    when(context.getDefinition()).thenReturn(inboundConnectorDefinition);
    when(inboundConnectorDefinition.deduplicationId()).thenReturn(DEDUPLICATION_ID);

    doReturn(future1)
        .doReturn(future2)
        .doReturn(future3)
        .when(executorService)
        .scheduleWithFixedDelay(any(A2aPollingTask.class), eq(TASK_POLLING_INTERVAL));

    when(context.getProcessInstanceContexts()).thenReturn(List.of(pi1, pi2, pi3));
    fetcherTask.run();

    when(context.getProcessInstanceContexts()).thenReturn(List.of());
    fetcherTask.run();

    verify(future1).cancel(true);
    verify(future2).cancel(true);
    verify(future3).cancel(true);
  }

  @Test
  void addsNewTasksWhileKeepingExistingOnes() {
    ProcessInstanceContext pi1 = mockProcessInstanceContext(1L);
    ProcessInstanceContext pi2 = mockProcessInstanceContext(2L);

    ScheduledFuture<?> future1 = mock(ScheduledFuture.class);
    ScheduledFuture<?> future2 = mock(ScheduledFuture.class);

    when(context.getDefinition()).thenReturn(inboundConnectorDefinition);
    when(inboundConnectorDefinition.deduplicationId()).thenReturn(DEDUPLICATION_ID);

    doReturn(future1)
        .doReturn(future2)
        .when(executorService)
        .scheduleWithFixedDelay(any(A2aPollingTask.class), eq(TASK_POLLING_INTERVAL));

    when(context.getProcessInstanceContexts()).thenReturn(List.of(pi1));
    fetcherTask.run();

    when(context.getProcessInstanceContexts()).thenReturn(List.of(pi1, pi2));
    fetcherTask.run();

    verify(future1, never()).cancel(true);
    verify(executorService, times(2))
        .scheduleWithFixedDelay(any(A2aPollingTask.class), eq(TASK_POLLING_INTERVAL));
  }

  @Test
  void reportsHealthDownOnException() {
    when(context.getProcessInstanceContexts())
        .thenThrow(new RuntimeException("Failed to get process instances"));

    fetcherTask.run();

    ArgumentCaptor<Health> healthCaptor = ArgumentCaptor.forClass(Health.class);
    verify(context).reportHealth(healthCaptor.capture());

    Health health = healthCaptor.getValue();
    assertThat(health.getStatus()).isEqualTo(Health.Status.DOWN);
    assertThat(health.getError()).isNotNull();
    assertThat(health.getError().message()).contains("Failed to get process instances");
  }

  @Test
  void startsMainTaskWithCorrectInterval() {
    doReturn(scheduledFuture)
        .when(executorService)
        .scheduleWithFixedDelay(
            any(A2aPollingProcessInstancesFetcherTask.class), eq(PROCESS_POLLING_INTERVAL));

    fetcherTask.start();

    verify(executorService).scheduleWithFixedDelay(eq(fetcherTask), eq(PROCESS_POLLING_INTERVAL));
  }

  @Test
  void stopsMainTaskAndCancelsAllRunningPollTasks() {
    ProcessInstanceContext pi1 = mockProcessInstanceContext(1L);
    ProcessInstanceContext pi2 = mockProcessInstanceContext(2L);

    ScheduledFuture<?> mainFuture = mock(ScheduledFuture.class);
    ScheduledFuture<?> pollFuture1 = mock(ScheduledFuture.class);
    ScheduledFuture<?> pollFuture2 = mock(ScheduledFuture.class);

    when(context.getDefinition()).thenReturn(inboundConnectorDefinition);
    when(inboundConnectorDefinition.deduplicationId()).thenReturn(DEDUPLICATION_ID);

    doReturn(mainFuture)
        .when(executorService)
        .scheduleWithFixedDelay(
            any(A2aPollingProcessInstancesFetcherTask.class), eq(PROCESS_POLLING_INTERVAL));

    doReturn(pollFuture1)
        .doReturn(pollFuture2)
        .when(executorService)
        .scheduleWithFixedDelay(any(A2aPollingTask.class), eq(TASK_POLLING_INTERVAL));

    fetcherTask.start();

    when(context.getProcessInstanceContexts()).thenReturn(List.of(pi1, pi2));
    fetcherTask.run();

    fetcherTask.stop();

    verify(mainFuture).cancel(true);
    verify(pollFuture1).cancel(true);
    verify(pollFuture2).cancel(true);
  }

  @Test
  void stopClearsAllRunningTasks() {
    ProcessInstanceContext processInstanceContext = mockProcessInstanceContext(1L);

    ScheduledFuture<?> mainFuture = mock(ScheduledFuture.class);
    ScheduledFuture<?> pollFuture = mock(ScheduledFuture.class);

    when(context.getDefinition()).thenReturn(inboundConnectorDefinition);
    when(inboundConnectorDefinition.deduplicationId()).thenReturn(DEDUPLICATION_ID);

    doReturn(mainFuture)
        .when(executorService)
        .scheduleWithFixedDelay(
            any(A2aPollingProcessInstancesFetcherTask.class), eq(PROCESS_POLLING_INTERVAL));

    doReturn(pollFuture)
        .when(executorService)
        .scheduleWithFixedDelay(any(A2aPollingTask.class), eq(TASK_POLLING_INTERVAL));

    fetcherTask.start();
    when(context.getProcessInstanceContexts()).thenReturn(List.of(processInstanceContext));
    fetcherTask.run();

    fetcherTask.stop();

    when(context.getProcessInstanceContexts()).thenReturn(List.of(processInstanceContext));
    fetcherTask.run();

    verify(executorService, times(2))
        .scheduleWithFixedDelay(any(A2aPollingTask.class), eq(TASK_POLLING_INTERVAL));
  }

  @Test
  void stopHandlesNullMainTaskFuture() {
    fetcherTask.stop();

    verifyNoInteractions(scheduledFuture);
  }

  @Test
  void usesProvidedPollingIntervals() {
    Duration customProcessPollingInterval = Duration.ofSeconds(10);
    Duration customTaskPollingInterval = Duration.ofSeconds(60);

    A2aPollingActivationProperties customProperties =
        new A2aPollingActivationProperties(
            new A2aPollingActivationPropertiesData(
                customProcessPollingInterval, customTaskPollingInterval));

    when(context.bindProperties(A2aPollingActivationProperties.class)).thenReturn(customProperties);

    A2aPollingProcessInstancesFetcherTask customFetcherTask =
        new A2aPollingProcessInstancesFetcherTask(
            context,
            executorService,
            agentCardFetcher,
            clientFactory,
            objectConverter,
            objectMapper);

    ProcessInstanceContext processInstanceContext = mockProcessInstanceContext(1L);

    when(context.getProcessInstanceContexts()).thenReturn(List.of(processInstanceContext));
    when(context.getDefinition()).thenReturn(inboundConnectorDefinition);
    when(inboundConnectorDefinition.deduplicationId()).thenReturn(DEDUPLICATION_ID);

    ScheduledFuture<?> mainFuture = mock(ScheduledFuture.class);
    ScheduledFuture<?> pollFuture = mock(ScheduledFuture.class);

    doReturn(mainFuture)
        .when(executorService)
        .scheduleWithFixedDelay(any(A2aPollingProcessInstancesFetcherTask.class), any());
    doReturn(pollFuture)
        .when(executorService)
        .scheduleWithFixedDelay(any(A2aPollingTask.class), any());

    customFetcherTask.start();
    customFetcherTask.run();

    verify(executorService)
        .scheduleWithFixedDelay(eq(customFetcherTask), eq(customProcessPollingInterval));
    verify(executorService)
        .scheduleWithFixedDelay(any(A2aPollingTask.class), eq(customTaskPollingInterval));
  }

  private ProcessInstanceContext mockProcessInstanceContext(Long key) {
    ProcessInstanceContext processInstanceContext = mock(ProcessInstanceContext.class);
    when(processInstanceContext.getKey()).thenReturn(key);
    return processInstanceContext;
  }
}
