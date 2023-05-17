/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 *             under one or more contributor license agreements. Licensed under a proprietary license.
 *             See the License.txt file for more information. You may not use this file
 *             except in compliance with the proprietary license.
 */
package io.camunda.connector.gdrive.supliers;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import com.google.api.services.docs.v1.Docs;
import com.google.api.services.drive.Drive;
import io.camunda.google.model.Authentication;
import io.camunda.google.model.AuthenticationType;
import io.camunda.google.supplier.GoogleDriveServiceSupplier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class GoogleDocsServiceSupplierTest {

  private static final String BASE_DRIVE_V3_URL = "https://www.googleapis.com/drive/v3/";
  private static final String ROOT_DRIVE_V3_URL = "https://www.googleapis.com/";
  private static final String SERVICE_PATH_DRIVE_V3_URL = "drive/v3/";

  private static final String DOCS_V1_URL = "https://docs.googleapis.com/";

  @DisplayName("Should create google drive client")
  @Test
  void getDriveClient_shouldInitGoogleDriveClientVersion3() {
    // Given
    String token = "Bearer_token";

    Authentication authentication = new Authentication();
    authentication.setAuthType(AuthenticationType.BEARER);
    authentication.setBearerToken(token);
    // When
    Drive drive = GoogleDriveServiceSupplier.createDriveClientInstance(authentication);
    // Then
    assertThat(drive.getBaseUrl()).isEqualTo(BASE_DRIVE_V3_URL);
    assertThat(drive.getRootUrl()).isEqualTo(ROOT_DRIVE_V3_URL);
    assertThat(drive.getServicePath()).isEqualTo(SERVICE_PATH_DRIVE_V3_URL);
  }

  @DisplayName("Should create google docs client")
  @Test
  void getDocsClient_shouldInitGoogleDocsClientVersion1() {
    // Given
    String token = "Bearer_token";
    Authentication authentication = new Authentication();
    authentication.setAuthType(AuthenticationType.BEARER);
    authentication.setBearerToken(token);
    // When
    Docs docs = GoogleDocsServiceSupplier.createDocsClientInstance(authentication);
    // Then
    assertThat(docs.getBaseUrl()).isEqualTo(DOCS_V1_URL);
    assertThat(docs.getRootUrl()).isEqualTo(DOCS_V1_URL);
    assertThat(docs.getBaseUrl()).isEqualTo(DOCS_V1_URL);
  }
}
