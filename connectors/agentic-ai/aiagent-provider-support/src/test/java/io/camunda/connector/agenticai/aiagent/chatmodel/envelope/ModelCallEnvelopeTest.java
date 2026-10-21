/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.chatmodel.envelope;

import static io.camunda.connector.agenticai.aiagent.api.chatmodel.ChatModelErrorCodes.ERROR_CODE_FAILED_MODEL_CALL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.connector.agenticai.aiagent.api.chatmodel.ChatModel;
import io.camunda.connector.agenticai.aiagent.api.chatmodel.ChatRequest;
import io.camunda.connector.agenticai.aiagent.api.chatmodel.ChatResult;
import io.camunda.connector.agenticai.aiagent.api.chatmodel.ContentFilteredException;
import io.camunda.connector.agenticai.aiagent.api.chatmodel.ContextWindowExceededException;
import io.camunda.connector.agenticai.aiagent.model.AgentMetrics;
import io.camunda.connector.agenticai.aiagent.model.message.AssistantMessage;
import io.camunda.connector.api.error.ConnectorException;
import java.io.EOFException;
import java.net.SocketTimeoutException;
import java.util.Optional;
import java.util.concurrent.CompletionException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ModelCallEnvelopeTest {

  @Mock private ChatModel delegate;
  @Mock private ChatRequest request;

  private SdkFailureMapper failureMapper = failure -> Optional.empty();

  private ModelCallEnvelope envelope() {
    return new ModelCallEnvelope(delegate, failure -> failureMapper.map(failure));
  }

  @Test
  void recordsExecutionTimeInMetricsOfCompletedResult() {
    final var assistantMessage = AssistantMessage.builder().build();
    when(delegate.execute(request))
        .thenReturn(new ChatResult.Completed(assistantMessage, AgentMetrics.builder().build()));

    final var result = envelope().execute(request);

    assertThat(result).isInstanceOf(ChatResult.Completed.class);
    assertThat(result.assistantMessage()).isSameAs(assistantMessage);
    assertThat(result.metrics().executionTime())
        .isNotNull()
        .isGreaterThanOrEqualTo(java.time.Duration.ZERO);
  }

  @Test
  void recordsExecutionTimeInMetricsOfContinuationResult() {
    when(delegate.execute(request))
        .thenReturn(
            new ChatResult.Continuation(
                AssistantMessage.builder().build(), AgentMetrics.builder().modelCalls(1).build()));

    final var result = envelope().execute(request);

    assertThat(result).isInstanceOf(ChatResult.Continuation.class);
    assertThat(result.metrics().modelCalls()).isEqualTo(1);
    assertThat(result.metrics().executionTime())
        .isNotNull()
        .isGreaterThanOrEqualTo(java.time.Duration.ZERO);
  }

  @Test
  void passesOnConnectorExceptionUnchanged() {
    final var failure = new ConnectorException("OAUTH_TOKEN_EXCHANGE_FAILED", "invalid_client");
    when(delegate.execute(request)).thenThrow(failure);

    assertThatThrownBy(() -> envelope().execute(request)).isSameAs(failure);
  }

  @Test
  void passesOnRejectionUnchanged() {
    final var rejection = new ContentFilteredException("blocked", null);
    when(delegate.execute(request)).thenThrow(rejection);

    assertThatThrownBy(() -> envelope().execute(request)).isSameAs(rejection);
  }

  @Test
  void unwrapsFailureOfAsynchronousCall() {
    final var rejection = new ContentFilteredException("blocked", null);
    when(delegate.execute(request)).thenThrow(new CompletionException(rejection));

    assertThatThrownBy(() -> envelope().execute(request)).isSameAs(rejection);
  }

  @Test
  void mapsFailureTheSdkMapperRecognizesToFailedModelCall() {
    failureMapper =
        failure -> Optional.of(new SdkFailure(429, "rate_limit_error", "slow down", false));
    final var cause = new RuntimeException("boom");
    when(delegate.execute(request)).thenThrow(cause);

    assertThatThrownBy(() -> envelope().execute(request))
        .isInstanceOfSatisfying(
            ConnectorException.class,
            e -> {
              assertThat(e.getErrorCode()).isEqualTo(ERROR_CODE_FAILED_MODEL_CALL);
              assertThat(e.getMessage())
                  .isEqualTo("Model call failed with HTTP 429 (rate_limit_error): slow down");
              assertThat(e.getCause()).isSameAs(cause);
            });
  }

  @Test
  void mapsFailureOfAsynchronousCallTheSdkMapperRecognizes() {
    failureMapper = failure -> Optional.of(new SdkFailure(429, "throttled", "slow down", false));
    when(delegate.execute(request)).thenThrow(new CompletionException(new RuntimeException()));

    assertThatThrownBy(() -> envelope().execute(request))
        .isInstanceOf(ConnectorException.class)
        .hasMessage("Model call failed with HTTP 429 (throttled): slow down");
  }

  @Test
  void namesFailureClassWhenSdkFailureHasNoTypeOrMessage() {
    failureMapper = failure -> Optional.of(new SdkFailure(500, null, " ", false));
    when(delegate.execute(request)).thenThrow(new IllegalStateException());

    assertThatThrownBy(() -> envelope().execute(request))
        .hasMessage(
            "Model call failed with HTTP 500 (IllegalStateException): IllegalStateException");
  }

  @Test
  void mapsContextWindowFailureToContextWindowExceeded() {
    failureMapper = failure -> Optional.of(new SdkFailure(400, "code", "too long", true));
    final var cause = new RuntimeException("boom");
    when(delegate.execute(request)).thenThrow(cause);

    assertThatThrownBy(() -> envelope().execute(request))
        .isInstanceOfSatisfying(
            ContextWindowExceededException.class,
            e -> {
              assertThat(e.getMessage()).isEqualTo(ContextWindowExceededException.DEFAULT_MESSAGE);
              assertThat(e.partialResult()).isNull();
              assertThat(e.getCause()).isSameAs(cause);
            });
  }

  @Test
  void mapsOtherFailureToFailedModelCallWithItsMessage() {
    when(delegate.execute(request)).thenThrow(new IllegalStateException("no chunks"));

    assertThatThrownBy(() -> envelope().execute(request))
        .isInstanceOfSatisfying(
            ConnectorException.class,
            e -> {
              assertThat(e.getErrorCode()).isEqualTo(ERROR_CODE_FAILED_MODEL_CALL);
              assertThat(e.getMessage()).isEqualTo("Model call failed: no chunks");
            });
  }

  @Test
  void prefersMessageOfCauseOverGenericMessageOfSdkIoException() {
    when(delegate.execute(request))
        .thenThrow(new RuntimeException("Request failed", new SocketTimeoutException("timed out")));

    assertThatThrownBy(() -> envelope().execute(request))
        .hasMessage("Model call failed: timed out");
  }

  @Test
  void namesClassOfCauseWhenCauseHasNoMessage() {
    when(delegate.execute(request))
        .thenThrow(new RuntimeException("Request failed", new EOFException()));

    assertThatThrownBy(() -> envelope().execute(request))
        .hasMessage("Model call failed: EOFException");
  }

  @Test
  void keepsMessageThatIsNotTheGenericMessageOfSdkIoException() {
    when(delegate.execute(request))
        .thenThrow(new IllegalStateException("Failed to build request", new RuntimeException("x")));

    assertThatThrownBy(() -> envelope().execute(request))
        .hasMessage("Model call failed: Failed to build request");
  }

  @Test
  void namesFailureClassWhenFailureHasNeitherMessageNorCause() {
    when(delegate.execute(request)).thenThrow(new IllegalStateException());

    assertThatThrownBy(() -> envelope().execute(request))
        .hasMessage("Model call failed: IllegalStateException");
  }

  @Test
  void closesDelegate() {
    envelope().close();

    verify(delegate).close();
  }
}
