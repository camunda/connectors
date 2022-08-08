/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information regarding copyright
 * ownership. Camunda licenses this file to you under the Apache License,
 * Version 2.0; you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.camunda.connector.gdrive;

import static io.camunda.connector.gdrive.GoogleDriveService.FOLDER_MIME_TYPE;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;

import com.google.api.services.drive.model.File;
import io.camunda.connector.gdrive.model.GoogleDriveResult;
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

  private GoogleDriveService service;
  private GoogleDriveClient googleDriveClient;

  @BeforeEach
  public void before() {
    service = new GoogleDriveService();
    googleDriveClient = mock(GoogleDriveClient.class);
    File file = new File();
    file.setId(FILE_ID);
    file.setMimeType(FOLDER_MIME_TYPE);
    Mockito.when(googleDriveClient.createWithMetadata(any())).thenReturn(file);
  }

  @DisplayName("Should create folder form request")
  @ParameterizedTest(name = "Executing test case # {index}")
  @MethodSource("successFolderRequestCases")
  public void execute_shouldCreateFolderFromRequest(String input) {
    // Given
    GoogleDriveRequest request = GSON.fromJson(input, GoogleDriveRequest.class);
    ArgumentCaptor<File> captor = ArgumentCaptor.forClass(File.class);
    // When
    GoogleDriveResult execute = service.execute(googleDriveClient, request.getResource());
    // Then
    Mockito.verify(googleDriveClient).createWithMetadata(captor.capture());
    File value = captor.getValue();

    assertThat(value.getName()).isEqualTo(FOLDER_NAME);
    assertThat(value.getParents()).isEqualTo(List.of(PARENT_ID));
    assertThat(value.getMimeType()).isEqualTo(FOLDER_MIME_TYPE);
    assertThat(execute.getGoogleDriveResourceId()).isEqualTo(FILE_ID);
    assertThat(execute.getGoogleDriveResourceUrl()).isEqualTo(service.getFolderUrlById(FILE_ID));
  }

  private static Stream<String> successFolderRequestCases() throws IOException {
    return BaseTest.loadTestCasesFromResourceFile(SUCCESS_FOLDER_CASES_RESOURCE_PATH);
  }
}
