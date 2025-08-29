/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.slack.outbound.caller;

import static io.camunda.connector.slack.outbound.caller.FileUploader.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.slack.api.SlackConfig;
import com.slack.api.methods.MethodsClient;
import com.slack.api.methods.SlackApiException;
import com.slack.api.methods.request.files.FilesCompleteUploadExternalRequest;
import com.slack.api.methods.request.files.FilesGetUploadURLExternalRequest;
import com.slack.api.methods.response.files.FilesCompleteUploadExternalResponse;
import com.slack.api.methods.response.files.FilesGetUploadURLExternalResponse;
import com.slack.api.model.File;
import com.slack.api.util.http.SlackHttpClient;
import io.camunda.connector.api.document.Document;
import io.camunda.connector.api.document.DocumentMetadata;
import io.camunda.connector.api.document.DocumentReference;
import io.camunda.connector.document.jackson.DocumentReferenceModel;
import io.camunda.connector.test.TestDocument;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import okhttp3.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FileUploaderTest {

  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  private MethodsClient methodsClient;

  @InjectMocks private FileUploader fileUploader;

  @Test
  void uploadDocuments() throws SlackApiException, IOException {
    List<Document> documents = List.of(prepareDocument());

    mockGetExternalURL();
    when(methodsClient.getSlackHttpClient().getConfig()).thenReturn(SlackConfig.DEFAULT);

    OkHttpClient okHttpClient = Mockito.mock(OkHttpClient.class, Answers.RETURNS_DEEP_STUBS);

    try (MockedStatic<SlackHttpClient> slackHttpClient =
        Mockito.mockStatic(SlackHttpClient.class)) {
      slackHttpClient.when(() -> SlackHttpClient.buildOkHttpClient(any())).thenReturn(okHttpClient);

      var response = mock(Response.class);
      when(response.code()).thenReturn(200);

      when(okHttpClient.newCall(any(Request.class)).execute()).thenReturn(response);

      List<File> files = List.of(File.builder().id("id").build());
      mockCompleteUpload(files);

      List<File> result = fileUploader.uploadDocuments(documents);
      assertThat(result).hasSize(1);
      assertThat(result).isEqualTo(files);
    }
  }

  @Test
  void uploadDocumentsShouldFilterEmptyFiles() throws SlackApiException, IOException {
    List<Document> documents = List.of(prepareDocument());
    mockGetExternalURL();
    when(methodsClient.getSlackHttpClient().getConfig()).thenReturn(SlackConfig.DEFAULT);

    OkHttpClient okHttpClient = Mockito.mock(OkHttpClient.class, Answers.RETURNS_DEEP_STUBS);

    try (MockedStatic<SlackHttpClient> slackHttpClient =
        Mockito.mockStatic(SlackHttpClient.class)) {
      slackHttpClient.when(() -> SlackHttpClient.buildOkHttpClient(any())).thenReturn(okHttpClient);

      var response = mock(Response.class);
      when(response.code()).thenReturn(200);

      when(okHttpClient.newCall(any(Request.class)).execute()).thenReturn(response);

      List<File> emptyFiles = List.of();
      mockCompleteUpload(emptyFiles);

      List<File> result = fileUploader.uploadDocuments(documents);
      assertThat(result).hasSize(0);
    }
  }

  @Test
  void exceptionDuringGetExternalURLCall() throws SlackApiException, IOException {
    List<Document> documents = List.of(prepareDocument());
    var uploadURLResp = new FilesGetUploadURLExternalResponse();
    uploadURLResp.setOk(false);
    uploadURLResp.setError("Do not have Read permissions");

    when(methodsClient.filesGetUploadURLExternal(any(FilesGetUploadURLExternalRequest.class)))
        .thenReturn(uploadURLResp);

    Exception ex =
        assertThrows(RuntimeException.class, () -> fileUploader.uploadDocuments(documents));

    assertThat(ex.getMessage()).contains(GET_EXTERNAL_URL_EX);
    assertThat(ex.getMessage()).contains(uploadURLResp.getError());
  }

  @Test
  void exceptionDuringExternalURlCall() throws SlackApiException, IOException {
    List<Document> documents = List.of(prepareDocument());

    var uploadURLResp = new FilesGetUploadURLExternalResponse();
    uploadURLResp.setOk(true);
    uploadURLResp.setUploadUrl("https:example.com");

    when(methodsClient.filesGetUploadURLExternal(any(FilesGetUploadURLExternalRequest.class)))
        .thenReturn(uploadURLResp);
    when(methodsClient.getSlackHttpClient().getConfig()).thenReturn(SlackConfig.DEFAULT);
    OkHttpClient okHttpClient = Mockito.mock(OkHttpClient.class, Answers.RETURNS_DEEP_STUBS);

    try (MockedStatic<SlackHttpClient> slackHttpClient =
        Mockito.mockStatic(SlackHttpClient.class)) {
      slackHttpClient.when(() -> SlackHttpClient.buildOkHttpClient(any())).thenReturn(okHttpClient);

      String msg = "Internal Server Error";

      Response response = mock(Response.class);
      when(response.code()).thenReturn(500);
      when(response.message()).thenReturn(msg);

      when(okHttpClient.newCall(any(Request.class)).execute()).thenReturn(response);

      Exception ex =
          assertThrows(RuntimeException.class, () -> fileUploader.uploadDocuments(documents));

      assertThat(ex.getMessage()).contains(EXTERNAL_URL_CALL_EX);
      assertThat(ex.getMessage()).contains(msg);
    }
  }

  @Test
  void ExceptionDuringCompleteFileUploadCall() throws SlackApiException, IOException {
    List<Document> documents = List.of(prepareDocument());
    var uploadURLResp = new FilesGetUploadURLExternalResponse();
    uploadURLResp.setOk(true);
    uploadURLResp.setUploadUrl("https:example.com");

    when(methodsClient.filesGetUploadURLExternal(any(FilesGetUploadURLExternalRequest.class)))
        .thenReturn(uploadURLResp);
    when(methodsClient.getSlackHttpClient().getConfig()).thenReturn(SlackConfig.DEFAULT);

    OkHttpClient okHttpClient = Mockito.mock(OkHttpClient.class, Answers.RETURNS_DEEP_STUBS);

    try (MockedStatic<SlackHttpClient> slackHttpClient =
        Mockito.mockStatic(SlackHttpClient.class)) {
      slackHttpClient.when(() -> SlackHttpClient.buildOkHttpClient(any())).thenReturn(okHttpClient);

      var response = mock(Response.class);
      when(response.code()).thenReturn(200);

      when(okHttpClient.newCall(any(Request.class)).execute()).thenReturn(response);

      FilesCompleteUploadExternalResponse completeUploadResp =
          new FilesCompleteUploadExternalResponse();
      completeUploadResp.setOk(false);
      completeUploadResp.setError("somthing go wrong");

      when(methodsClient.filesCompleteUploadExternal(any(FilesCompleteUploadExternalRequest.class)))
          .thenReturn(completeUploadResp);

      Exception ex =
          assertThrows(RuntimeException.class, () -> fileUploader.uploadDocuments(documents));

      assertThat(ex.getMessage()).contains(completeUploadResp.getError());
      assertThat(ex.getMessage()).contains(COMPLETE_UPLOAD_CALL_EX);
    }
  }

  private Document prepareDocument() {
    DocumentReference.CamundaDocumentReference documentReference =
        Mockito.mock(DocumentReference.CamundaDocumentReference.class);

    DocumentMetadata documentMetadata =
        new DocumentReferenceModel.CamundaDocumentMetadataModel(
            "txt", OffsetDateTime.now(), 3000L, "fileName", "processId", 2000L, Map.of());

    return new TestDocument(new byte[0], documentMetadata, documentReference, "id");
  }

  private void mockGetExternalURL() throws SlackApiException, IOException {
    var uploadURLResp = new FilesGetUploadURLExternalResponse();
    uploadURLResp.setOk(true);
    uploadURLResp.setUploadUrl("https:example.com");

    when(methodsClient.filesGetUploadURLExternal(any(FilesGetUploadURLExternalRequest.class)))
        .thenReturn(uploadURLResp);
  }

  private void mockCompleteUpload(List<File> filesResponse) throws SlackApiException, IOException {
    FilesCompleteUploadExternalResponse completeUploadResp =
        new FilesCompleteUploadExternalResponse();
    completeUploadResp.setOk(true);
    completeUploadResp.setFiles(filesResponse);

    when(methodsClient.filesCompleteUploadExternal(any(FilesCompleteUploadExternalRequest.class)))
        .thenReturn(completeUploadResp);
  }
}
