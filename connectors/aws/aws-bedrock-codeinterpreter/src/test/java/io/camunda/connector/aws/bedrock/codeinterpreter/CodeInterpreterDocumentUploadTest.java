/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.aws.bedrock.codeinterpreter;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.connector.api.document.Document;
import io.camunda.connector.api.document.DocumentCreationRequest;
import io.camunda.connector.api.document.DocumentMetadata;
import io.camunda.connector.api.error.ConnectorException;
import io.camunda.connector.aws.bedrock.codeinterpreter.model.request.CodeInterpreterInput;
import io.camunda.connector.aws.bedrock.codeinterpreter.model.request.CodeInterpreterRequest;
import io.camunda.connector.aws.bedrock.codeinterpreter.model.request.FileUpload;
import io.camunda.connector.aws.bedrock.codeinterpreter.model.request.Language;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.core.async.SdkPublisher;
import software.amazon.awssdk.services.bedrockagentcore.BedrockAgentCoreAsyncClient;
import software.amazon.awssdk.services.bedrockagentcore.BedrockAgentCoreClient;
import software.amazon.awssdk.services.bedrockagentcore.model.CodeInterpreterStreamOutput;
import software.amazon.awssdk.services.bedrockagentcore.model.ContentBlock;
import software.amazon.awssdk.services.bedrockagentcore.model.InputContentBlock;
import software.amazon.awssdk.services.bedrockagentcore.model.InvokeCodeInterpreterRequest;
import software.amazon.awssdk.services.bedrockagentcore.model.InvokeCodeInterpreterResponseHandler;
import software.amazon.awssdk.services.bedrockagentcore.model.StartCodeInterpreterSessionRequest;
import software.amazon.awssdk.services.bedrockagentcore.model.StartCodeInterpreterSessionResponse;
import software.amazon.awssdk.services.bedrockagentcore.model.StopCodeInterpreterSessionRequest;
import software.amazon.awssdk.services.bedrockagentcore.model.StopCodeInterpreterSessionResponse;
import software.amazon.awssdk.services.bedrockagentcore.model.ToolName;

@ExtendWith(MockitoExtension.class)
class CodeInterpreterDocumentUploadTest extends BaseTest {

  @Mock private BedrockAgentCoreClient syncClient;
  @Mock private BedrockAgentCoreAsyncClient asyncClient;
  @Mock private Function<DocumentCreationRequest, Document> createDocument;

  private CodeInterpreterExecutor executor;
  private final List<InvokeCodeInterpreterRequest> invocations = new ArrayList<>();

  @BeforeEach
  void setUp() {
    executor = new CodeInterpreterExecutor(syncClient, asyncClient, createDocument);
    lenient()
        .when(syncClient.startCodeInterpreterSession(any(StartCodeInterpreterSessionRequest.class)))
        .thenReturn(StartCodeInterpreterSessionResponse.builder().sessionId("sess").build());
    lenient()
        .when(syncClient.stopCodeInterpreterSession(any(StopCodeInterpreterSessionRequest.class)))
        .thenReturn(StopCodeInterpreterSessionResponse.builder().build());
  }

  @Test
  void shouldNotCallWriteFilesWithoutDocuments() {
    stubInvoke(false);

    var response = executor.execute(request(null), 1L);

    assertThat(response.uploadedFiles()).isEmpty();
    assertThat(writeFileCalls()).isEmpty();

    response = executor.execute(request(List.of()), 1L);

    assertThat(response.uploadedFiles()).isEmpty();
    assertThat(writeFileCalls()).isEmpty();
  }

