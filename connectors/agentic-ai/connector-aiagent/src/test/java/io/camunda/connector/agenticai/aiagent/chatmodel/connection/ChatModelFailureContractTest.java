/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.chatmodel.connection;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.any;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static io.camunda.connector.agenticai.aiagent.AgentConfigurationFixture.agentConfiguration;
import static io.camunda.connector.agenticai.aiagent.TestMessagesFixture.userMessage;
import static io.camunda.connector.agenticai.aiagent.api.chatmodel.ChatModelErrorCodes.ERROR_CODE_FAILED_MODEL_CALL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import io.camunda.connector.agenticai.aiagent.api.chatmodel.ChatRequest;
import io.camunda.connector.agenticai.aiagent.api.chatmodel.ContextWindowExceededException;
import io.camunda.connector.agenticai.aiagent.api.conversation.ConversationSnapshot;
import io.camunda.connector.agenticai.aiagent.chatmodel.connection.ChatModelConnectionContractTest.Provider;
import io.camunda.connector.agenticai.aiagent.model.AgentExecutionContext;
import io.camunda.connector.api.error.ConnectorException;
import io.camunda.connector.http.client.proxy.ProxyConfiguration;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * A failure of a model call is reported the same way for every native provider, whatever shape of
 * error the API of the provider responds with. Each provider is driven through its own factory
 * against a stub endpoint which answers with an error in the shape of the provider.
 */
@WireMockTest
class ChatModelFailureContractTest {

  private static final Duration TIMEOUT = Duration.ofSeconds(5);

  @ParameterizedTest
  @EnumSource(Provider.class)
  void reportsTheErrorResponseOfTheProvider(Provider provider, WireMockRuntimeInfo wireMock) {
    stubFor(
        any(anyUrl())
            .willReturn(
                aResponse()
                    .withStatus(400)
                    .withHeaders(provider.errorHeaders())
                    .withBody(provider.errorBody("bad input"))));

    try (var model = provider.create(ProxyConfiguration.NONE, wireMock.getHttpBaseUrl(), TIMEOUT)) {
      assertThatThrownBy(() -> model.execute(request()))
          .isInstanceOfSatisfying(
              ConnectorException.class,
              e -> {
                assertThat(e.getErrorCode()).isEqualTo(ERROR_CODE_FAILED_MODEL_CALL);
                assertThat(e.getMessage())
                    .isEqualTo(
                        "Model call failed with HTTP 400 ("
                            + provider.errorType()
                            + "): bad input");
              });
    }
  }

  @ParameterizedTest
  @EnumSource(
      value = Provider.class,
      names = {"OPENAI", "MISTRAL", "GEMINI"})
  void reportsAnOverLengthRequestAsExceededContextWindow(
      Provider provider, WireMockRuntimeInfo wireMock) {
    stubFor(
        any(anyUrl())
            .willReturn(
                aResponse()
                    .withStatus(400)
                    .withHeaders(provider.errorHeaders())
                    .withBody(provider.contextWindowErrorBody())));

    try (var model = provider.create(ProxyConfiguration.NONE, wireMock.getHttpBaseUrl(), TIMEOUT)) {
      assertThatThrownBy(() -> model.execute(request()))
          .isInstanceOfSatisfying(
              ContextWindowExceededException.class, e -> assertThat(e.partialResult()).isNull());
    }
  }

  private ChatRequest request() {
    final var executionContext = mock(AgentExecutionContext.class);
    when(executionContext.configuration()).thenReturn(agentConfiguration().build());
    return new ChatRequest(
        executionContext, new ConversationSnapshot(List.of(userMessage("Hello")), List.of()));
  }
}
