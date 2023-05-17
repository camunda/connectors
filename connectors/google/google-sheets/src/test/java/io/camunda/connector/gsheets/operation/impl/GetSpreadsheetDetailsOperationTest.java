/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.gsheets.operation.impl;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.model.Spreadsheet;
import io.camunda.connector.gsheets.BaseTest;
import io.camunda.connector.gsheets.model.request.impl.GetSpreadsheetDetails;
import io.camunda.connector.gsheets.supplier.GoogleSheetsServiceSupplier;
import io.camunda.google.model.Authentication;
import java.io.IOException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GetSpreadsheetDetailsOperationTest extends BaseTest {

  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  private Sheets service;

  private Authentication auth;

  @BeforeEach
  public void before() {
    auth = new Authentication();
  }

  @DisplayName("Should get spreadsheet details by id")
  @Test
  void getSpreadsheetDetails_shouldGetDetailsById() throws IOException {
    // Given
    GetSpreadsheetDetails model = new GetSpreadsheetDetails(SPREADSHEET_ID, null);

    Spreadsheet spreadsheet = new Spreadsheet();

    try (MockedStatic<GoogleSheetsServiceSupplier> mockedServiceSupplier =
        mockStatic(GoogleSheetsServiceSupplier.class)) {
      mockedServiceSupplier
          .when(() -> GoogleSheetsServiceSupplier.getGoogleSheetsService(any()))
          .thenReturn(service);

      when(service.spreadsheets().get(anyString()).execute()).thenReturn(spreadsheet);

      // When
      new GetSpreadsheetDetailsOperation(model).execute(auth);

      // Then
      mockedServiceSupplier.verify(() -> GoogleSheetsServiceSupplier.getGoogleSheetsService(auth));
      verify(service.spreadsheets().get(SPREADSHEET_ID)).execute();
    }
  }
}