  @Test
  void shouldUploadTextAsTextAndBinaryAsBlobBeforeTheCodeRuns() {
    stubInvoke(false);
    var text = document("notes.txt", "text/plain; charset=utf-8", "héllo".getBytes(UTF_8));
    var json = document("data.json", "application/json", "{\"a\":1}".getBytes(UTF_8));
    var png = document("image.png", "image/png", new byte[] {(byte) 0x89, 'P', 'N', 'G'});
    var badUtf8 = document("latin.txt", "text/plain", new byte[] {(byte) 0xE9, (byte) 0xFF});
    var noType = document("unknown", null, "plain".getBytes(UTF_8));

    var response = executor.execute(request(List.of(text, json, png, badUtf8, noType)), 1L);

    assertThat(response.uploadedFiles())
        .containsExactly(
            "uploads/notes.txt",
            "uploads/data.json",
            "uploads/image.png",
            "uploads/latin.txt",
            "uploads/unknown");
    assertThat(writeFileCalls()).hasSize(1);
    var blocks = writeFileCalls().get(0).arguments().content();
    assertThat(blocks)
        .extracting(InputContentBlock::path)
        .containsExactlyElementsOf(response.uploadedFiles());
    assertThat(blocks.get(0).text()).isEqualTo("héllo");
    assertThat(blocks.get(0).blob()).isNull();
    assertThat(blocks.get(1).text()).isEqualTo("{\"a\":1}");
    assertThat(blocks.get(2).text()).isNull();
    assertThat(blocks.get(2).blob().asByteArray()).containsExactly(0x89, 'P', 'N', 'G');
    assertThat(blocks.get(3).text()).isNull();
    assertThat(blocks.get(3).blob().asByteArray()).containsExactly(0xE9, 0xFF);
    assertThat(blocks.get(4).text()).isNull();
    assertThat(blocks.get(4).blob()).isNotNull();

    // the upload happens before the first listFiles call, so uploaded files are not reported as
    // generated files
    assertThat(invocations.get(0).name()).isEqualTo(ToolName.WRITE_FILES);
    assertThat(invocations.get(0).sessionId()).isEqualTo("sess");
    assertThat(invocations.get(1).name()).isEqualTo(ToolName.LIST_FILES);
    assertThat(invocations.get(2).name()).isEqualTo(ToolName.EXECUTE_CODE);
  }

  @Test
  void shouldSanitizeFileNames() {
    stubInvoke(false);
    var docs =
        List.of(
            document("../../etc/passwd", "text/plain", new byte[0]),
            document("C:\\temp\\report.pdf", "application/pdf", new byte[0]),
            document("..", "text/plain", new byte[0]),
            document("  ", "text/plain", new byte[0]),
            document(null, "text/plain", new byte[0]),
            document("dir/", "text/plain", new byte[0]),
            document("tab\tname.txt", "text/plain", new byte[0]));

    var response = executor.execute(request(docs), 1L);

    assertThat(response.uploadedFiles())
        .containsExactly(
            "uploads/passwd",
            "uploads/report.pdf",
            "uploads/document-3",
            "uploads/document-4",
            "uploads/document-5",
            "uploads/document-6",
            "uploads/tabname.txt");
  }

  @Test
  void shouldStoreFilesUnderTheRequestedFileName() {
    stubInvoke(false);
    var named = document("0f9c-uuid", "text/csv", "a;b".getBytes(UTF_8));
    var unnamed = document("fallback.txt", "text/plain", "x".getBytes(UTF_8));
    var unusable = document(null, "text/plain", "y".getBytes(UTF_8));
    var duplicate = document("other", "text/plain", "z".getBytes(UTF_8));

    var response =
        executor.execute(
            requestWithFiles(
                List.of(
                    new FileUpload(named, "weather.csv"),
                    new FileUpload(unnamed, null),
                    new FileUpload(unusable, "  "),
                    new FileUpload(duplicate, "../weather.csv"))),
            1L);

    assertThat(response.uploadedFiles())
        .containsExactly(
            "uploads/weather.csv",
            "uploads/fallback.txt",
            "uploads/document-3",
            "uploads/weather-2.csv");
  }

  @Test
  void shouldFailForAFileWithoutDocument() {
    assertThatThrownBy(
            () -> executor.execute(requestWithFiles(List.of(new FileUpload(null, "a.txt"))), 1L))
        .isInstanceOf(ConnectorException.class)
        .hasMessageContaining("index 0");
  }

  @Test
  void shouldMakeDuplicateNamesUniqueDeterministically() {
    stubInvoke(false);
    var docs =
        List.of(
            document("report.pdf", "application/pdf", new byte[0]),
            document("report.pdf", "application/pdf", new byte[0]),
            document("report-2.pdf", "application/pdf", new byte[0]),
            document("report.pdf", "application/pdf", new byte[0]),
            document("README", "text/plain", new byte[0]),
            document("README", "text/plain", new byte[0]));

    var response = executor.execute(request(docs), 1L);

    assertThat(response.uploadedFiles())
        .containsExactly(
            "uploads/report.pdf",
            "uploads/report-2.pdf",
            "uploads/report-2-2.pdf",
            "uploads/report-3.pdf",
            "uploads/README",
            "uploads/README-2");
  }

  @Test
  void shouldFailWhenUploadIsRejectedByTheService() {
    stubInvoke(true);
    var docs = List.of(document("a.txt", "text/plain", "a".getBytes(UTF_8)));

    assertThatThrownBy(() -> executor.execute(request(docs), 1L))
        .isInstanceOfSatisfying(
            ConnectorException.class,
            e -> {
              assertThat(e.getErrorCode()).isEqualTo("DOCUMENT_UPLOAD_FAILED");
              assertThat(e.getMessage())
                  .contains("Failed to upload documents")
                  .contains("disk full");
            });
    assertThat(invocations)
        .extracting(InvokeCodeInterpreterRequest::name)
        .doesNotContain(ToolName.EXECUTE_CODE);
    verify(syncClient).stopCodeInterpreterSession(any(StopCodeInterpreterSessionRequest.class));
  }

