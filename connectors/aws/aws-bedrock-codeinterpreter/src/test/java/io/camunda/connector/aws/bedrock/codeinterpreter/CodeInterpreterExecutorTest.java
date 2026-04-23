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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.connector.api.document.Document;
import io.camunda.connector.api.document.DocumentCreationRequest;
import io.camunda.connector.api.error.ConnectorException;
import io.camunda.connector.aws.bedrock.codeinterpreter.model.request.CodeInterpreterInput;
import io.camunda.connector.aws.bedrock.codeinterpreter.model.request.CodeInterpreterRequest;
import io.camunda.connector.aws.bedrock.codeinterpreter.model.request.Language;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.core.async.SdkPublisher;
import software.amazon.awssdk.services.bedrockagentcore.BedrockAgentCoreAsyncClient;
import software.amazon.awssdk.services.bedrockagentcore.BedrockAgentCoreClient;
import software.amazon.awssdk.services.bedrockagentcore.model.BedrockAgentCoreException;
import software.amazon.awssdk.services.bedrockagentcore.model.CodeInterpreterResult;
import software.amazon.awssdk.services.bedrockagentcore.model.CodeInterpreterStreamOutput;
import software.amazon.awssdk.services.bedrockagentcore.model.ContentBlock;
import software.amazon.awssdk.services.bedrockagentcore.model.InvokeCodeInterpreterRequest;
import software.amazon.awssdk.services.bedrockagentcore.model.InvokeCodeInterpreterResponseHandler;
import software.amazon.awssdk.services.bedrockagentcore.model.ResourceContent;
import software.amazon.awssdk.services.bedrockagentcore.model.StartCodeInterpreterSessionRequest;
import software.amazon.awssdk.services.bedrockagentcore.model.StartCodeInterpreterSessionResponse;
import software.amazon.awssdk.services.bedrockagentcore.model.StopCodeInterpreterSessionRequest;
import software.amazon.awssdk.services.bedrockagentcore.model.StopCodeInterpreterSessionResponse;
import software.amazon.awssdk.services.bedrockagentcore.model.ToolName;
import software.amazon.awssdk.services.bedrockagentcore.model.ToolResultStructuredContent;

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
    input.setSessionTimeout(Duration.ofMinutes(10));
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
      input.setSessionTimeout(Duration.ofSeconds(timeoutSeconds));
    }
    var request = new CodeInterpreterRequest();
    request.setInput(input);
    return request;
  }

  // --- Streaming / file handling tests ---

  @Test
  void shouldAccumulateStdoutStderrExitCodeAndExecutionTime() {
    mockSession("sess-stream");
    mockStopSession();
    new StreamStub()
        .withExecEvents(
            resultWithStructured("hello ", null, null, null),
            resultWithStructured("world", "oops", 7, 1.25))
        .install();

    var response = executor.execute(buildRequest(ActualValue.CODE, null), 1L);

    assertThat(response.stdout()).isEqualTo("hello world");
    assertThat(response.stderr()).isEqualTo("oops");
    assertThat(response.exitCode()).isEqualTo(7);
    assertThat(response.executionTimeMs()).isEqualTo(1.25);
    assertThat(response.files()).isEmpty();
  }

  @Test
  void shouldReturnGeneratedFilesAsDocuments() {
    mockSession("sess-files");
    mockStopSession();
    var mockDoc = mock(Document.class);
    when(createDocument.apply(any(DocumentCreationRequest.class))).thenReturn(mockDoc);

    new StreamStub()
        .withListResponses(List.of(), List.of("empty.txt", "out.txt"))
        // file whose single block has no extractable data -> skipped silently
        .withReadFile("empty.txt", ContentBlock.builder().mimeType("text/plain").build())
        .withReadFile(
            "out.txt",
            ContentBlock.builder()
                .data(SdkBytes.fromUtf8String("hello"))
                .mimeType("text/plain")
                .build())
        .install();

    var response = executor.execute(buildRequest(ActualValue.CODE, null), 1L);

    assertThat(response.files()).hasSize(1).containsExactly(mockDoc);
    var captor = ArgumentCaptor.forClass(DocumentCreationRequest.class);
    verify(createDocument).apply(captor.capture());
    assertThat(captor.getValue().fileName()).isEqualTo("out.txt");
    assertThat(captor.getValue().contentType()).isEqualTo("text/plain");
  }

  @Test
  void shouldDiffNewFilesAgainstPreExistingAndSkipHiddenFiles() {
    mockSession("sess-diff");
    mockStopSession();
    when(createDocument.apply(any(DocumentCreationRequest.class))).thenReturn(mock(Document.class));

    new StreamStub()
        .withListResponses(
            List.of("preexisting.txt"), List.of("preexisting.txt", ".hidden", "new.txt"))
        .withReadFile(
            "new.txt", ContentBlock.builder().data(SdkBytes.fromUtf8String("fresh")).build())
        .install();

    executor.execute(buildRequest(ActualValue.CODE, null), 1L);

    var captor = ArgumentCaptor.forClass(DocumentCreationRequest.class);
    verify(createDocument).apply(captor.capture());
    assertThat(captor.getValue().fileName()).isEqualTo("new.txt");
    // block had no explicit mimeType -> default octet-stream applied
    assertThat(captor.getValue().contentType()).isEqualTo("application/octet-stream");

    // ensure neither the pre-existing file nor the hidden file was ever read
    var invokeCaptor = ArgumentCaptor.forClass(InvokeCodeInterpreterRequest.class);
    verify(asyncClient, atLeast(1)).invokeCodeInterpreter(invokeCaptor.capture(), any());
    assertThat(invokeCaptor.getAllValues())
        .filteredOn(r -> r.name() == ToolName.READ_FILES)
        .extracting(r -> r.arguments().paths().get(0))
        .containsExactly("new.txt");
  }

  @Test
  void shouldCapAtMaxFiles() {
    mockSession("sess-cap");
    mockStopSession();
    when(createDocument.apply(any(DocumentCreationRequest.class))).thenReturn(mock(Document.class));

    var files = List.of("a.txt", "b.txt", "c.txt", "d.txt", "e.txt");
    var stub = new StreamStub().withListResponses(List.of(), files);
    for (var f : files) {
      stub.withReadFile(f, ContentBlock.builder().data(SdkBytes.fromUtf8String("x")).build());
    }
    stub.install();

    var input = new CodeInterpreterInput();
    input.setCode(ActualValue.CODE);
    input.setLanguage(Language.PYTHON);
    input.setMaxFiles(2);
    var request = new CodeInterpreterRequest();
    request.setInput(input);

    executor.execute(request, 1L);

    var invokeCaptor = ArgumentCaptor.forClass(InvokeCodeInterpreterRequest.class);
    verify(asyncClient, atLeast(1)).invokeCodeInterpreter(invokeCaptor.capture(), any());
    assertThat(invokeCaptor.getAllValues())
        .filteredOn(r -> r.name() == ToolName.READ_FILES)
        .extracting(r -> r.arguments().paths().get(0))
        .containsExactly("a.txt", "b.txt");
  }

  @Test
  void shouldStopRetrievalWhenMaxTotalFileSizeExceeded() {
    mockSession("sess-size");
    mockStopSession();
    var mockDoc = mock(Document.class);
    when(createDocument.apply(any(DocumentCreationRequest.class))).thenReturn(mockDoc);

    // first file fits, second file pushes over the limit
    new StreamStub()
        .withListResponses(List.of(), List.of("first.bin", "second.bin"))
        .withReadFile(
            "first.bin",
            ContentBlock.builder().size(400L).data(SdkBytes.fromUtf8String("aaaa")).build())
        .withReadFile(
            "second.bin",
            ContentBlock.builder().size(700L).data(SdkBytes.fromUtf8String("bbbb")).build())
        .install();

    var input = new CodeInterpreterInput();
    input.setCode(ActualValue.CODE);
    input.setLanguage(Language.PYTHON);
    input.setMaxTotalFileSize(1000L);
    var request = new CodeInterpreterRequest();
    request.setInput(input);

    var response = executor.execute(request, 1L);

    // first.bin fits under the 1000 byte budget (400),
    // second.bin (700) would push totals to 1100 > 1000 and retrieval stops.
    assertThat(response.files()).hasSize(1);
    var captor = ArgumentCaptor.forClass(DocumentCreationRequest.class);
    verify(createDocument).apply(captor.capture());
    assertThat(captor.getValue().fileName()).isEqualTo("first.bin");
  }

  @Test
  void shouldContinueRetrievalWhenReadingOneFileFails() {
    mockSession("sess-readfail");
    mockStopSession();
    var mockDoc = mock(Document.class);
    when(createDocument.apply(any(DocumentCreationRequest.class))).thenReturn(mockDoc);

    new StreamStub()
        .withListResponses(List.of(), List.of("broken.txt", "ok.txt"))
        .withReadFileFailure("broken.txt", new RuntimeException("boom"))
        .withReadFile("ok.txt", ContentBlock.builder().data(SdkBytes.fromUtf8String("ok")).build())
        .install();

    var response = executor.execute(buildRequest(ActualValue.CODE, null), 1L);

    assertThat(response.files()).hasSize(1);
    var captor = ArgumentCaptor.forClass(DocumentCreationRequest.class);
    verify(createDocument).apply(captor.capture());
    assertThat(captor.getValue().fileName()).isEqualTo("ok.txt");
  }

  @Test
  void shouldExtractDataAndMimeTypeFromResourceBlob() {
    mockSession("sess-blob");
    mockStopSession();
    var mockDoc = mock(Document.class);
    when(createDocument.apply(any(DocumentCreationRequest.class))).thenReturn(mockDoc);

    var blob = "blob-bytes".getBytes();
    new StreamStub()
        .withListResponses(List.of(), List.of("image.png"))
        .withReadFile(
            "image.png",
            ContentBlock.builder()
                .resource(
                    ResourceContent.builder()
                        .blob(SdkBytes.fromByteArray(blob))
                        .mimeType("image/png")
                        .build())
                .build())
        .install();

    executor.execute(buildRequest(ActualValue.CODE, null), 1L);

    var captor = ArgumentCaptor.forClass(DocumentCreationRequest.class);
    verify(createDocument).apply(captor.capture());
    assertThat(captor.getValue().fileName()).isEqualTo("image.png");
    assertThat(captor.getValue().contentType()).isEqualTo("image/png");
  }

  @Test
  void shouldExtractDataFromResourceTextWithDefaultMimeType() {
    mockSession("sess-text");
    mockStopSession();
    var mockDoc = mock(Document.class);
    when(createDocument.apply(any(DocumentCreationRequest.class))).thenReturn(mockDoc);

    new StreamStub()
        .withListResponses(List.of(), List.of("notes.txt"))
        .withReadFile(
            "notes.txt",
            ContentBlock.builder()
                .resource(ResourceContent.builder().text("hello from text").build())
                .build())
        .install();

    executor.execute(buildRequest(ActualValue.CODE, null), 1L);

    var captor = ArgumentCaptor.forClass(DocumentCreationRequest.class);
    verify(createDocument).apply(captor.capture());
    // resource().text() without an explicit mimeType falls back to text/plain
    assertThat(captor.getValue().contentType()).isEqualTo("text/plain");
  }

  @Test
  void shouldResolveListedFileNamesFromBlockNameOrText() {
    mockSession("sess-listnames");
    mockStopSession();
    when(createDocument.apply(any(DocumentCreationRequest.class))).thenReturn(mock(Document.class));

    // One list-file block uses .name(), another uses only .text()
    var listBlocks =
        List.of(
            ContentBlock.builder().name("from-name.txt").build(),
            ContentBlock.builder().text("  from-text.txt  ").build());

    var stub = new StreamStub();
    stub.listResponseEvents.add(List.of()); // first call: pre-existing (empty)
    stub.listResponseEvents.add(
        List.of(CodeInterpreterResult.builder().content(listBlocks).build()));
    stub.withReadFile(
            "from-name.txt", ContentBlock.builder().data(SdkBytes.fromUtf8String("a")).build())
        .withReadFile(
            "from-text.txt", ContentBlock.builder().data(SdkBytes.fromUtf8String("b")).build())
        .install();

    executor.execute(buildRequest(ActualValue.CODE, null), 1L);

    var invokeCaptor = ArgumentCaptor.forClass(InvokeCodeInterpreterRequest.class);
    verify(asyncClient, atLeast(1)).invokeCodeInterpreter(invokeCaptor.capture(), any());
    assertThat(invokeCaptor.getAllValues())
        .filteredOn(r -> r.name() == ToolName.READ_FILES)
        .extracting(r -> r.arguments().paths().get(0))
        .containsExactlyInAnyOrder("from-name.txt", "from-text.txt");
  }

  // --- Streaming test helpers ---

  /**
   * Builds a {@link CodeInterpreterResult} with a populated {@link ToolResultStructuredContent} so
   * the executor's event-stream parser can accumulate stdout/stderr/exitCode/executionTime.
   */
  private static CodeInterpreterResult resultWithStructured(
      String stdout, String stderr, Integer exitCode, Double executionTime) {
    return CodeInterpreterResult.builder()
        .structuredContent(
            ToolResultStructuredContent.builder()
                .stdout(stdout)
                .stderr(stderr)
                .exitCode(exitCode)
                .executionTime(executionTime)
                .build())
        .build();
  }

  /**
   * Wraps a list of {@link CodeInterpreterResult}s as an {@link SdkPublisher} of {@link
   * CodeInterpreterStreamOutput} events, using the SDK's {@code resultBuilder()} so that {@code
   * event.accept(visitor)} dispatches to {@code visitResult}. Delivery is synchronous so assertions
   * made after {@code executor.execute(...)} see all events.
   */
  private static SdkPublisher<CodeInterpreterStreamOutput> toPublisher(
      List<CodeInterpreterResult> results) {
    var events = new ArrayList<CodeInterpreterStreamOutput>();
    for (var r : results) {
      events.add(
          CodeInterpreterStreamOutput.resultBuilder()
              .content(r.content())
              .structuredContent(r.structuredContent())
              .isError(r.isError())
              .build());
    }
    return SdkPublisher.fromIterable(events);
  }

  /**
   * Fluent builder for routing {@code asyncClient.invokeCodeInterpreter(...)} calls by {@link
   * ToolName}. One instance represents the scripted responses for a single {@code
   * executor.execute(...)} invocation.
   */
  private class StreamStub {
    List<CodeInterpreterResult> execEvents = List.of();
    final Deque<List<CodeInterpreterResult>> listResponseEvents = new ArrayDeque<>();
    final Map<String, List<CodeInterpreterResult>> readResponseEvents = new HashMap<>();
    final Map<String, Throwable> readFailures = new HashMap<>();

    StreamStub withExecEvents(CodeInterpreterResult... events) {
      execEvents = List.of(events);
      return this;
    }

    /** Declares the results of successive listFiles calls (pre-execution, post-execution). */
    StreamStub withListResponses(List<String> before, List<String> after) {
      listResponseEvents.add(
          List.of(CodeInterpreterResult.builder().content(toNameBlocks(before)).build()));
      listResponseEvents.add(
          List.of(CodeInterpreterResult.builder().content(toNameBlocks(after)).build()));
      return this;
    }

    StreamStub withReadFile(String path, ContentBlock... blocks) {
      readResponseEvents.put(
          path, List.of(CodeInterpreterResult.builder().content(List.of(blocks)).build()));
      return this;
    }

    StreamStub withReadFileFailure(String path, Throwable cause) {
      readFailures.put(path, cause);
      return this;
    }

    private List<ContentBlock> toNameBlocks(List<String> names) {
      return names.stream().map(n -> ContentBlock.builder().name(n).build()).toList();
    }

    void install() {
      // ensure defaults so the executor never gets null-streamed if a test omits something
      if (listResponseEvents.isEmpty()) {
        listResponseEvents.add(List.of());
        listResponseEvents.add(List.of());
      }
      when(asyncClient.invokeCodeInterpreter(
              any(InvokeCodeInterpreterRequest.class),
              any(InvokeCodeInterpreterResponseHandler.class)))
          .thenAnswer(
              inv -> {
                InvokeCodeInterpreterRequest req = inv.getArgument(0);
                InvokeCodeInterpreterResponseHandler handler = inv.getArgument(1);
                List<CodeInterpreterResult> results;
                if (req.name() == ToolName.EXECUTE_CODE) {
                  results = execEvents;
                } else if (req.name() == ToolName.LIST_FILES) {
                  results = listResponseEvents.isEmpty() ? List.of() : listResponseEvents.poll();
                } else if (req.name() == ToolName.READ_FILES) {
                  var path = req.arguments().paths().get(0);
                  if (readFailures.containsKey(path)) {
                    return CompletableFuture.failedFuture(readFailures.get(path));
                  }
                  results = readResponseEvents.getOrDefault(path, List.of());
                } else {
                  results = List.of();
                }
                handler.onEventStream(toPublisher(results));
                return CompletableFuture.completedFuture(null);
              });
    }
  }
}
