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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.model.AppendDimensionRequest;
import com.google.api.services.sheets.v4.model.BatchUpdateSpreadsheetRequest;
import com.google.api.services.sheets.v4.model.BatchUpdateSpreadsheetResponse;
import com.google.api.services.sheets.v4.model.DimensionRange;
import com.google.api.services.sheets.v4.model.InsertDimensionRequest;
import com.google.api.services.sheets.v4.model.Request;
import io.camunda.connector.gsheets.BaseTest;
import io.camunda.connector.gsheets.model.request.Dimension;
import io.camunda.connector.gsheets.model.request.impl.CreateEmptyColumnOrRow;
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
class CreateEmptyColumnOrRowOperationTest extends BaseTest {
  public static final int START_INDEX = 1;
  public static final int END_INDEX = 3;

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

  @DisplayName("Should append empty row")
  @Test
  void createEmptyColumnOrRow_shouldAppendEmptyRow() throws IOException {
    // Given
    CreateEmptyColumnOrRow model =
        new CreateEmptyColumnOrRow(SPREADSHEET_ID, WORKSHEET_ID, Dimension.ROWS, null, null, null);

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
      new CreateEmptyColumnOrRowOperation(model).execute(auth);

      // Then
      mockedServiceSupplier.verify(() -> GoogleSheetsServiceSupplier.getGoogleSheetsService(auth));

      List<Request> requests = requestsCaptor.getValue().getRequests();
      assertThat(requests, hasSize(1));
      Request request = requests.get(0);
      AppendDimensionRequest appendDimension = request.getAppendDimension();

      assertNull(request.getInsertDimension());
      assertNotNull(appendDimension);

      assertEquals(WORKSHEET_ID, appendDimension.getSheetId());
      assertEquals(Dimension.ROWS.getValue(), appendDimension.getDimension());
      assertEquals(1, appendDimension.getLength());

      verify(service.spreadsheets().batchUpdate(anyString(), any()).setFields(any())).execute();
    }
  }

  @DisplayName("Should insert empty column")
  @Test
  void createEmptyColumnOrRow_shouldInsertEmptyColumn() throws IOException {
    // Given
    CreateEmptyColumnOrRow model =
        new CreateEmptyColumnOrRow(
            SPREADSHEET_ID, WORKSHEET_ID, Dimension.COLUMNS, START_INDEX, END_INDEX, null);

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
      new CreateEmptyColumnOrRowOperation(model).execute(auth);

      // Then
      mockedServiceSupplier.verify(() -> GoogleSheetsServiceSupplier.getGoogleSheetsService(auth));

      List<Request> requests = requestsCaptor.getValue().getRequests();
      assertThat(requests, hasSize(1));
      Request request = requests.get(0);
      InsertDimensionRequest insertDimension = request.getInsertDimension();

      assertNull(request.getAppendDimension());
      assertNotNull(insertDimension);

      DimensionRange range = insertDimension.getRange();
      assertEquals(WORKSHEET_ID, range.getSheetId());
      assertEquals(Dimension.COLUMNS.getValue(), range.getDimension());
      assertEquals(START_INDEX, range.getStartIndex());
      assertEquals(END_INDEX, range.getEndIndex());

      verify(service.spreadsheets().batchUpdate(anyString(), any()).setFields(any())).execute();
    }
  }

  @DisplayName("Should throw exception when start index is empty while end one is not")
  @Test
  void createEmptyColumnOrRow_shouldThrowExceptionWhenStartIndexIsEmpty() {
    // Given
    CreateEmptyColumnOrRow model =
        new CreateEmptyColumnOrRow(
            SPREADSHEET_ID, WORKSHEET_ID, Dimension.ROWS, null, END_INDEX, null);

    try (MockedStatic<GoogleSheetsServiceSupplier> mockedServiceSupplier =
        mockStatic(GoogleSheetsServiceSupplier.class)) {
      mockedServiceSupplier
          .when(() -> GoogleSheetsServiceSupplier.getGoogleSheetsService(any()))
          .thenReturn(service);

      CreateEmptyColumnOrRowOperation operation = new CreateEmptyColumnOrRowOperation(model);
      // When and Then

      assertThrows(IllegalArgumentException.class, () -> operation.execute(auth));
      mockedServiceSupplier.verifyNoInteractions();
    }
  }

  @DisplayName("Should throw exception when end index is empty while start one is not")
  @Test
  void createEmptyColumnOrRow_shouldThrowExceptionWhenEndIndexIsEmpty() {
    // Given
    CreateEmptyColumnOrRow model =
        new CreateEmptyColumnOrRow(
            SPREADSHEET_ID, WORKSHEET_ID, Dimension.COLUMNS, START_INDEX, null, null);

    try (MockedStatic<GoogleSheetsServiceSupplier> mockedServiceSupplier =
        mockStatic(GoogleSheetsServiceSupplier.class)) {
      mockedServiceSupplier
          .when(() -> GoogleSheetsServiceSupplier.getGoogleSheetsService(any()))
          .thenReturn(service);

      CreateEmptyColumnOrRowOperation operation = new CreateEmptyColumnOrRowOperation(model);

      // When and Then
      assertThrows(IllegalArgumentException.class, () -> operation.execute(auth));
      mockedServiceSupplier.verifyNoInteractions();
    }
  }
}