  @Test
  void shouldFailWhenUploadCallFails() {
    when(asyncClient.invokeCodeInterpreter(
            any(InvokeCodeInterpreterRequest.class),
            any(InvokeCodeInterpreterResponseHandler.class)))
        .thenReturn(CompletableFuture.failedFuture(new RuntimeException("network down")));
    var docs = List.of(document("a.txt", "text/plain", "a".getBytes(UTF_8)));

    assertThatThrownBy(() -> executor.execute(request(docs), 1L))
        .isInstanceOfSatisfying(
            ConnectorException.class,
            e -> {
              assertThat(e.getErrorCode()).isEqualTo("DOCUMENT_UPLOAD_FAILED");
              assertThat(e.getMessage()).contains("Failed to upload").contains("network down");
            });
  }

  @Test
  void shouldFailWhenDocumentCannotBeRead() {
    var doc = document("a.txt", "text/plain", new byte[0]);
    when(doc.asByteArray()).thenThrow(new IllegalStateException("document expired"));

    assertThatThrownBy(() -> executor.execute(request(List.of(doc)), 1L))
        .isInstanceOfSatisfying(
            ConnectorException.class,
            e -> {
              assertThat(e.getErrorCode()).isEqualTo("DOCUMENT_UPLOAD_FAILED");
              assertThat(e.getMessage()).contains("a.txt").contains("document expired");
            });
  }

  @Test
  void shouldFailWhenTotalSizeExceedsLimit() {
    var big = new byte[(int) CodeInterpreterExecutor.MAX_TOTAL_UPLOAD_SIZE / 2 + 1];
    var docs =
        List.of(
            document("a.bin", "application/octet-stream", big),
            document("b.bin", "application/octet-stream", big));

    assertThatThrownBy(() -> executor.execute(request(docs), 1L))
        .isInstanceOfSatisfying(
            ConnectorException.class,
            e -> {
              assertThat(e.getErrorCode()).isEqualTo("DOCUMENT_UPLOAD_FAILED");
              assertThat(e.getMessage()).contains("maximum total upload size");
            });
    assertThat(invocations).isEmpty();
  }

  // --- helpers ---

  private List<InvokeCodeInterpreterRequest> writeFileCalls() {
    return invocations.stream().filter(r -> r.name() == ToolName.WRITE_FILES).toList();
  }

  private void stubInvoke(boolean writeFilesFails) {
    when(asyncClient.invokeCodeInterpreter(
            any(InvokeCodeInterpreterRequest.class),
            any(InvokeCodeInterpreterResponseHandler.class)))
        .thenAnswer(
            inv -> {
              InvokeCodeInterpreterRequest req = inv.getArgument(0);
              InvokeCodeInterpreterResponseHandler handler = inv.getArgument(1);
              invocations.add(req);
              var events = new ArrayList<CodeInterpreterStreamOutput>();
              if (writeFilesFails && req.name() == ToolName.WRITE_FILES) {
                events.add(
                    CodeInterpreterStreamOutput.resultBuilder()
                        .isError(true)
                        .content(ContentBlock.builder().text("disk full").build())
                        .build());
              }
              handler.onEventStream(SdkPublisher.fromIterable(events));
              return CompletableFuture.completedFuture(null);
            });
  }

  private static Document document(String fileName, String contentType, byte[] content) {
    var document = mock(Document.class);
    var metadata = mock(DocumentMetadata.class);
    lenient().when(metadata.getFileName()).thenReturn(fileName);
    lenient().when(metadata.getContentType()).thenReturn(contentType);
    lenient().when(document.metadata()).thenReturn(metadata);
    lenient().when(document.asByteArray()).thenReturn(content);
    return document;
  }

  private static CodeInterpreterRequest request(List<Document> documents) {
    return requestWithFiles(
        documents == null
            ? null
            : documents.stream().map(document -> new FileUpload(document, null)).toList());
  }

  private static CodeInterpreterRequest requestWithFiles(List<FileUpload> files) {
    var input = new CodeInterpreterInput();
    input.setCode(ActualValue.CODE);
    input.setLanguage(Language.PYTHON);
    input.setFiles(files);
    var request = new CodeInterpreterRequest();
    request.setInput(input);
    return request;
  }
}
