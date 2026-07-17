/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.framework.openai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.openai.client.OpenAIClient;
import io.camunda.connector.agenticai.aiagent.agent.AgentErrorCodes;
import io.camunda.connector.agenticai.aiagent.framework.api.ChatModelRequest;
import io.camunda.connector.agenticai.aiagent.framework.api.ChatModelResult;
import io.camunda.connector.agenticai.aiagent.framework.capabilities.CoreModelCapabilities;
import io.camunda.connector.agenticai.aiagent.framework.capabilities.ModelCapabilities.Modality;
import io.camunda.connector.agenticai.aiagent.framework.openai.family.OpenAiApiFamilyStrategy;
import io.camunda.connector.agenticai.aiagent.memory.ConversationSnapshot;
import io.camunda.connector.agenticai.aiagent.model.AgentExecutionContext;
import io.camunda.connector.agenticai.aiagent.model.AgentMetrics;
import io.camunda.connector.agenticai.aiagent.model.message.AssistantMessage;
import io.camunda.connector.api.error.ConnectorException;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OpenAiChatModelApiTest {

  @Mock private OpenAIClient client;
  @Mock private OpenAiApiFamilyStrategy strategy;

  private final OpenAiModelCapabilities capabilities = openAiCaps();
  private final ChatModelRequest request =
      new ChatModelRequest(
          mock(AgentExecutionContext.class), new ConversationSnapshot(List.of(), List.of()));

  private OpenAiChatModelApi api;

  @BeforeEach
  void setUp() {
    api = new OpenAiChatModelApi(client, strategy, capabilities, true);
  }

  @Test
  void delegatesToStrategy() {
    final var expected =
        new ChatModelResult.Completed(
            AssistantMessage.builder().build(), AgentMetrics.builder().build());

    when(strategy.call(client, request, capabilities, true)).thenReturn(expected);

    final var result = api.call(request);

    assertThat(result).isSameAs(expected);
    verify(strategy).call(client, request, capabilities, true);
  }

  @Test
  void threadsModelMatchedSignalFromConstructorIntoStrategy() {
    final var unmatchedApi = new OpenAiChatModelApi(client, strategy, capabilities, false);
    final var expected =
        new ChatModelResult.Completed(
            AssistantMessage.builder().build(), AgentMetrics.builder().build());

    when(strategy.call(client, request, capabilities, false)).thenReturn(expected);

    unmatchedApi.call(request);

    verify(strategy).call(client, request, capabilities, false);
  }

  @Test
  void propagatesConnectorExceptionFromStrategyUnwrapped() {
    final var connectorException = new ConnectorException("SOME_OTHER_CODE", "validation failed");

    when(strategy.call(any(), any(), any(), eq(true))).thenThrow(connectorException);

    assertThatThrownBy(() -> api.call(request)).isSameAs(connectorException);
  }

  @Test
  void wrapsGenericFailureAsConnectorException() {
    when(strategy.call(any(), any(), any(), eq(true))).thenThrow(new RuntimeException("boom"));

    assertThatThrownBy(() -> api.call(request))
        .isInstanceOf(ConnectorException.class)
        .extracting(e -> ((ConnectorException) e).getErrorCode())
        .isEqualTo(AgentErrorCodes.ERROR_CODE_FAILED_MODEL_CALL);
  }

  @Test
  void closesUnderlyingClient() {
    api.close();

    verify(client).close();
  }

  @Test
  void closeLogsWarningInsteadOfThrowingWhenClientCloseFails() {
    doThrow(new RuntimeException("boom")).when(client).close();

    api.close();

    verify(client).close();
  }

  @Test
  void capabilitiesReturnsInjectedValue() {
    assertThat(api.capabilities()).isSameAs(capabilities);
  }

  private static OpenAiModelCapabilities openAiCaps() {
    return new OpenAiModelCapabilities(
        new CoreModelCapabilities(
            List.of(Modality.TEXT), List.of(Modality.TEXT), List.of(Modality.TEXT), null, null),
        null);
  }
}
