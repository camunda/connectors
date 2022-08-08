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
package io.camunda.connector.gdrive.supliers;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import com.google.api.services.drive.Drive;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class GoogleDriveSupplierTest {

  private static final String BASE_DRIVE_V3_URL = "https://www.googleapis.com/drive/v3/";
  private static final String ROOT_DRIVE_V3_URL = "https://www.googleapis.com/";
  private static final String SERVICE_PATH_DRIVE_V3_URL = "drive/v3/";

  @DisplayName("Should create google drive client")
  @Test
  public void getDriveClient_shouldInitGoogleDriveClientVersion3() {
    // Given
    String token = "Bearer_token";
    // When
    Drive drive =
        GoogleDriveSupplier.createDriveClientInstance(
            token, GJsonComponentSupplier.getJsonFactory());
    // Then
    assertThat(drive.getBaseUrl()).isEqualTo(BASE_DRIVE_V3_URL);
    assertThat(drive.getRootUrl()).isEqualTo(ROOT_DRIVE_V3_URL);
    assertThat(drive.getServicePath()).isEqualTo(SERVICE_PATH_DRIVE_V3_URL);
  }
}
