/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.a2a.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.connector.agenticai.a2a.client.model.A2AClientAsToolOperationConfiguration;
import io.camunda.connector.agenticai.a2a.client.model.A2AClientAsToolRequest;
import io.camunda.connector.agenticai.a2a.client.model.A2AClientOperationConfiguration.FetchAgentCardOperationConfiguration;
import io.camunda.connector.agenticai.a2a.client.model.A2AClientOperationConfiguration.SendMessageOperationConfiguration;
import io.camunda.connector.agenticai.a2a.client.model.A2AClientRequest;
import io.camunda.connector.agenticai.a2a.client.model.A2AClientRequest.A2AClientRequestData.ConnectionConfiguration;
import io.camunda.connector.agenticai.a2a.client.model.result.A2AClientResult;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class A2AClientAsToolFunctionTest {

  @Mock private A2AClientHandler handler;
  @Mock private OutboundConnectorContext context;

  private A2AClientAsToolFunction a2AClientAsToolFunction;

  @BeforeEach
  void setUp() {
    a2AClientAsToolFunction = new A2AClientAsToolFunction(handler);
  }

  @Test
  void executesFetchAgentCardOperation_andPassesConvertedRequestToHandler() throws Exception {
    var connection =
        new ConnectionConfiguration("https://a2a.example.com", ".well-known/agent.json");
    var operationCfg =
        new A2AClientAsToolOperationConfiguration("fetchAgentCard", null, Duration.ofSeconds(10));
    var request =
        new A2AClientAsToolRequest(
            new A2AClientAsToolRequest.A2AClientRequestData(connection, operationCfg));

    when(context.bindVariables(A2AClientAsToolRequest.class)).thenReturn(request);

    var handlerResult = mock(A2AClientResult.class);
    when(handler.handle(any())).thenReturn(handlerResult);

    var result = a2AClientAsToolFunction.execute(context);

    assertThat(result).isSameAs(handlerResult);

    ArgumentCaptor<A2AClientRequest> captor = ArgumentCaptor.forClass(A2AClientRequest.class);
    verify(handler).handle(captor.capture());
    var passedRequest = captor.getValue();

    assertThat(passedRequest.data().connection().url()).isEqualTo("https://a2a.example.com");
    assertThat(passedRequest.data().connection().agentCardLocation())
        .isEqualTo(".well-known/agent.json");
    assertThat(passedRequest.data().operation())
        .isInstanceOf(FetchAgentCardOperationConfiguration.class);
  }

  @Test
  void executesSendMessageOperation_andBuildsCorrectOperationConfiguration() throws Exception {
    var connection = new ConnectionConfiguration("https://a2a.example.com", null);
    var params = Map.<String, Object>of("message", "Hello, agent!");
    var timeout = Duration.ofSeconds(45);
    var operationCfg = new A2AClientAsToolOperationConfiguration("sendMessage", params, timeout);
    var request =
        new A2AClientAsToolRequest(
            new A2AClientAsToolRequest.A2AClientRequestData(connection, operationCfg));

    when(context.bindVariables(A2AClientAsToolRequest.class)).thenReturn(request);

    var handlerResult = mock(A2AClientResult.class);
    when(handler.handle(any())).thenReturn(handlerResult);

    var result = a2AClientAsToolFunction.execute(context);

    assertThat(result).isSameAs(handlerResult);

    ArgumentCaptor<A2AClientRequest> captor = ArgumentCaptor.forClass(A2AClientRequest.class);
    verify(handler).handle(captor.capture());
    var passedRequest = captor.getValue();

    assertThat(passedRequest.data().operation())
        .isInstanceOf(SendMessageOperationConfiguration.class);
    var op = (SendMessageOperationConfiguration) passedRequest.data().operation();
    assertThat(op.params().text()).isEqualTo("Hello, agent!");
    assertThat(op.params().documents()).isEqualTo(List.of());
    assertThat(op.timeout()).isEqualTo(timeout);
  }

  @Test
  void throwsWhenMessageParamMissingForSendMessageOperation() {
    var connection = new ConnectionConfiguration("https://a2a.example.com", null);
    var operationCfg =
        new A2AClientAsToolOperationConfiguration("sendMessage", null, Duration.ofSeconds(1));
    var request =
        new A2AClientAsToolRequest(
            new A2AClientAsToolRequest.A2AClientRequestData(connection, operationCfg));

    when(context.bindVariables(A2AClientAsToolRequest.class)).thenReturn(request);

    assertThatThrownBy(() -> a2AClientAsToolFunction.execute(context))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("The 'message' parameter is required for the 'sendMessage' operation.");
  }

  @Test
  void throwsWhenUnsupportedOperation() {
    var connection = new ConnectionConfiguration("https://a2a.example.com", null);
    var operationCfg =
        new A2AClientAsToolOperationConfiguration("unknown", Map.of(), Duration.ofSeconds(1));
    var request =
        new A2AClientAsToolRequest(
            new A2AClientAsToolRequest.A2AClientRequestData(connection, operationCfg));

    when(context.bindVariables(A2AClientAsToolRequest.class)).thenReturn(request);

    assertThatThrownBy(() -> a2AClientAsToolFunction.execute(context))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Unsupported operation: 'unknown'");
  }
}
