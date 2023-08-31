/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.gsheets.operation.impl;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.model.BatchUpdateSpreadsheetRequest;
import com.google.api.services.sheets.v4.model.BatchUpdateSpreadsheetResponse;
import com.google.api.services.sheets.v4.model.Request;
import com.google.api.services.sheets.v4.model.SheetProperties;
import io.camunda.connector.gsheets.BaseTest;
import io.camunda.connector.gsheets.model.request.input.CreateWorksheet;
import io.camunda.connector.gsheets.supplier.GoogleSheetsServiceSupplier;
import io.camunda.google.model.Authentication;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CreateWorksheetOperationTest extends BaseTest {

  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  private Sheets service;

  @Captor private ArgumentCaptor<BatchUpdateSpreadsheetRequest> requestsCaptor;

  private Authentication auth;
  private BatchUpdateSpreadsheetResponse response;

  @BeforeEach
  public void before() {
    auth = new Authentication();
    response = new BatchUpdateSpreadsheetResponse();
    response.setSpreadsheetId(SPREADSHEET_ID);
  }

  @DisplayName("Should create worksheet in the end of worksheets")
  @Test
  void createWorksheet_shouldFormRequestWithoutIndex() throws IOException {
    // Given
    CreateWorksheet model = new CreateWorksheet(SPREADSHEET_ID, WORKSHEET_NAME, null);

    try (MockedStatic<GoogleSheetsServiceSupplier> mockedServiceSupplier =
        mockStatic(GoogleSheetsServiceSupplier.class)) {
      mockedServiceSupplier
          .when(() -> GoogleSheetsServiceSupplier.getGoogleSheetsService(any()))
          .thenReturn(service);
      when(service
              .spreadsheets()
              .batchUpdate(anyString(), requestsCaptor.capture())
              .setFields(any())
              .execute())
          .thenReturn(response);

      // When
      new CreateWorksheetOperation(model).execute(auth);

      // Then
      mockedServiceSupplier.verify(() -> GoogleSheetsServiceSupplier.getGoogleSheetsService(auth));

      List<Request> requests = requestsCaptor.getValue().getRequests();
      assertThat(requests, hasSize(1));
      SheetProperties properties = requests.get(0).getAddSheet().getProperties();
      assertEquals(WORKSHEET_NAME, properties.getTitle());
      assertNull(properties.getIndex());

      verify(service.spreadsheets().batchUpdate(anyString(), any()).setFields(any())).execute();
    }
  }

  @DisplayName("Should create worksheet in defined place")
  @Test
  void createWorksheet_shouldFormRequestWitIndex() throws IOException {
    // Given
    CreateWorksheet model = new CreateWorksheet(SPREADSHEET_ID, WORKSHEET_NAME, WORKSHEET_INDEX);

    try (MockedStatic<GoogleSheetsServiceSupplier> mockedServiceSupplier =
        mockStatic(GoogleSheetsServiceSupplier.class)) {
      mockedServiceSupplier
          .when(() -> GoogleSheetsServiceSupplier.getGoogleSheetsService(any()))
          .thenReturn(service);
      when(service
              .spreadsheets()
              .batchUpdate(anyString(), requestsCaptor.capture())
              .setFields(any())
              .execute())
          .thenReturn(response);

      // When
      new CreateWorksheetOperation(model).execute(auth);

      // Then
      mockedServiceSupplier.verify(() -> GoogleSheetsServiceSupplier.getGoogleSheetsService(auth));

      List<Request> requests = requestsCaptor.getValue().getRequests();
      assertThat(requests, hasSize(1));
      SheetProperties properties = requests.get(0).getAddSheet().getProperties();
      assertEquals(WORKSHEET_NAME, properties.getTitle());
      assertEquals(WORKSHEET_INDEX, properties.getIndex());

      verify(service.spreadsheets().batchUpdate(anyString(), any()).setFields(any())).execute();
    }
  }
}
