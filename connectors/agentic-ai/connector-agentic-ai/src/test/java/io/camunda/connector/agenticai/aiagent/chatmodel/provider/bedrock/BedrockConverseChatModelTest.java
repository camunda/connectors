/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.chatmodel.provider.bedrock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.agenticai.aiagent.agent.AgentErrorCodes;
import io.camunda.connector.agenticai.aiagent.chatmodel.ChatRequest;
import io.camunda.connector.agenticai.aiagent.chatmodel.ChatResult;
import io.camunda.connector.agenticai.aiagent.memory.ConversationSnapshot;
import io.camunda.connector.agenticai.aiagent.model.AgentConfiguration;
import io.camunda.connector.agenticai.aiagent.model.AgentExecutionContext;
import io.camunda.connector.agenticai.aiagent.model.AgentMetrics;
import io.camunda.connector.agenticai.aiagent.model.message.AssistantMessage;
import io.camunda.connector.agenticai.aiagent.model.request.v2.AwsAuthentication;
import io.camunda.connector.agenticai.aiagent.model.request.v2.BedrockConverseChatModelConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.v2.BedrockConverseChatModelConfiguration.BedrockConverseConnection;
import io.camunda.connector.agenticai.aiagent.model.request.v2.BedrockConverseChatModelConfiguration.BedrockConverseModel;
import io.camunda.connector.api.error.ConnectorException;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.awscore.exception.AwsErrorDetails;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeAsyncClient;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseResponse;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseStreamRequest;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseStreamResponseHandler;

@ExtendWith(MockitoExtension.class)
class BedrockConverseChatModelTest {

  private static final String MODEL_ID = "us.amazon.nova-2-lite-v1:0";

  @Mock private BedrockRuntimeAsyncClient client;
  @Mock private BedrockConverseRequestConverter requestConverter;
  @Mock private BedrockConverseResponseConverter responseConverter;
  @Mock private BedrockConverseStreamAssembler streamAssembler;
  @Mock private ConverseResponse assembledResponse;

  private final ObjectMapper objectMapper = new ObjectMapper();

  private final BedrockConverseChatModelConfiguration configuration =
      new BedrockConverseChatModelConfiguration(
          new BedrockConverseConnection(
              "eu-central-1",
              null,
              new AwsAuthentication.AwsDefaultCredentialsChainAuthentication(),
              null,
              null,
              null,
              null,
              new BedrockConverseModel(MODEL_ID, null)));

  private final AgentExecutionContext executionContext = mock(AgentExecutionContext.class);

  private final ChatRequest request =
      new ChatRequest(executionContext, new ConversationSnapshot(List.of(), List.of()));

  private BedrockConverseChatModel api;

  @BeforeEach
  void setUp() {
    when(executionContext.configuration()).thenReturn(mock(AgentConfiguration.class));
    api =
        new BedrockConverseChatModel(
            client,
            configuration,
            requestConverter,
            responseConverter,
            objectMapper,
            () -> streamAssembler);
  }

  @Test
  void drivesConverseStreamAssemblesAndDelegatesToConverters() {
    final var converseStreamRequest = mock(ConverseStreamRequest.class);
    final var expected =
        new ChatResult.Completed(
            AssistantMessage.builder().build(), AgentMetrics.builder().build());

    when(requestConverter.toConverseStreamRequest(any(), any(), any()))
        .thenReturn(converseStreamRequest);
    when(client.converseStream(eq(converseStreamRequest), any(ConverseStreamResponseHandler.class)))
        .thenReturn(CompletableFuture.completedFuture(null));
    when(streamAssembler.converseResponse()).thenReturn(assembledResponse);
    when(responseConverter.toResult(eq(assembledResponse), any())).thenReturn(expected);

    final var result = api.execute(request);

    assertThat(result).isSameAs(expected);
    verify(requestConverter)
        .toConverseStreamRequest(
            configuration, executionContext.configuration().response(), request.snapshot());
    verify(client)
        .converseStream(eq(converseStreamRequest), any(ConverseStreamResponseHandler.class));
    verify(streamAssembler).converseResponse();
    verify(client, never()).close();
  }

  @Test
  void wrapsSdkFailureAsConnectorException() {
    when(requestConverter.toConverseStreamRequest(any(), any(), any()))
        .thenReturn(mock(ConverseStreamRequest.class));
    when(client.converseStream(
            any(ConverseStreamRequest.class), any(ConverseStreamResponseHandler.class)))
        .thenThrow(new RuntimeException("boom"));

    assertThatThrownBy(() -> api.execute(request))
        .isInstanceOf(ConnectorException.class)
        .extracting(e -> ((ConnectorException) e).getErrorCode())
        .isEqualTo(AgentErrorCodes.ERROR_CODE_FAILED_MODEL_CALL);
  }

  @Test
  void wrapsSynchronouslyThrownAwsServiceExceptionWithStatusCodeAndErrorCode() {
    when(requestConverter.toConverseStreamRequest(any(), any(), any()))
        .thenReturn(mock(ConverseStreamRequest.class));
    when(client.converseStream(
            any(ConverseStreamRequest.class), any(ConverseStreamResponseHandler.class)))
        .thenThrow(awsServiceException());

    assertThatThrownBy(() -> api.execute(request))
        .isInstanceOf(ConnectorException.class)
        .satisfies(
            e -> {
              final var connectorException = (ConnectorException) e;
              assertThat(connectorException.getErrorCode())
                  .isEqualTo(AgentErrorCodes.ERROR_CODE_FAILED_MODEL_CALL);
              assertThat(connectorException.getMessage())
                  .contains("429")
                  .contains("ThrottlingException")
                  .contains("slow down");
            });
  }

  @Test
  void unwrapsCompletionExceptionFromFailedFutureAndWrapsAwsServiceException() {
    when(requestConverter.toConverseStreamRequest(any(), any(), any()))
        .thenReturn(mock(ConverseStreamRequest.class));

    final CompletableFuture<Void> failedFuture = new CompletableFuture<>();
    failedFuture.completeExceptionally(awsServiceException());
    when(client.converseStream(
            any(ConverseStreamRequest.class), any(ConverseStreamResponseHandler.class)))
        .thenReturn(failedFuture);

    assertThatThrownBy(() -> api.execute(request))
        .isInstanceOf(ConnectorException.class)
        .satisfies(
            e -> {
              final var connectorException = (ConnectorException) e;
              assertThat(connectorException.getErrorCode())
                  .isEqualTo(AgentErrorCodes.ERROR_CODE_FAILED_MODEL_CALL);
              assertThat(connectorException.getMessage())
                  .contains("429")
                  .contains("ThrottlingException")
                  .contains("slow down");
            });
  }

  @Test
  void closesUnderlyingClient() {
    api.close();

    verify(client).close();
  }

  @Test
  void closeIsIdempotentAndLogsWarningInsteadOfThrowingWhenClientCloseFails() {
    doThrow(new RuntimeException("boom")).when(client).close();

    api.close();
    api.close();

    verify(client, times(2)).close();
  }

  private static AwsServiceException awsServiceException() {
    return AwsServiceException.builder()
        .statusCode(429)
        .message("slow down")
        .awsErrorDetails(
            AwsErrorDetails.builder()
                .errorCode("ThrottlingException")
                .errorMessage("slow down")
                .serviceName("BedrockRuntime")
                .build())
        .build();
  }
}
