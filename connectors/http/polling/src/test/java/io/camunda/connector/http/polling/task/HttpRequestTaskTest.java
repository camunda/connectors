/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.http.polling.task;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import io.camunda.connector.api.inbound.ProcessInstanceContext;
import io.camunda.connector.http.base.HttpService;
import io.camunda.connector.http.base.model.HttpCommonRequest;
import io.camunda.connector.http.base.model.HttpCommonResult;
import io.camunda.connector.http.base.model.HttpMethod;
import io.camunda.connector.http.polling.model.PollingRuntimeProperties;
import io.camunda.connector.runtime.test.inbound.InboundConnectorContextBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class HttpRequestTaskTest {

  InboundConnectorContextBuilder.TestInboundIntermediateConnectorContext context;
  @Mock private HttpService mockHttpService;
  @Mock private HttpCommonResult httpCommonResult;
  @Mock private ProcessInstanceContext mockProcessInstanceContext;

  public static InboundConnectorContextBuilder getContextBuilder() {
    return InboundConnectorContextBuilder.create();
  }

  @BeforeEach
  void init() {
    context = getContextBuilder().buildIntermediateConnectorContext();
  }

  @Test
  public void shouldExecuteAndCorrelateHttpRequestOnRun() {
    // Given
    var pollingRuntimeProperties = new PollingRuntimeProperties();
    pollingRuntimeProperties.setUrl("http://dummyUrl.com");
    pollingRuntimeProperties.setMethod(HttpMethod.GET);

    HttpRequestTask task =
        new HttpRequestTask(mockHttpService, mockProcessInstanceContext, context);
    when(mockHttpService.executeConnectorRequest(any(HttpCommonRequest.class)))
        .thenReturn(httpCommonResult);
    when(mockProcessInstanceContext.bind(any())).thenReturn(pollingRuntimeProperties);

    // When
    task.run();

    // Then
    verify(mockProcessInstanceContext).correlate(httpCommonResult);
  }

  @Test
  public void shouldBindOnlyRuntimeProperties() {
    var pollingRuntimeProperties = new PollingRuntimeProperties();
    pollingRuntimeProperties.setUrl("http://dummyUrl.com");
    pollingRuntimeProperties.setMethod(HttpMethod.GET);

    context = spy(context);
    // Given
    HttpRequestTask task =
        new HttpRequestTask(mockHttpService, mockProcessInstanceContext, context);
    when(mockHttpService.executeConnectorRequest(any(HttpCommonRequest.class)))
        .thenReturn(httpCommonResult);
    when(mockProcessInstanceContext.bind(any())).thenReturn(pollingRuntimeProperties);
    // When
    task.run();

    // Then
    verify(mockProcessInstanceContext, times(1)).bind(PollingRuntimeProperties.class);
    verify(mockProcessInstanceContext).correlate(httpCommonResult);
  }

  @Test
  public void shouldHandleExceptionWhileExecutingHttpRequest() {
    // Given
    var pollingRuntimeProperties = new PollingRuntimeProperties();
    pollingRuntimeProperties.setUrl("http://dummyUrl.com");
    pollingRuntimeProperties.setMethod(HttpMethod.GET);
    HttpRequestTask task =
        new HttpRequestTask(mockHttpService, mockProcessInstanceContext, context);
    when(mockHttpService.executeConnectorRequest(any(HttpCommonRequest.class)))
        .thenThrow(new RuntimeException("test exception"));
    when(mockProcessInstanceContext.bind(any())).thenReturn(pollingRuntimeProperties);

    // When
    task.run();

    // Then
    verify(mockProcessInstanceContext, never()).correlate(any());
  }
}
