/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.gdrive;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.google.api.client.http.AbstractInputStreamContent;
import com.google.api.services.docs.v1.model.BatchUpdateDocumentResponse;
import com.google.api.services.docs.v1.model.Request;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.File;
import io.camunda.connector.document.annotation.jackson.DocumentReferenceModel;
import io.camunda.connector.gdrive.model.GoogleDriveResult;
import io.camunda.connector.gdrive.model.MimeTypeUrl;
import io.camunda.connector.gdrive.model.request.*;
import io.camunda.document.Document;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.stream.Stream;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

class GoogleDriveServiceTest extends BaseTest {

  private static final String SUCCESS_FOLDER_CASES_RESOURCE_PATH =
      "src/test/resources/requests/folder-success-test-cases.json";

  private static final String SUCCESS_FILE_CASES_RESOURCE_PATH =
      "src/test/resources/requests/file-success-test-cases.json";

  private GoogleDriveService service;
  private GoogleDriveClient googleDriveClient;
  private File file;

  @BeforeEach
  public void before() {
    service = new GoogleDriveService();
    googleDriveClient = mock(GoogleDriveClient.class);
    file = new File();
    file.setId(FILE_ID);
    file.setMimeType(MimeTypeUrl.FOLDER.getMimeType());
    Mockito.when(googleDriveClient.createWithMetadata(any())).thenReturn(file);
  }

  @DisplayName("Should create folder form request")
  @ParameterizedTest(name = "Executing test case # {index}")
  @MethodSource("successFolderRequestCases")
  void execute_shouldCreateFolderFromRequest(String input) {
    // Given
    var context = getContextBuilderWithSecrets().variables(input).build();
    GoogleDriveRequest request = context.bindVariables(GoogleDriveRequest.class);
    ArgumentCaptor<File> captor = ArgumentCaptor.forClass(File.class);
    // When
    var execute = (GoogleDriveResult) service.execute(googleDriveClient, request.getResource());
    // Then
    Mockito.verify(googleDriveClient).createWithMetadata(captor.capture());
    File value = captor.getValue();

    assertThat(value.getName()).isEqualTo(FOLDER_NAME);
    assertThat(value.getParents()).isEqualTo(List.of(PARENT_ID));
    assertThat(value.getMimeType()).isEqualTo(MimeTypeUrl.FOLDER.getMimeType());
    assertThat(execute.getGoogleDriveResourceId()).isEqualTo(FILE_ID);
    assertThat(execute.getGoogleDriveResourceUrl())
        .isEqualTo(MimeTypeUrl.getResourceUrl(MimeTypeUrl.FOLDER.getMimeType(), FILE_ID));
  }

  @DisplayName("Should create file from request")
  @ParameterizedTest(name = "Executing test case # {index}")
  @MethodSource("successFileRequestCases")
  void execute_shouldCreateFileFromRequest(String input) {
    // Given
    var context = getContextBuilderWithSecrets().variables(input).build();
    GoogleDriveRequest request = context.bindVariables(GoogleDriveRequest.class);
    String templateId = request.getResource().template().id();
    Mockito.when(googleDriveClient.createWithTemplate(any(), eq(templateId))).thenReturn(file);
    ArgumentCaptor<File> captor = ArgumentCaptor.forClass(File.class);
    // When
    var execute = (GoogleDriveResult) service.execute(googleDriveClient, request.getResource());
    // Then
    Mockito.verify(googleDriveClient).createWithTemplate(captor.capture(), eq(templateId));
    File value = captor.getValue();

    assertThat(value.getName()).isEqualTo(FILE_NAME);
    assertThat(value.getParents()).isEqualTo(List.of(PARENT_ID));
    assertThat(execute.getGoogleDriveResourceId()).isEqualTo(FILE_ID);
  }

  @DisplayName("Should create file without template")
  @ParameterizedTest(name = "Executing test case # {index}")
  @MethodSource("successFileRequestCases")
  void execute_shouldCreateFileWithoutTemplate(String input) {
    // Given
    var context = getContextBuilderWithSecrets().variables(input).build();
    GoogleDriveRequest request = context.bindVariables(GoogleDriveRequest.class);
    String templateId = request.getResource().template().id();
    Mockito.when(googleDriveClient.createWithTemplate(any(), eq(templateId))).thenReturn(file);
    ArgumentCaptor<File> captor = ArgumentCaptor.forClass(File.class);
    // When
    var execute = (GoogleDriveResult) service.execute(googleDriveClient, request.getResource());
    // Then
    Mockito.verify(googleDriveClient).createWithTemplate(captor.capture(), eq(templateId));
    File value = captor.getValue();

    assertThat(value.getName()).isEqualTo(FILE_NAME);
    assertThat(value.getParents()).isEqualTo(List.of(PARENT_ID));
    assertThat(execute.getGoogleDriveResourceId()).isEqualTo(FILE_ID);
    assertThat(execute.getGoogleDriveResourceUrl())
        .isEqualTo(MimeTypeUrl.getResourceUrl(MimeTypeUrl.FOLDER.getMimeType(), FILE_ID));
  }

