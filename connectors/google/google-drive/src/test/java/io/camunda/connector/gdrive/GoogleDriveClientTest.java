/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 *             under one or more contributor license agreements. Licensed under a proprietary license.
 *             See the License.txt file for more information. You may not use this file
 *             except in compliance with the proprietary license.
 */
package io.camunda.connector.gdrive;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.api.services.docs.v1.Docs;
import com.google.api.services.docs.v1.model.BatchUpdateDocumentRequest;
import com.google.api.services.docs.v1.model.BatchUpdateDocumentResponse;
import com.google.api.services.docs.v1.model.ReplaceAllTextRequest;
import com.google.api.services.docs.v1.model.Request;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.File;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

class GoogleDriveClientTest {

  private GoogleDriveClient client;
  private Drive.Files files;
  private Docs docs;

  @BeforeEach
  public void before() {
    Drive drive = Mockito.mock(Drive.class);
    docs = Mockito.mock(Docs.class);
    client = new GoogleDriveClient(drive, docs);
    files = Mockito.mock(Drive.Files.class);
    when(drive.files()).thenReturn(files);
  }

  @DisplayName("Should create google metaData file")
  @Test
  void createByMetaData_shouldCreateFileByMetaData() throws IOException {
    // Given
    Drive.Files.Create create = Mockito.mock(Drive.Files.Create.class);
    when(files.create(any(File.class))).thenReturn(create);
    when(create.execute()).thenReturn(new File());
    // When
    File metadata = client.createWithMetadata(new File());
    // Then
    assertThat(metadata).isNotNull();
  }

  @DisplayName("Should create file from template")
  @ParameterizedTest(name = "Executing test case # {index}")
  @ValueSource(strings = {"templateId", "* - *", "123456789"})
  void createByTemplate_shouldCreateNewFileFromTemplate(String templateId) throws IOException {
    // Given
    Drive.Files.Copy copy = Mockito.mock(Drive.Files.Copy.class);
    when(files.copy(eq(templateId), any(File.class))).thenReturn(copy);
    when(copy.execute()).thenReturn(new File());
    // When
    File byTemplate = client.createWithTemplate(new File(), templateId);
    // Then
    assertThat(byTemplate).isNotNull();
  }

  @DisplayName("Should execute updateDocument method")
  @ParameterizedTest(name = "Executing test case # {index}")
  @ValueSource(strings = {"fileId", "* - *", "123456789"})
  void replaceVariables_shouldExecuteUpdateDocumentMethod(String fileId) throws IOException {
    // Given
    ArgumentCaptor<BatchUpdateDocumentRequest> captor =
        ArgumentCaptor.forClass(BatchUpdateDocumentRequest.class);
    BatchUpdateDocumentResponse batchResponse = new BatchUpdateDocumentResponse();
    Docs.Documents documents = mock(Docs.Documents.class);
    Docs.Documents.BatchUpdate update = mock(Docs.Documents.BatchUpdate.class);
    when(docs.documents()).thenReturn(documents);
    when(documents.batchUpdate(eq(fileId), any())).thenReturn(update);
    when(update.execute()).thenReturn(batchResponse);

    List<Request> requests =
        List.of(
            new Request().setReplaceAllText(new ReplaceAllTextRequest().setReplaceText(fileId)));
    // When
    BatchUpdateDocumentResponse response = client.updateDocument(fileId, requests);
    // Then
    Mockito.verify(documents).batchUpdate(eq(fileId), captor.capture());

    BatchUpdateDocumentRequest value = captor.getValue();
    assertThat(value.getRequests().get(0).getReplaceAllText().getReplaceText()).isEqualTo(fileId);
    assertThat(response).isNotNull();
  }
}
