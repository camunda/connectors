/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.gsheets.operation.impl;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.model.ValueRange;
import io.camunda.connector.gsheets.BaseTest;
import io.camunda.connector.gsheets.model.request.impl.GetWorksheetData;
import io.camunda.connector.gsheets.model.response.GoogleSheetsResult;
import io.camunda.connector.gsheets.supplier.GoogleSheetsServiceSupplier;
import io.camunda.google.model.Authentication;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GetWorksheetDataOperationTest extends BaseTest {

  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  private Sheets service;

  private Authentication auth;

  @BeforeEach
  public void before() {
    auth = new Authentication();
  }

  @DisplayName("Should get worksheet data")
  @Test
  void getWorksheetData_shouldGetWorksheetData() throws IOException {
    // Given
    GetWorksheetData model = new GetWorksheetData(SPREADSHEET_ID, WORKSHEET_NAME);
    List<List<Object>> values = List.of(List.of());
    ValueRange valueRange = new ValueRange().setValues(values);

    try (MockedStatic<GoogleSheetsServiceSupplier> mockedServiceSupplier =
        mockStatic(GoogleSheetsServiceSupplier.class)) {
      mockedServiceSupplier
          .when(() -> GoogleSheetsServiceSupplier.getGoogleSheetsService(any()))
          .thenReturn(service);

      when(service.spreadsheets().values().get(anyString(), anyString()).execute())
          .thenReturn(valueRange);

      // When
      Object result = new GetWorksheetDataOperation(model).execute(auth);

      // Then
      mockedServiceSupplier.verify(() -> GoogleSheetsServiceSupplier.getGoogleSheetsService(auth));

      assertThat(result, instanceOf(GoogleSheetsResult.class));
      Object data = ((GoogleSheetsResult) result).getResponse();
      assertEquals(values, data);

      verify(service.spreadsheets().values().get(SPREADSHEET_ID, WORKSHEET_NAME)).execute();
    }
  }
}
