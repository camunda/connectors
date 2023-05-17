/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.gsheets.supplier;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import com.google.api.services.sheets.v4.Sheets;
import io.camunda.google.model.Authentication;
import io.camunda.google.model.AuthenticationType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class GoogleSheetsServiceSupplierTest {

  private static final String ROOT_SHEETS_V4_URL = "https://sheets.googleapis.com/";

  @DisplayName("Should create google sheets client")
  @Test
  void getSheetsService_shouldInitGoogleSheetsServiceV4() {
    // Given
    String token = "Bearer_token";
    Authentication authentication = new Authentication();
    authentication.setAuthType(AuthenticationType.BEARER);
    authentication.setBearerToken(token);

    // When
    Sheets sheets = GoogleSheetsServiceSupplier.getGoogleSheetsService(authentication);

    // Then
    System.out.println(sheets.getServicePath());
    assertThat(sheets.getBaseUrl()).isEqualTo(ROOT_SHEETS_V4_URL);
    assertThat(sheets.getRootUrl()).isEqualTo(ROOT_SHEETS_V4_URL);
    assertThat(sheets.getRootUrl()).isEqualTo(ROOT_SHEETS_V4_URL);
  }
}
