/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.http.polling.task;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.connector.api.inbound.InboundConnectorDefinition;
import io.camunda.connector.api.inbound.InboundIntermediateConnectorContext;
import io.camunda.connector.api.inbound.ProcessInstanceContext;
import io.camunda.connector.http.base.services.HttpService;
import io.camunda.connector.http.polling.model.PollingIntervalConfiguration;
import io.camunda.connector.http.polling.service.SharedExecutorService;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class ProcessInstancesFetcherTaskTest {

  @Mock private InboundIntermediateConnectorContext mockContext;
  @Mock private HttpService mockHttpService;
  @Mock private SharedExecutorService mockExecutorService;
  @Mock private ScheduledExecutorService mockScheduledExecutorService;
  @Mock private ProcessInstanceContext mockProcessInstanceContext1;
  @Mock private ProcessInstanceContext mockProcessInstanceContext2;
  @Mock private ScheduledFuture<?> mockScheduledFuture;
  @Mock private InboundConnectorDefinition mockInboundConnectorDefinition;

  @Captor private ArgumentCaptor<Runnable> runnableCaptor;

  private ProcessInstancesFetcherTask task;
  private PollingIntervalConfiguration config;

  @BeforeEach
  public void setUp() {
    config = new PollingIntervalConfiguration();
    config.setOperatePollingInterval(Duration.ofMillis(1000));
    config.setHttpRequestInterval(Duration.ofMillis(1000));
    when(mockContext.getDefinition()).thenReturn(mockInboundConnectorDefinition);
    when(mockInboundConnectorDefinition.deduplicationId()).thenReturn("someDeduplicationId");
    when(mockContext.bindProperties(PollingIntervalConfiguration.class)).thenReturn(config);
    when(mockExecutorService.getExecutorService()).thenReturn(mockScheduledExecutorService);
    task = new ProcessInstancesFetcherTask(mockContext, mockHttpService, mockExecutorService);
  }

  @Test
  public void shouldAddNewTasks() {
    // given
    when(mockContext.getProcessInstanceContexts()).thenReturn(List.of(mockProcessInstanceContext1));
    doReturn((ScheduledFuture<?>) mockScheduledFuture)
        .when(mockScheduledExecutorService)
        .scheduleWithFixedDelay(any(), anyLong(), anyLong(), any());
    // when
    task.run();
    // then
    verify(mockScheduledExecutorService, times(1))
        .scheduleWithFixedDelay(any(Runnable.class), eq(0L), eq(1000L), eq(TimeUnit.MILLISECONDS));
  }

  @Test
  public void shouldSkipExistingTasks() {
    // given
    when(mockProcessInstanceContext1.getKey()).thenReturn(1L);
    doReturn((ScheduledFuture<?>) mockScheduledFuture)
        .when(mockScheduledExecutorService)
        .scheduleWithFixedDelay(any(), anyLong(), anyLong(), any());
    when(mockContext.getProcessInstanceContexts())
        .thenReturn(Collections.singletonList(mockProcessInstanceContext1));
    // when run twice
    task.run();
    task.run();

    // then schedule only once
    verify(mockScheduledExecutorService, times(1))
        .scheduleWithFixedDelay(any(Runnable.class), eq(0L), eq(1000L), eq(TimeUnit.MILLISECONDS));
  }

  @Test
  public void shouldRemoveInactiveTasks() {
    // Given two active tasks initially
    when(mockProcessInstanceContext1.getKey()).thenReturn(1L);
    when(mockProcessInstanceContext2.getKey()).thenReturn(2L);

    doReturn((ScheduledFuture<?>) mockScheduledFuture)
        .when(mockScheduledExecutorService)
        .scheduleWithFixedDelay(any(), anyLong(), anyLong(), any());

    when(mockContext.getProcessInstanceContexts())
        .thenReturn(
            List.of(
                mockProcessInstanceContext1,
                mockProcessInstanceContext2)) // Initially two active tasks
        .thenReturn(List.of(mockProcessInstanceContext1)); // Then one becomes inactive

    // Create a map of running tasks
    ConcurrentHashMap<String, ScheduledFuture<?>> runningTasks = new ConcurrentHashMap<>();
    runningTasks.put("someKey1", mockScheduledFuture);
    runningTasks.put("someKey2", mockScheduledFuture);

    // Run task to populate the tasks initially
    task.run();

    // Run task again to trigger the removal of inactive tasks
    task.run();

    // Then verify that the task was canceled
    verify(mockScheduledFuture, times(1)).cancel(true);
  }

  @Test
  public void shouldStopAllRunningTasks() {
    // Given
    when(mockProcessInstanceContext1.getKey()).thenReturn(1L);
    when(mockProcessInstanceContext2.getKey()).thenReturn(2L);
    when(mockContext.getProcessInstanceContexts())
        .thenReturn(List.of(mockProcessInstanceContext1, mockProcessInstanceContext2));
    doReturn((ScheduledFuture<?>) mockScheduledFuture)
        .when(mockScheduledExecutorService)
        .scheduleWithFixedDelay(any(), anyLong(), anyLong(), any());

    // Run task to add new tasks
    task.run();

    // when: Stop tasks
    task.stop();

    // then
    verify(mockScheduledFuture, times(2)).cancel(true);
  }
}
