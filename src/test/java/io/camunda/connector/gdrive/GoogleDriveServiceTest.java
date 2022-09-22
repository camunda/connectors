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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.google.api.services.docs.v1.model.BatchUpdateDocumentResponse;
import com.google.api.services.docs.v1.model.Request;
import com.google.api.services.drive.model.File;
import io.camunda.connector.gdrive.model.GoogleDriveResult;
import io.camunda.connector.gdrive.model.MimeTypeUrl;
import io.camunda.connector.gdrive.model.request.GoogleDriveRequest;
import java.io.IOException;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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
  public void execute_shouldCreateFolderFromRequest(String input) {
    // Given
    GoogleDriveRequest request = parseInput(input, GoogleDriveRequest.class);
    ArgumentCaptor<File> captor = ArgumentCaptor.forClass(File.class);
    // When
    GoogleDriveResult execute = service.execute(googleDriveClient, request.getResource());
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
  public void execute_shouldCreateFileFromRequest(String input) {
    // Given
    GoogleDriveRequest request = parseInput(input, GoogleDriveRequest.class);
    String templateId = request.getResource().getTemplate().getId();
    Mockito.when(googleDriveClient.createWithTemplate(any(), eq(templateId))).thenReturn(file);
    ArgumentCaptor<File> captor = ArgumentCaptor.forClass(File.class);
    // When
    GoogleDriveResult execute = service.execute(googleDriveClient, request.getResource());
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
  public void execute_shouldCreateFileWithoutTemplate(String input) {
    // Given
    GoogleDriveRequest request = parseInput(input, GoogleDriveRequest.class);
    String templateId = request.getResource().getTemplate().getId();
    Mockito.when(googleDriveClient.createWithTemplate(any(), eq(templateId))).thenReturn(file);
    ArgumentCaptor<File> captor = ArgumentCaptor.forClass(File.class);
    // When
    GoogleDriveResult execute = service.execute(googleDriveClient, request.getResource());
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
  public void execute_shouldCreateFileAndUpdateDocument(String input) {
    // Given
    GoogleDriveRequest request = parseInput(input, GoogleDriveRequest.class);

    file.setMimeType(MimeTypeUrl.DOCUMENT.getMimeType());
    String templateId = request.getResource().getTemplate().getId();
    BatchUpdateDocumentResponse response = new BatchUpdateDocumentResponse();
    Mockito.when(googleDriveClient.createWithTemplate(any(), eq(templateId))).thenReturn(file);
    Mockito.when(googleDriveClient.updateDocument(any(), any())).thenReturn(response);
    ArgumentCaptor<List<Request>> captor = ArgumentCaptor.forClass(List.class);
    // When
    GoogleDriveResult execute = service.execute(googleDriveClient, request.getResource());
    // Then
    verify(googleDriveClient).updateDocument(eq(FILE_ID), captor.capture());

    assertThat(captor.getValue().size()).isEqualTo(1);
    assertThat(captor.getValue().get(0).getReplaceAllText()).isNotNull();

    assertThat(execute.getGoogleDriveResourceId()).isEqualTo(FILE_ID);
  }

  @DisplayName("Should do not update document if type is not document")
  @ParameterizedTest(name = "Executing test case # {index}")
  @MethodSource("successFileRequestCases")
  public void execute_shouldDoNotUpdateDocumentIfTypeNotDocument(String input) {
    // Given
    GoogleDriveRequest request = parseInput(input, GoogleDriveRequest.class);
    file.setMimeType(MimeTypeUrl.PRESENTATION.getMimeType());
    String templateId = request.getResource().getTemplate().getId();
    BatchUpdateDocumentResponse response = new BatchUpdateDocumentResponse();
    Mockito.when(googleDriveClient.createWithTemplate(any(), eq(templateId))).thenReturn(file);
    Mockito.when(googleDriveClient.updateDocument(any(), any())).thenReturn(response);
    // When
    service.execute(googleDriveClient, request.getResource());
    // Then
    verify(googleDriveClient, never()).updateDocument(any(), any());
  }

  private static Stream<String> successFolderRequestCases() throws IOException {
    return BaseTest.loadTestCasesFromResourceFile(SUCCESS_FOLDER_CASES_RESOURCE_PATH);
  }

  private static Stream<String> successFileRequestCases() throws IOException {
    return BaseTest.loadTestCasesFromResourceFile(SUCCESS_FILE_CASES_RESOURCE_PATH);
  }
}
