/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.gdrive;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;

import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.gdrive.model.GoogleDriveResult;
import io.camunda.connector.gdrive.model.MimeTypeUrl;
import io.camunda.connector.gdrive.model.request.Resource;
import io.camunda.connector.validation.impl.DefaultValidationProvider;
import io.camunda.connector.api.document.Document;
import java.io.IOException;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mockito;

class GoogleDriveFunctionTest extends BaseTest {

  private static final String SUCCESS_CASES_RESOURCE_PATH =
      "src/test/resources/requests/request-success-test-cases.json";
  private static final String SUCCESS_UPLOAD_RESOURCE_PATH =
      "src/test/resources/requests/file-success-upload.json";
  private static final String SUCCESS_DOWNLOAD_RESOURCE_PATH =
      "src/test/resources/requests/file-success-download.json";

  @DisplayName("Should execute connector and return success result")
  @ParameterizedTest(name = "Executing test case # {index}")
  @MethodSource("successRequestCases")
  void execute_shouldExecuteAndReturnResultWhenGiveContext(String input) {
    // Given
    GoogleDriveService googleDriveServiceMock = mock(GoogleDriveService.class);
    GoogleDriveFunction service = new GoogleDriveFunction(googleDriveServiceMock);

    OutboundConnectorContext context =
        getContextBuilderWithSecrets()
            .variables(input)
            .validation(new DefaultValidationProvider())
            .build();

    GoogleDriveResult googleDriveResult = new GoogleDriveResult();
    googleDriveResult.setGoogleDriveResourceId(FILE_ID);
    googleDriveResult.setGoogleDriveResourceUrl(FILE_URL);

    Mockito.when(googleDriveServiceMock.execute(any(GoogleDriveClient.class), any(Resource.class)))
        .thenReturn(googleDriveResult);
    // When
    Object execute = service.execute(context);
    // Then
    assertThat(execute).isInstanceOf(GoogleDriveResult.class);
    assertThat(((GoogleDriveResult) execute).getGoogleDriveResourceId()).isEqualTo(FILE_ID);
    assertThat(((GoogleDriveResult) execute).getGoogleDriveResourceUrl()).isEqualTo(FILE_URL);
  }

  @ParameterizedTest(name = "Executing test case # {index}")
  @MethodSource("successUploadCases")
  void execute_shouldExecuteFileUpload(String input) {
    // Given
    GoogleDriveService googleDriveServiceMock = mock(GoogleDriveService.class);
    GoogleDriveFunction service = new GoogleDriveFunction(googleDriveServiceMock);

    OutboundConnectorContext context =
        getContextBuilderWithSecrets()
            .variables(input)
            .validation(new DefaultValidationProvider())
            .build();

    GoogleDriveResult googleDriveResult = new GoogleDriveResult();
    googleDriveResult.setGoogleDriveResourceId(FILE_ID);
    googleDriveResult.setGoogleDriveResourceUrl(
        String.format(MimeTypeUrl.FILE_TEMPLATE_URL, FILE_ID));

    Mockito.when(googleDriveServiceMock.execute(any(GoogleDriveClient.class), any(Resource.class)))
        .thenReturn(googleDriveResult);
    // When
    Object execute = service.execute(context);
    // Then
    assertThat(execute).isInstanceOf(GoogleDriveResult.class);
    assertThat(((GoogleDriveResult) execute).getGoogleDriveResourceId()).isEqualTo(FILE_ID);
    assertThat(((GoogleDriveResult) execute).getGoogleDriveResourceUrl())
        .isEqualTo((String.format(MimeTypeUrl.FILE_TEMPLATE_URL, FILE_ID)));
  }

  @ParameterizedTest(name = "Executing test case # {index}")
  @MethodSource("successDownloadCases")
  void execute_shouldExecuteFileDownload(String input) {
    // Given
    GoogleDriveService googleDriveServiceMock = mock(GoogleDriveService.class);
    GoogleDriveFunction service = new GoogleDriveFunction(googleDriveServiceMock);

    OutboundConnectorContext context =
        getContextBuilderWithSecrets()
            .variables(input)
            .validation(new DefaultValidationProvider())
            .build();

    Mockito.when(googleDriveServiceMock.execute(any(GoogleDriveClient.class), any(Resource.class)))
        .thenReturn(mock(Document.class));

    var result = (Document) service.execute(context);

    assertThat(result).isInstanceOf(Document.class);
  }

  private static Stream<String> successRequestCases() throws IOException {
    return BaseTest.loadTestCasesFromResourceFile(SUCCESS_CASES_RESOURCE_PATH);
  }

  private static Stream<String> successUploadCases() throws IOException {
    return BaseTest.loadTestCasesFromResourceFile(SUCCESS_UPLOAD_RESOURCE_PATH);
  }

  private static Stream<String> successDownloadCases() throws IOException {
    return BaseTest.loadTestCasesFromResourceFile(SUCCESS_DOWNLOAD_RESOURCE_PATH);
  }
}
