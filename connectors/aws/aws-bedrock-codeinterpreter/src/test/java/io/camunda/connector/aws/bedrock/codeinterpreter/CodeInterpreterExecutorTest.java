/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.aws.bedrock.codeinterpreter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.connector.api.document.Document;
import io.camunda.connector.api.document.DocumentCreationRequest;
import io.camunda.connector.api.error.ConnectorException;
import io.camunda.connector.aws.bedrock.codeinterpreter.model.request.CodeInterpreterInput;
import io.camunda.connector.aws.bedrock.codeinterpreter.model.request.CodeInterpreterRequest;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.bedrockagentcore.BedrockAgentCoreAsyncClient;
import software.amazon.awssdk.services.bedrockagentcore.BedrockAgentCoreClient;
import software.amazon.awssdk.services.bedrockagentcore.model.BedrockAgentCoreException;
import software.amazon.awssdk.services.bedrockagentcore.model.InvokeCodeInterpreterRequest;
import software.amazon.awssdk.services.bedrockagentcore.model.InvokeCodeInterpreterResponseHandler;
import software.amazon.awssdk.services.bedrockagentcore.model.StartCodeInterpreterSessionRequest;
import software.amazon.awssdk.services.bedrockagentcore.model.StartCodeInterpreterSessionResponse;
import software.amazon.awssdk.services.bedrockagentcore.model.StopCodeInterpreterSessionRequest;
import software.amazon.awssdk.services.bedrockagentcore.model.StopCodeInterpreterSessionResponse;

@ExtendWith(MockitoExtension.class)
class CodeInterpreterExecutorTest extends BaseTest {

  @Mock private BedrockAgentCoreClient syncClient;
  @Mock private BedrockAgentCoreAsyncClient asyncClient;
  @Mock private Function<DocumentCreationRequest, Document> createDocument;

  private CodeInterpreterExecutor executor;

  @BeforeEach
  void setUp() {
    executor = new CodeInterpreterExecutor(syncClient, asyncClient, createDocument);
  }

  @Test
  void shouldStartSessionInvokeAndStop() {
    when(syncClient.startCodeInterpreterSession(any(StartCodeInterpreterSessionRequest.class)))
        .thenReturn(
            StartCodeInterpreterSessionResponse.builder().sessionId("test-session-id").build());
    when(asyncClient.invokeCodeInterpreter(
            any(InvokeCodeInterpreterRequest.class),
            any(InvokeCodeInterpreterResponseHandler.class)))
        .thenReturn(CompletableFuture.completedFuture(null));
    when(syncClient.stopCodeInterpreterSession(any(StopCodeInterpreterSessionRequest.class)))
        .thenReturn(StopCodeInterpreterSessionResponse.builder().build());

    var request = buildRequest(ActualValue.CODE, null);
    var result = executor.execute(request, 12345L);

    assertThat(result).isNotNull();
    assertThat(result.stdout()).isNotNull();
    assertThat(result.files()).isEmpty();

    verify(syncClient).startCodeInterpreterSession(any(StartCodeInterpreterSessionRequest.class));
    verify(asyncClient, atLeast(3))
        .invokeCodeInterpreter(
            any(InvokeCodeInterpreterRequest.class),
            any(InvokeCodeInterpreterResponseHandler.class));
    verify(syncClient).stopCodeInterpreterSession(any(StopCodeInterpreterSessionRequest.class));
  }

  @Test
  void shouldPassCodeInterpreterIdentifierAndCode() {
    when(syncClient.startCodeInterpreterSession(any(StartCodeInterpreterSessionRequest.class)))
        .thenReturn(StartCodeInterpreterSessionResponse.builder().sessionId("sess-123").build());
    when(asyncClient.invokeCodeInterpreter(
            any(InvokeCodeInterpreterRequest.class),
            any(InvokeCodeInterpreterResponseHandler.class)))
        .thenReturn(CompletableFuture.completedFuture(null));
    when(syncClient.stopCodeInterpreterSession(any(StopCodeInterpreterSessionRequest.class)))
        .thenReturn(StopCodeInterpreterSessionResponse.builder().build());

    var request = buildRequest("x = 1 + 2\nprint(x)", 300);
    executor.execute(request, 12345L);

    var startCaptor = ArgumentCaptor.forClass(StartCodeInterpreterSessionRequest.class);
    verify(syncClient).startCodeInterpreterSession(startCaptor.capture());
    assertThat(startCaptor.getValue().codeInterpreterIdentifier())
        .isEqualTo("aws.codeinterpreter.v1");
    assertThat(startCaptor.getValue().sessionTimeoutSeconds()).isEqualTo(300);

    var invokeCaptor = ArgumentCaptor.forClass(InvokeCodeInterpreterRequest.class);
    verify(asyncClient, atLeast(3)).invokeCodeInterpreter(invokeCaptor.capture(), any());
    // First call is listFiles (before execution), second is executeCode, third is listFiles (after)
    var executeCall = invokeCaptor.getAllValues().get(1);
    assertThat(executeCall.sessionId()).isEqualTo("sess-123");
    assertThat(executeCall.arguments().code()).isEqualTo("x = 1 + 2\nprint(x)");
    assertThat(executeCall.arguments().languageAsString()).isEqualTo("python");
  }

  @Test
  void shouldStopSessionEvenOnError() {
    when(syncClient.startCodeInterpreterSession(any(StartCodeInterpreterSessionRequest.class)))
        .thenReturn(StartCodeInterpreterSessionResponse.builder().sessionId("sess-err").build());
    when(asyncClient.invokeCodeInterpreter(
            any(InvokeCodeInterpreterRequest.class),
            any(InvokeCodeInterpreterResponseHandler.class)))
        .thenReturn(CompletableFuture.failedFuture(new RuntimeException("invoke failed")));
    when(syncClient.stopCodeInterpreterSession(any(StopCodeInterpreterSessionRequest.class)))
        .thenReturn(StopCodeInterpreterSessionResponse.builder().build());

    var request = buildRequest("bad code", null);

    assertThatThrownBy(() -> executor.execute(request, 12345L))
        .isInstanceOf(ConnectorException.class)
        .hasMessageContaining("Code Interpreter error");

    verify(syncClient).stopCodeInterpreterSession(any(StopCodeInterpreterSessionRequest.class));
  }

  @Test
  void shouldWrapBedrockExceptionProperly() {
    when(syncClient.startCodeInterpreterSession(any(StartCodeInterpreterSessionRequest.class)))
        .thenThrow(BedrockAgentCoreException.builder().message("Access denied").build());

    var request = buildRequest(ActualValue.CODE, null);

    assertThatThrownBy(() -> executor.execute(request, 12345L))
        .isInstanceOf(ConnectorException.class)
        .hasMessageContaining("Code Interpreter error");
  }

  @Test
  void shouldRejectBlankCode() {
    var request = buildRequest("  ", null);
    assertThatThrownBy(() -> executor.execute(request, 12345L))
        .isInstanceOf(ConnectorException.class)
        .hasMessageContaining("Code must not be empty");
  }

  @Test
  void shouldRejectNullInput() {
    var request = new CodeInterpreterRequest();
    assertThatThrownBy(() -> executor.execute(request, 12345L))
        .isInstanceOf(ConnectorException.class)
        .hasMessageContaining("Code must not be empty");
  }

  private CodeInterpreterRequest buildRequest(String code, Integer timeout) {
    var input = new CodeInterpreterInput();
    input.setCode(code);
    input.setSessionTimeoutSeconds(timeout);
    var request = new CodeInterpreterRequest();
    request.setInput(input);
    return request;
  }
}
