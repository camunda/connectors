/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.http.polling;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.connector.api.inbound.InboundIntermediateConnectorContext;
import io.camunda.connector.http.base.HttpService;
import io.camunda.connector.http.polling.model.PollingIntervalConfiguration;
import io.camunda.connector.http.polling.service.SharedExecutorService;
import io.camunda.connector.http.polling.task.ProcessInstancesFetcherTask;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class HttpPollingConnectorTest {

  private static final Long DEFAULT_OPERATE_INTERVAL = 5000L;
  @Mock private HttpService httpService;
  @Mock private SharedExecutorService executorService;
  @Mock private InboundIntermediateConnectorContext context;
  @Mock private ScheduledExecutorService mockScheduledExecutorService;

  private HttpPollingConnector httpPollingConnector;

  @BeforeEach
  public void setUp() {
    httpPollingConnector = new HttpPollingConnector(httpService, executorService);
    when(context.bindProperties(any())).thenReturn(new PollingIntervalConfiguration());
    when(executorService.getExecutorService()).thenReturn(mockScheduledExecutorService);
  }

  @Test
  public void testActivate() {
    // Given
    // When
    httpPollingConnector.activate(context);
    // Then
    verify(mockScheduledExecutorService, times(1))
        .scheduleWithFixedDelay(
            any(ProcessInstancesFetcherTask.class),
            eq(0L),
            eq(DEFAULT_OPERATE_INTERVAL),
            eq(TimeUnit.MILLISECONDS));
  }
}