  @SuppressWarnings("unchecked")
  @DisplayName("Should create file and update document data")
  @ParameterizedTest(name = "Executing test case # {index}")
  @MethodSource("successFileRequestCases")
  void execute_shouldCreateFileAndUpdateDocument(String input) {
    // Given
    var context = getContextBuilderWithSecrets().variables(input).build();
    GoogleDriveRequest request = context.bindVariables(GoogleDriveRequest.class);

    file.setMimeType(MimeTypeUrl.DOCUMENT.getMimeType());
    String templateId = request.getResource().template().id();
    BatchUpdateDocumentResponse response = new BatchUpdateDocumentResponse();
    Mockito.when(googleDriveClient.createWithTemplate(any(), eq(templateId))).thenReturn(file);
    Mockito.when(googleDriveClient.updateDocument(any(), any())).thenReturn(response);
    ArgumentCaptor<List<Request>> captor = ArgumentCaptor.forClass(List.class);
    // When
    var execute = (GoogleDriveResult) service.execute(googleDriveClient, request.getResource());
    // Then
    verify(googleDriveClient).updateDocument(eq(FILE_ID), captor.capture());

    assertThat(captor.getValue().size()).isEqualTo(1);
    assertThat(captor.getValue().get(0).getReplaceAllText()).isNotNull();

    assertThat(execute.getGoogleDriveResourceId()).isEqualTo(FILE_ID);
  }

  @DisplayName("Should do not update document if type is not document")
  @ParameterizedTest(name = "Executing test case # {index}")
  @MethodSource("successFileRequestCases")
  void execute_shouldDoNotUpdateDocumentIfTypeNotDocument(String input) {
    // Given
    var context = getContextBuilderWithSecrets().variables(input).build();
    GoogleDriveRequest request = context.bindVariables(GoogleDriveRequest.class);
    file.setMimeType(MimeTypeUrl.PRESENTATION.getMimeType());
    String templateId = request.getResource().template().id();
    BatchUpdateDocumentResponse response = new BatchUpdateDocumentResponse();
    Mockito.when(googleDriveClient.createWithTemplate(any(), eq(templateId))).thenReturn(file);
    Mockito.when(googleDriveClient.updateDocument(any(), any())).thenReturn(response);
    // When
    service.execute(googleDriveClient, request.getResource());
    // Then
    verify(googleDriveClient, never()).updateDocument(any(), any());
  }

  @Test
  void execute_shouldUploadFile() throws IOException {
    // Given
    var docMetadata =
        new DocumentReferenceModel.CamundaDocumentMetadataModel(
            "image/png", null, 66497L, "picture", null, null, null);

    Document document = Mockito.mock(Document.class);
    when(document.metadata()).thenReturn(docMetadata);
    when(document.asByteArray()).thenReturn(new byte[1]);

    var resource =
        new Resource(Type.UPLOAD, null, null, null, null, null, new UploadData(document));

    Drive drive = mock(Drive.class, Mockito.RETURNS_DEEP_STUBS);

    when(googleDriveClient.getDriveService()).thenReturn(drive);
    when(drive.files().create(any(File.class), any(AbstractInputStreamContent.class)).execute())
        .thenReturn(new File().setId("1"));

    var result = service.execute(googleDriveClient, resource);
    assertThat(result).isInstanceOf(GoogleDriveResult.class);
    assertThat(((GoogleDriveResult) result).getGoogleDriveResourceId()).isEqualTo("1");
    assertThat(((GoogleDriveResult) result).getGoogleDriveResourceUrl())
        .isEqualTo(String.format(MimeTypeUrl.FILE_TEMPLATE_URL, "1"));
  }

  @Test
  void execute_shouldDownloadFile() throws IOException {

    var resource = new Resource(Type.DOWNLOAD, null, null, null, null, new DownloadData("1"), null);

    Drive drive = mock(Drive.class, Mockito.RETURNS_DEEP_STUBS);
    when(googleDriveClient.getDriveService()).thenReturn(drive);
    when(drive.files().get(anyString()).execute()).thenReturn(new File());
    Drive.Files.Get getFile = mock(Drive.Files.Get.class);
    doNothing().when(getFile).executeAndDownloadTo(any(ByteArrayOutputStream.class));

    var result = service.execute(googleDriveClient, resource);

    assertThat(result).isInstanceOf(Pair.class);
    assertThat(((Pair) result).getLeft()).isInstanceOf(File.class);
    assertThat(((Pair) result).getRight()).isInstanceOf(ByteArrayOutputStream.class);
  }

  private static Stream<String> successFolderRequestCases() throws IOException {
    return BaseTest.loadTestCasesFromResourceFile(SUCCESS_FOLDER_CASES_RESOURCE_PATH);
  }

  private static Stream<String> successFileRequestCases() throws IOException {
    return BaseTest.loadTestCasesFromResourceFile(SUCCESS_FILE_CASES_RESOURCE_PATH);
  }
}
