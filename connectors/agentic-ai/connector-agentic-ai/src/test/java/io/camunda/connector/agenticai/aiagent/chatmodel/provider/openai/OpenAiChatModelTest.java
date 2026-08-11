/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.chatmodel.provider.openai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.openai.client.OpenAIClient;
import io.camunda.connector.agenticai.aiagent.agent.AgentErrorCodes;
import io.camunda.connector.agenticai.aiagent.chatmodel.ChatRequest;
import io.camunda.connector.agenticai.aiagent.chatmodel.ChatResult;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.openai.family.OpenAiApiFamilyStrategy;
import io.camunda.connector.agenticai.aiagent.memory.ConversationSnapshot;
import io.camunda.connector.agenticai.aiagent.model.AgentExecutionContext;
import io.camunda.connector.agenticai.aiagent.model.AgentMetrics;
import io.camunda.connector.agenticai.aiagent.model.message.AssistantMessage;
import io.camunda.connector.agenticai.aiagent.model.request.v2.OpenAiChatModelConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.v2.OpenAiChatModelConfiguration.OpenAiApi.OpenAiResponsesApi;
import io.camunda.connector.agenticai.aiagent.model.request.v2.OpenAiChatModelConfiguration.OpenAiApi.OpenAiResponsesApi.ResponsesParameters;
import io.camunda.connector.agenticai.aiagent.model.request.v2.OpenAiChatModelConfiguration.OpenAiBackend.OpenAiApiBackend;
import io.camunda.connector.agenticai.aiagent.model.request.v2.OpenAiChatModelConfiguration.OpenAiBackend.OpenAiApiBackend.OpenAiApiConnection;
import io.camunda.connector.agenticai.aiagent.model.request.v2.OpenAiChatModelConfiguration.OpenAiModel;
import io.camunda.connector.api.error.ConnectorException;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OpenAiChatModelTest {

  @Mock private OpenAIClient client;
  @Mock private OpenAiApiFamilyStrategy strategy;

  private final OpenAiChatModelConfiguration configuration =
      new OpenAiChatModelConfiguration(
          new OpenAiChatModelConfiguration.OpenAiConnection(
              new OpenAiResponsesApi(new ResponsesParameters(null, null, null, null)),
              new OpenAiApiBackend(
                  new OpenAiApiConnection("sk-openai-test", null, null, null, null, null, null)),
              new OpenAiModel("gpt-5.5"),
              null));

  private final AgentExecutionContext executionContext = mock(AgentExecutionContext.class);

  private final ChatRequest request =
      new ChatRequest(executionContext, new ConversationSnapshot(List.of(), List.of()));

  private OpenAiChatModel api;

  @BeforeEach
  void setUp() {
    api = new OpenAiChatModel(client, configuration, strategy);
  }

  @Test
  void delegatesToSelectedFamilyStrategy() {
    final var expected =
        new ChatResult.Completed(
            AssistantMessage.builder().build(), AgentMetrics.builder().build());
    when(strategy.call(client, configuration, request)).thenReturn(expected);

    final var result = api.execute(request);

    assertThat(result).isSameAs(expected);
    verify(strategy).call(client, configuration, request);
    verify(client, never()).close();
  }

  @Test
  void rethrowsConnectorExceptionFromStrategyVerbatim() {
    final var thrown = new ConnectorException("SOME_OTHER_ERROR_CODE", "unsupported content type");
    when(strategy.call(eq(client), eq(configuration), eq(request))).thenThrow(thrown);

    assertThatThrownBy(() -> api.execute(request)).isSameAs(thrown);
  }

  @Test
  void wrapsUnexpectedSdkFailureAsConnectorException() {
    when(strategy.call(eq(client), eq(configuration), eq(request)))
        .thenThrow(new RuntimeException("boom"));

    assertThatThrownBy(() -> api.execute(request))
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
}
