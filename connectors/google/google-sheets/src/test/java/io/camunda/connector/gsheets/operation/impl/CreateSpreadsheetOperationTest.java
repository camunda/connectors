/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.gsheets.operation.impl;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.model.Spreadsheet;
import io.camunda.connector.gsheets.BaseTest;
import io.camunda.connector.gsheets.model.request.impl.CreateSpreadsheet;
import io.camunda.connector.gsheets.model.response.CreateSpreadSheetResponse;
import io.camunda.connector.gsheets.supplier.GoogleSheetsServiceSupplier;
import io.camunda.google.DriveUtil;
import io.camunda.google.model.Authentication;
import java.io.IOException;
import org.assertj.core.api.AssertionsForClassTypes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CreateSpreadsheetOperationTest extends BaseTest {

  @Mock private Sheets service;
  @Mock private Sheets.Spreadsheets spreadsheets;
  @Mock private Sheets.Spreadsheets.Create create;

  private Spreadsheet response;
  private Authentication auth;

  @BeforeEach
  public void before() throws IOException {
    response = new Spreadsheet();
    response.setSpreadsheetId(SPREADSHEET_ID);
    response.setSpreadsheetUrl(SPREADSHEET_URL);

    auth = new Authentication();

    when(service.spreadsheets()).thenReturn(spreadsheets);
    when(service.spreadsheets().create(any())).thenReturn(create);
    when(service.spreadsheets().create(any()).setFields(any())).thenReturn(create);
  }

  @DisplayName("Should create google spreadsheet")
  @Test
  void createSpreadsheet_shouldCreateSpreadsheetInRootFolder() throws IOException {
    // Given
    CreateSpreadsheet model = new CreateSpreadsheet(SPREADSHEET_NAME, null, null);

    try (MockedStatic<GoogleSheetsServiceSupplier> mockedSheetsServiceSupplier =
            mockStatic(GoogleSheetsServiceSupplier.class);
        MockedStatic<DriveUtil> mockedDriveUtil = mockStatic(DriveUtil.class)) {
      mockedSheetsServiceSupplier
          .when(() -> GoogleSheetsServiceSupplier.getGoogleSheetsService(any()))
          .thenReturn(service);

      when(service.spreadsheets().create(any()).setFields(any()).execute()).thenReturn(response);

      // When
      Object resultObject = new CreateSpreadSheetOperation(model).execute(auth);

      // Then
      mockedSheetsServiceSupplier.verify(
          () -> GoogleSheetsServiceSupplier.getGoogleSheetsService(auth));
      mockedDriveUtil.verifyNoInteractions();

      assertThat(resultObject, instanceOf(CreateSpreadSheetResponse.class));
      CreateSpreadSheetResponse operationResult = (CreateSpreadSheetResponse) resultObject;
      AssertionsForClassTypes.assertThat(operationResult.spreadsheetId()).isEqualTo(SPREADSHEET_ID);
      AssertionsForClassTypes.assertThat(operationResult.spreadsheetUrl())
          .isEqualTo(SPREADSHEET_URL);
    }
  }

  @DisplayName("Should create google spreadsheet in defined folder")
  @Test
  void createSpreadsheet_shouldCreateSpreadsheetInDefinedFolder() throws IOException {
    // Given
    CreateSpreadsheet model = new CreateSpreadsheet(SPREADSHEET_NAME, PARENT, null);

    try (MockedStatic<GoogleSheetsServiceSupplier> mockedSheetsServiceSupplier =
            mockStatic(GoogleSheetsServiceSupplier.class);
        MockedStatic<DriveUtil> mockedDriveUtil = mockStatic(DriveUtil.class)) {
      mockedSheetsServiceSupplier
          .when(() -> GoogleSheetsServiceSupplier.getGoogleSheetsService(any()))
          .thenReturn(service);

      when(service.spreadsheets().create(any()).setFields(any()).execute()).thenReturn(response);

      // When
      Object resultObject = new CreateSpreadSheetOperation(model).execute(auth);

      // Then
      mockedSheetsServiceSupplier.verify(
          () -> GoogleSheetsServiceSupplier.getGoogleSheetsService(auth));
      mockedDriveUtil.verify(() -> DriveUtil.moveFile(auth, PARENT, SPREADSHEET_ID));

      assertThat(resultObject, instanceOf(CreateSpreadSheetResponse.class));
      CreateSpreadSheetResponse operationResult = (CreateSpreadSheetResponse) resultObject;
      AssertionsForClassTypes.assertThat(operationResult.spreadsheetId()).isEqualTo(SPREADSHEET_ID);
      AssertionsForClassTypes.assertThat(operationResult.spreadsheetUrl())
          .isEqualTo(SPREADSHEET_URL);
    }
  }
}
