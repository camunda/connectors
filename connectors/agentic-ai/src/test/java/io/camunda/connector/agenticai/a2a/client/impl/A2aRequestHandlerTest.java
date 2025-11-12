/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.a2a.client.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.a2a.spec.AgentCard;
import io.camunda.connector.agenticai.a2a.client.api.A2aMessageSender;
import io.camunda.connector.agenticai.a2a.client.model.A2aCommonSendMessageConfiguration;
import io.camunda.connector.agenticai.a2a.client.model.A2aConnectorModeConfiguration;
import io.camunda.connector.agenticai.a2a.client.model.A2aRequest;
import io.camunda.connector.agenticai.a2a.client.model.A2aRequest.A2aRequestData;
import io.camunda.connector.agenticai.a2a.client.model.A2aSendMessageOperationParametersBuilder;
import io.camunda.connector.agenticai.a2a.client.model.A2aStandaloneOperationConfiguration.FetchAgentCardOperationConfiguration;
import io.camunda.connector.agenticai.a2a.client.model.A2aStandaloneOperationConfiguration.SendMessageOperationConfiguration;
import io.camunda.connector.agenticai.a2a.client.model.A2aToolOperationConfiguration;
import io.camunda.connector.agenticai.a2a.common.api.A2aAgentCardFetcher;
import io.camunda.connector.agenticai.a2a.common.model.A2aConnectionConfiguration;
import io.camunda.connector.agenticai.a2a.common.model.result.A2aAgentCard;
import io.camunda.connector.agenticai.a2a.common.model.result.A2aArtifact;
import io.camunda.connector.agenticai.a2a.common.model.result.A2aMessage;
import io.camunda.connector.agenticai.a2a.common.model.result.A2aTask;
import io.camunda.connector.agenticai.a2a.common.model.result.A2aTaskStatus;
import io.camunda.connector.agenticai.model.message.content.TextContent;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EmptySource;
import org.junit.jupiter.params.provider.NullSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class A2aRequestHandlerTest {

  @Mock private A2aAgentCardFetcher agentCardFetcher;
  @Mock private A2aMessageSender messageSender;
  private A2aRequestHandlerImpl handler;

  private static final A2aConnectionConfiguration CONNECTION =
      new A2aConnectionConfiguration("https://a2a.example.com", null);

  @BeforeEach
  void setUp() {
    handler = new A2aRequestHandlerImpl(agentCardFetcher, messageSender, new ObjectMapper());
  }

  @Nested
  class StandaloneModeTests {
    @Test
    void handleFetchAgentCard() {
      var operation = new FetchAgentCardOperationConfiguration();
      var request =
          new A2aRequest(
              new A2aRequestData(
                  CONNECTION,
                  new A2aConnectorModeConfiguration.StandaloneModeConfiguration(operation)));

      var expectedResult =
          new A2aAgentCard(UUID.randomUUID().toString(), "name", "desc", List.of());
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
              A2aSendMessageOperationParametersBuilder.builder().text("Hello").build(),
              sendMessageConfiguration(1, 1));
      var request =
          new A2aRequest(
              new A2aRequestData(
                  CONNECTION,
                  new A2aConnectorModeConfiguration.StandaloneModeConfiguration(operation)));

      when(agentCardFetcher.fetchAgentCardRaw(CONNECTION)).thenReturn(agentCard);
      var expectedSendResult =
          A2aTask.builder()
              .id("task-1")
              .contextId("context-1")
              .status(A2aTaskStatus.builder().state(A2aTaskStatus.TaskState.COMPLETED).build())
              .artifacts(
                  List.of(
                      A2aArtifact.builder()
                          .artifactId("artifact-1")
                          .name("artifact1")
                          .contents(List.of(TextContent.textContent("ok")))
                          .build()))
              .build();
      when(messageSender.sendMessage(agentCard, operation)).thenReturn(expectedSendResult);

      var result = handler.handle(request);

      assertThat(result).isSameAs(expectedSendResult);
      verify(agentCardFetcher).fetchAgentCardRaw(CONNECTION);
      verify(messageSender).sendMessage(agentCard, operation);
      verify(agentCardFetcher, never()).fetchAgentCard(CONNECTION);
    }
  }

  @Nested
  class ToolModeTests {
    @Test
    void handleFetchAgentCard() {
      var operation =
          new A2aToolOperationConfiguration(
              "fetchAgentCard", null, sendMessageConfiguration(1, 10));
      var request =
          new A2aRequest(
              new A2aRequestData(
                  CONNECTION, new A2aConnectorModeConfiguration.ToolModeConfiguration(operation)));

      var expectedResult =
          new A2aAgentCard(UUID.randomUUID().toString(), "name", "desc", List.of());
      when(agentCardFetcher.fetchAgentCard(CONNECTION)).thenReturn(expectedResult);

      var result = handler.handle(request);

      assertThat(result).isSameAs(expectedResult);
      verify(agentCardFetcher).fetchAgentCard(CONNECTION);
      verify(agentCardFetcher, never()).fetchAgentCardRaw(any());
      verify(messageSender, never()).sendMessage(any(), any());
    }

    @Test
    void handleSendMessage() {
      var params = Map.<String, Object>of("text", "Hello, agent!");
      var commonConfiguration = sendMessageConfiguration(10, 45);
      var operation = new A2aToolOperationConfiguration("sendMessage", params, commonConfiguration);
      var request =
          new A2aRequest(
              new A2aRequestData(
                  CONNECTION, new A2aConnectorModeConfiguration.ToolModeConfiguration(operation)));

      var agentCard = mock(AgentCard.class);
      when(agentCardFetcher.fetchAgentCardRaw(CONNECTION)).thenReturn(agentCard);

      var expectedSendResult =
          A2aMessage.builder()
              .role(A2aMessage.Role.AGENT)
              .messageId("message-1")
              .contextId("context-1")
              .contents(List.of(TextContent.textContent("ok")))
              .build();
      when(messageSender.sendMessage(eq(agentCard), any())).thenReturn(expectedSendResult);

      var result = handler.handle(request);

      assertThat(result).isSameAs(expectedSendResult);

      ArgumentCaptor<SendMessageOperationConfiguration> opCaptor =
          ArgumentCaptor.forClass(SendMessageOperationConfiguration.class);
      verify(messageSender).sendMessage(eq(agentCard), opCaptor.capture());

      var convertedOperation = opCaptor.getValue();
      assertThat(convertedOperation.params().text()).isEqualTo("Hello, agent!");
      assertThat(convertedOperation.params().contextId()).isNull();
      assertThat(convertedOperation.params().taskId()).isNull();
      assertThat(convertedOperation.params().referenceTaskIds()).isEmpty();
      assertThat(convertedOperation.params().documents()).isEmpty();
      assertThat(convertedOperation.settings()).isEqualTo(commonConfiguration);

      verify(agentCardFetcher).fetchAgentCardRaw(CONNECTION);
      verify(agentCardFetcher, never()).fetchAgentCard(CONNECTION);
    }

    @Test
    void handleSendMessageWithAllOptionalFields() {
      var params =
          Map.of(
              "text",
              "Follow-up message",
              "contextId",
              "ctx-123",
              "taskId",
              "task-456",
              "referenceTaskIds",
              List.of("ref-1", "ref-2"));
      var operation =
          new A2aToolOperationConfiguration(
              "sendMessage", params, sendMessageConfiguration(10, 30));
      var request =
          new A2aRequest(
              new A2aRequestData(
                  CONNECTION, new A2aConnectorModeConfiguration.ToolModeConfiguration(operation)));

      var agentCard = mock(AgentCard.class);
      when(agentCardFetcher.fetchAgentCardRaw(CONNECTION)).thenReturn(agentCard);

      var expectedSendResult =
          A2aMessage.builder()
              .role(A2aMessage.Role.AGENT)
              .messageId("message-2")
              .contextId("ctx-123")
              .contents(List.of(TextContent.textContent("response")))
              .build();
      when(messageSender.sendMessage(eq(agentCard), any())).thenReturn(expectedSendResult);

      var result = handler.handle(request);

      assertThat(result).isSameAs(expectedSendResult);

      ArgumentCaptor<SendMessageOperationConfiguration> opCaptor =
          ArgumentCaptor.forClass(SendMessageOperationConfiguration.class);
      verify(messageSender).sendMessage(eq(agentCard), opCaptor.capture());

      var convertedOperation = opCaptor.getValue();
      assertThat(convertedOperation.params().text()).isEqualTo("Follow-up message");
      assertThat(convertedOperation.params().contextId()).isEqualTo("ctx-123");
      assertThat(convertedOperation.params().taskId()).isEqualTo("task-456");
      assertThat(convertedOperation.params().referenceTaskIds()).containsExactly("ref-1", "ref-2");
    }

    @ParameterizedTest
    @NullSource
    @EmptySource
    void throwsWhenMessageParamsNullOrEmpty(Map<String, Object> params) {
      var operation =
          new A2aToolOperationConfiguration("sendMessage", params, sendMessageConfiguration(1, 1));
      var request =
          new A2aRequest(
              new A2aRequestData(
                  CONNECTION, new A2aConnectorModeConfiguration.ToolModeConfiguration(operation)));

      assertThatThrownBy(() -> handler.handle(request))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("'params' cannot be null or empty for operation: 'sendMessage'");
    }

    @Test
    void throwsWhenUnsupportedOperation() {
      var operation =
          new A2aToolOperationConfiguration("unknown", Map.of(), sendMessageConfiguration(1, 1));
      var request =
          new A2aRequest(
              new A2aRequestData(
                  CONNECTION, new A2aConnectorModeConfiguration.ToolModeConfiguration(operation)));

      assertThatThrownBy(() -> handler.handle(request))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("Unsupported operation: 'unknown'");
    }
  }

  private static A2aCommonSendMessageConfiguration sendMessageConfiguration(
      int historyLength, int durationSeconds) {
    return new A2aCommonSendMessageConfiguration(
        historyLength, false, Duration.ofSeconds(durationSeconds));
  }
}
