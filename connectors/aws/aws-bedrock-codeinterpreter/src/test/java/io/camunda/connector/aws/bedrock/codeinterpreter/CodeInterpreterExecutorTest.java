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
import io.camunda.connector.aws.bedrock.codeinterpreter.model.request.Language;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.bedrockagentcore.BedrockAgentCoreAsyncClient;
import software.amazon.awssdk.services.bedrockagentcore.BedrockAgentCoreClient;
import software.amazon.awssdk.services.bedrockagentcore.model.BedrockAgentCoreException;
import software.amazon.awssdk.services.bedrockagentcore.model.ContentBlock;
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

  @Test
  void shouldUseConfiguredLanguage() {
    mockSession("sess-lang");
    mockInvoke();
    mockStopSession();

    var input = new CodeInterpreterInput();
    input.setCode("console.log('hello')");
    input.setLanguage(Language.JAVASCRIPT);
    var request = new CodeInterpreterRequest();
    request.setInput(input);

    executor.execute(request, 12345L);

    var invokeCaptor = ArgumentCaptor.forClass(InvokeCodeInterpreterRequest.class);
    verify(asyncClient, atLeast(3)).invokeCodeInterpreter(invokeCaptor.capture(), any());
    var executeCall = invokeCaptor.getAllValues().get(1);
    assertThat(executeCall.arguments().languageAsString()).isEqualTo("javascript");
  }

  @Test
  void shouldUseConfiguredCodeInterpreterIdentifier() {
    var input = new CodeInterpreterInput();
    input.setCode(ActualValue.CODE);
    input.setLanguage(Language.PYTHON);
    input.setCodeInterpreterIdentifier("custom.codeinterpreter.v2");
    var request = new CodeInterpreterRequest();
    request.setInput(input);

    when(syncClient.startCodeInterpreterSession(any(StartCodeInterpreterSessionRequest.class)))
        .thenReturn(StartCodeInterpreterSessionResponse.builder().sessionId("sess-custom").build());
    mockInvoke();
    mockStopSession();

    executor.execute(request, 12345L);

    var startCaptor = ArgumentCaptor.forClass(StartCodeInterpreterSessionRequest.class);
    verify(syncClient).startCodeInterpreterSession(startCaptor.capture());
    assertThat(startCaptor.getValue().codeInterpreterIdentifier())
        .isEqualTo("custom.codeinterpreter.v2");
  }

  @Test
  void shouldUseSessionTimeoutFromDuration() {
    mockSession("sess-timeout");
    mockInvoke();
    mockStopSession();

    var input = new CodeInterpreterInput();
    input.setCode(ActualValue.CODE);
    input.setLanguage(Language.PYTHON);
    input.setSessionTimeout(java.time.Duration.ofMinutes(10));
    var request = new CodeInterpreterRequest();
    request.setInput(input);

    executor.execute(request, 12345L);

    var startCaptor = ArgumentCaptor.forClass(StartCodeInterpreterSessionRequest.class);
    verify(syncClient).startCodeInterpreterSession(startCaptor.capture());
    assertThat(startCaptor.getValue().sessionTimeoutSeconds()).isEqualTo(600);
  }

  @Test
  void shouldUseElementInstanceKeyInSessionName() {
    mockSession("sess-key");
    mockInvoke();
    mockStopSession();

    var request = buildRequest(ActualValue.CODE, null);
    executor.execute(request, 9876543L);

    var startCaptor = ArgumentCaptor.forClass(StartCodeInterpreterSessionRequest.class);
    verify(syncClient).startCodeInterpreterSession(startCaptor.capture());
    assertThat(startCaptor.getValue().name()).isEqualTo("camunda-9876543");
  }

  // --- Tests for getBlockSize ---

  @Test
  void shouldGetBlockSizeFromSizeField() {
    var block = ContentBlock.builder().size(1024L).build();
    assertThat(executor.getBlockSize(block)).isEqualTo(1024L);
  }

  @Test
  void shouldGetBlockSizeFromDataWhenSizeNull() {
    var data = "test content".getBytes();
    var block = ContentBlock.builder().data(SdkBytes.fromByteArray(data)).build();
    assertThat(executor.getBlockSize(block)).isEqualTo(data.length);
  }

  @Test
  void shouldReturnZeroForEmptyBlock() {
    var block = ContentBlock.builder().build();
    assertThat(executor.getBlockSize(block)).isEqualTo(0L);
  }

  // --- Tests for convertToDocuments ---

  @Test
  void shouldConvertContentBlockToDocument() {
    var mockDoc = org.mockito.Mockito.mock(Document.class);
    when(createDocument.apply(any(DocumentCreationRequest.class))).thenReturn(mockDoc);

    var data = "file content".getBytes();
    var block =
        ContentBlock.builder().data(SdkBytes.fromByteArray(data)).mimeType("text/plain").build();

    var docs = executor.convertToDocuments(List.of(block), "output.txt");

    assertThat(docs).hasSize(1).containsExactly(mockDoc);

    var captor = ArgumentCaptor.forClass(DocumentCreationRequest.class);
    verify(createDocument).apply(captor.capture());
    assertThat(captor.getValue().fileName()).isEqualTo("output.txt");
    assertThat(captor.getValue().contentType()).isEqualTo("text/plain");
  }

  @Test
  void shouldSkipBlocksWithNoData() {
    var block = ContentBlock.builder().mimeType("text/plain").build();

    var docs = executor.convertToDocuments(List.of(block), "empty.txt");

    assertThat(docs).isEmpty();
  }

  @Test
  void shouldUseDefaultMimeTypeWhenNotProvided() {
    var mockDoc = org.mockito.Mockito.mock(Document.class);
    when(createDocument.apply(any(DocumentCreationRequest.class))).thenReturn(mockDoc);

    var block = ContentBlock.builder().data(SdkBytes.fromByteArray("data".getBytes())).build();

    executor.convertToDocuments(List.of(block), "file.bin");

    var captor = ArgumentCaptor.forClass(DocumentCreationRequest.class);
    verify(createDocument).apply(captor.capture());
    assertThat(captor.getValue().contentType()).isEqualTo("application/octet-stream");
  }

  private void mockSession(String sessionId) {
    when(syncClient.startCodeInterpreterSession(any(StartCodeInterpreterSessionRequest.class)))
        .thenReturn(StartCodeInterpreterSessionResponse.builder().sessionId(sessionId).build());
  }

  private void mockInvoke() {
    when(asyncClient.invokeCodeInterpreter(
            any(InvokeCodeInterpreterRequest.class),
            any(InvokeCodeInterpreterResponseHandler.class)))
        .thenReturn(CompletableFuture.completedFuture(null));
  }

  private void mockStopSession() {
    when(syncClient.stopCodeInterpreterSession(any(StopCodeInterpreterSessionRequest.class)))
        .thenReturn(StopCodeInterpreterSessionResponse.builder().build());
  }

  private CodeInterpreterRequest buildRequest(String code, Integer timeoutSeconds) {
    var input = new CodeInterpreterInput();
    input.setCode(code);
    input.setLanguage(Language.PYTHON);
    if (timeoutSeconds != null) {
      input.setSessionTimeout(java.time.Duration.ofSeconds(timeoutSeconds));
    }
    var request = new CodeInterpreterRequest();
    request.setInput(input);
    return request;
  }
}
