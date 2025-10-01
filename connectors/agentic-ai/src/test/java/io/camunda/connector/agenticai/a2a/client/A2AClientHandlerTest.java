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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.a2a.spec.AgentCard;
import io.camunda.connector.agenticai.a2a.client.model.A2AClientOperationConfiguration.FetchAgentCardOperationConfiguration;
import io.camunda.connector.agenticai.a2a.client.model.A2AClientOperationConfiguration.SendMessageOperationConfiguration;
import io.camunda.connector.agenticai.a2a.client.model.A2AClientOperationConfiguration.SendMessageOperationConfiguration.Parameters;
import io.camunda.connector.agenticai.a2a.client.model.A2AClientRequest;
import io.camunda.connector.agenticai.a2a.client.model.A2AClientRequest.A2AClientRequestData;
import io.camunda.connector.agenticai.a2a.client.model.A2AClientRequest.A2AClientRequestData.ConnectionConfiguration;
import io.camunda.connector.agenticai.a2a.client.model.ConnectorModeConfiguration;
import io.camunda.connector.agenticai.a2a.client.model.ToolModeOperationConfiguration;
import io.camunda.connector.agenticai.a2a.client.model.result.A2AClientAgentCardResult;
import io.camunda.connector.agenticai.a2a.client.model.result.A2AClientSendMessageResult;
import io.camunda.connector.agenticai.a2a.client.model.result.A2AClientSendMessageResult.TaskState;
import io.camunda.connector.agenticai.model.message.content.TextContent;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class A2AClientHandlerTest {

  @Mock private AgentCardFetcher agentCardFetcher;
  @Mock private MessageSender messageSender;
  @InjectMocks private A2AClientHandlerImpl handler;

  private static final ConnectionConfiguration CONNECTION =
      new ConnectionConfiguration("https://a2a.example.com", null);

  @Nested
  class StandaloneModeTests {
    @Test
    void handleFetchAgentCard() {
      var operation = new FetchAgentCardOperationConfiguration();
      var request =
          new A2AClientRequest(
              new A2AClientRequestData(
                  CONNECTION,
                  new ConnectorModeConfiguration.StandaloneModeConfiguration(operation)));

      var expectedResult = new A2AClientAgentCardResult("name", "desc", List.of());
      when(agentCardFetcher.fetchAgentCard(CONNECTION)).thenReturn(expectedResult);

      var result = handler.handle(request);

      assertThat(result).isSameAs(expectedResult);
      verify(agentCardFetcher).fetchAgentCard(CONNECTION);
      verify(agentCardFetcher, never()).fetchAgentCardRaw(any());
      verify(messageSender, never()).sendMessage(any(), any());
    }

    @Test
    void handleSendMessage() {
      var agentCard = mock(AgentCard.class);
      var operation =
          new SendMessageOperationConfiguration(
              new Parameters("Hello", null), Duration.ofSeconds(1));
      var request =
          new A2AClientRequest(
              new A2AClientRequestData(
                  CONNECTION,
                  new ConnectorModeConfiguration.StandaloneModeConfiguration(operation)));

      when(agentCardFetcher.fetchAgentCardRaw(CONNECTION)).thenReturn(agentCard);
      var expectedSendResult =
          new A2AClientSendMessageResult(
              "resp-1", List.of(new TextContent("ok")), TaskState.COMPLETED);
      when(messageSender.sendMessage(operation, agentCard)).thenReturn(expectedSendResult);

      var result = handler.handle(request);

      assertThat(result).isSameAs(expectedSendResult);
      verify(agentCardFetcher).fetchAgentCardRaw(CONNECTION);
      verify(messageSender).sendMessage(operation, agentCard);
      verify(agentCardFetcher, never()).fetchAgentCard(CONNECTION);
    }
  }

  @Nested
  class ToolModeTests {

    @Test
    void handleFetchAgentCard() {
      var operation =
          new ToolModeOperationConfiguration("fetchAgentCard", null, Duration.ofSeconds(10));
      var request =
          new A2AClientRequest(
              new A2AClientRequestData(
                  CONNECTION, new ConnectorModeConfiguration.ToolModeConfiguration(operation)));

      var expectedResult = new A2AClientAgentCardResult("name", "desc", List.of());
      when(agentCardFetcher.fetchAgentCard(CONNECTION)).thenReturn(expectedResult);

      var result = handler.handle(request);

      assertThat(result).isSameAs(expectedResult);
      verify(agentCardFetcher).fetchAgentCard(CONNECTION);
      verify(agentCardFetcher, never()).fetchAgentCardRaw(any());
      verify(messageSender, never()).sendMessage(any(), any());
    }

    @Test
    void handleSendMessage() {
      var params = Map.<String, Object>of("message", "Hello, agent!");
      var timeout = Duration.ofSeconds(45);
      var operation = new ToolModeOperationConfiguration("sendMessage", params, timeout);
      var request =
          new A2AClientRequest(
              new A2AClientRequestData(
                  CONNECTION, new ConnectorModeConfiguration.ToolModeConfiguration(operation)));

      var agentCard = mock(AgentCard.class);
      when(agentCardFetcher.fetchAgentCardRaw(CONNECTION)).thenReturn(agentCard);

      var expectedSendResult =
          new A2AClientSendMessageResult(
              "resp-1", List.of(new TextContent("ok")), TaskState.COMPLETED);
      when(messageSender.sendMessage(any(), eq(agentCard))).thenReturn(expectedSendResult);

      var result = handler.handle(request);

      assertThat(result).isSameAs(expectedSendResult);

      ArgumentCaptor<SendMessageOperationConfiguration> opCaptor =
          ArgumentCaptor.forClass(SendMessageOperationConfiguration.class);
      verify(messageSender).sendMessage(opCaptor.capture(), eq(agentCard));

      var convertedOperation = opCaptor.getValue();
      assertThat(convertedOperation.params().text()).isEqualTo("Hello, agent!");
      assertThat(convertedOperation.params().documents()).isEqualTo(List.of());
      assertThat(convertedOperation.timeout()).isEqualTo(timeout);

      verify(agentCardFetcher).fetchAgentCardRaw(CONNECTION);
      verify(agentCardFetcher, never()).fetchAgentCard(CONNECTION);
    }

    @Test
    void throwsWhenMessageParamMissing() {
      var operation =
          new ToolModeOperationConfiguration("sendMessage", null, Duration.ofSeconds(1));
      var request =
          new A2AClientRequest(
              new A2AClientRequestData(
                  CONNECTION, new ConnectorModeConfiguration.ToolModeConfiguration(operation)));

      assertThatThrownBy(() -> handler.handle(request))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("The 'message' parameter is required for the 'sendMessage' connectorMode.");
    }

    @Test
    void throwsWhenUnsupportedOperation() {
      var operation =
          new ToolModeOperationConfiguration("unknown", Map.of(), Duration.ofSeconds(1));
      var request =
          new A2AClientRequest(
              new A2AClientRequestData(
                  CONNECTION, new ConnectorModeConfiguration.ToolModeConfiguration(operation)));

      assertThatThrownBy(() -> handler.handle(request))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("Unsupported connectorMode: 'unknown'");
    }
  }
}
