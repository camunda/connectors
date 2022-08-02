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
import static org.mockito.Mockito.when;

import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.File;
import com.google.gson.Gson;
import io.camunda.connector.gdrive.model.request.GoogleDriveRequest;
import java.io.IOException;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mockito;

class GoogleDriveClientTest extends BaseTest {

  private static final String SUCCESS_CASES_RESOURCE_PATH =
      "src/test/resources/requests/one-request-success-test-cases.json";

  private static final String FOLDER_MIME_TYPE = "application/vnd.google-apps.folder";
  private static final String BASE_DRIVE_V3_URL = "https://www.googleapis.com/drive/v3/";
  private static final String ROOT_DRIVE_V3_URL = "https://www.googleapis.com/";
  private static final String SERVICE_PATH_DRIVE_V3_URL = "drive/v3/";

  private GoogleDriveClient client;
  private GoogleDriveRequest request;

  @BeforeEach
  public void before() {
    client = new GoogleDriveClient();
  }

  @DisplayName("Should init google drive client")
  @ParameterizedTest(name = "Executing test case # {index}")
  @MethodSource("successRequestCases")
  public void init_shouldInitGoogleDriveClientVersion3(String input) {
    // Given
    request = GSON.fromJson(input, GoogleDriveRequest.class);

    client = new GoogleDriveClient();
    // When
    client.init(request.getAuthentication());
    Drive drive = client.getDriveService();
    // Then
    assertThat(drive.getApplicationName()).isEqualTo(APPLICATION_NAME);
    assertThat(drive.getBaseUrl()).isEqualTo(BASE_DRIVE_V3_URL);
    assertThat(drive.getRootUrl()).isEqualTo(ROOT_DRIVE_V3_URL);
    assertThat(drive.getServicePath()).isEqualTo(SERVICE_PATH_DRIVE_V3_URL);
  }

  @DisplayName("Should create metadata")
  @ParameterizedTest(name = "Executing test case # {index}")
  @MethodSource("successRequestCases")
  public void createMetaData_shouldCreateMetaData(String input) {
    // Given
    request = GSON.fromJson(input, GoogleDriveRequest.class);
    // When
    File metaData = client.createMetaData(request.getFolder());
    // Then
    assertThat(metaData.getName()).isEqualTo(FOLDER_NAME);
    assertThat(metaData.getMimeType()).isEqualTo(FOLDER_MIME_TYPE);
    assertThat(metaData.getParents()).isEqualTo(List.of(FOLDER_PARENT_ID));
  }

  @DisplayName("Should create metadata file without parents")
  @ParameterizedTest(name = "Executing test case # {index}")
  @MethodSource("successRequestCases")
  public void createMetaData_shouldCreateFileWithoutParents(String input) {
    // Given
    request = GSON.fromJson(input, GoogleDriveRequest.class);
    request.getFolder().setParent(null);
    // When
    File metaData = client.createMetaData(request.getFolder());
    // Then
    assertThat(metaData.getName()).isEqualTo(FOLDER_NAME);
    assertThat(metaData.getMimeType()).isEqualTo(FOLDER_MIME_TYPE);
    assertThat(metaData.getParents()).isNull();
  }

  @DisplayName("Should create folder and pass all executing steps")
  @ParameterizedTest(name = "Executing test case # {index}")
  @MethodSource("successRequestCases")
  public void createFolder_shouldCreateFolder(String input) throws IOException {
    // Given
    Drive drive = Mockito.mock(Drive.class);
    Gson gson = new Gson();
    client = new GoogleDriveClient(drive, GsonComponentSupplier.getJsonFactory(), gson);
    Drive.Files files = Mockito.mock(Drive.Files.class);
    Drive.Files.Create create = Mockito.mock(Drive.Files.Create.class);
    when(drive.files()).thenReturn(files);
    when(files.create(any(File.class))).thenReturn(create);
    when(create.setFields(any(String.class))).thenReturn(create);
    when(create.execute()).thenReturn(new File());
    // When
    File file = client.createFolder(new File());
    // Then
    assertThat(file).isNotNull();
  }

  @DisplayName("Should delete existing client")
  @ParameterizedTest(name = "Executing test case # {index}")
  @MethodSource("successRequestCases")
  public void shutdown_shouldDeleteExistClient(String input) {
    // Given
    request = GSON.fromJson(input, GoogleDriveRequest.class);
    client.init(request.getAuthentication());
    // When
    client.shutdown();
    // Then
    assertThat(client).isNotNull();
  }

  private static Stream<String> successRequestCases() throws IOException {
    return BaseTest.loadTestCasesFromResourceFile(SUCCESS_CASES_RESOURCE_PATH);
  }
}
