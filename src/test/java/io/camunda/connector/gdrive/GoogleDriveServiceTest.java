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

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.ArgumentMatchers.any;

import com.google.api.services.drive.model.File;
import io.camunda.connector.gdrive.model.GoogleDriveResult;
import io.camunda.connector.gdrive.model.request.GoogleDriveRequest;
import java.io.IOException;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mockito;

class GoogleDriveServiceTest extends BaseTest {

  private static final String SUCCESS_CASES_RESOURCE_PATH =
      "src/test/resources/requests/one-request-success-test-cases.json";

  @DisplayName("Should init google client, and execute request ")
  @ParameterizedTest(name = "Executing test case # {index}")
  @MethodSource("successRequestCases")
  public void execute_shouldCreateGoogleClientAndExecuteRequestAndReturnFolderId(String input) {
    // Given
    GoogleDriveClient googleDriveClient = Mockito.mock(GoogleDriveClient.class);
    GoogleDriveService service = new GoogleDriveService(googleDriveClient);
    GoogleDriveRequest request = GSON.fromJson(input, GoogleDriveRequest.class);
    File file = new File();
    file.setId(FILE_ID);
    Mockito.when(googleDriveClient.createFolder(any())).thenReturn(file);
    Mockito.when(googleDriveClient.createMetaData(any())).thenReturn(file);
    // When
    GoogleDriveResult execute = service.execute(request);
    // Then
    Mockito.verify(googleDriveClient).init(request.getAuthentication());
    Mockito.verify(googleDriveClient).createMetaData(request.getFolder());
    Mockito.verify(googleDriveClient).createFolder(any(File.class));
    Mockito.verify(googleDriveClient, Mockito.times(1)).shutdown();

    assertThat(execute.getGoogleDriveResourceId()).isEqualTo(FILE_ID);
  }

  @DisplayName("Should return full folder URL")
  @ParameterizedTest(name = "Executing test case # {index}")
  @ValueSource(strings = {"fileId", "idWithNum1239756", "-1!-=-=9071234"})
  public void folderUrlById_shouldReturnFullUrlForGivenFolderId(String inputId) {
    // Given
    GoogleDriveService service = new GoogleDriveService();
    // When
    String folderUrlById = service.getFolderUrlById(inputId);
    // Then
    assertThat(folderUrlById)
        .isEqualTo(String.format(GoogleDriveService.FOLDER_URL_TEMPLATE, inputId));
  }

  private static Stream<String> successRequestCases() throws IOException {
    return BaseTest.loadTestCasesFromResourceFile(SUCCESS_CASES_RESOURCE_PATH);
  }
}
