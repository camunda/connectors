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
import com.google.api.services.sheets.v4.model.ValueRange;
import io.camunda.connector.gsheets.BaseTest;
import io.camunda.connector.gsheets.model.request.impl.AddValues;
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
class AddValuesOperationTest extends BaseTest {

  public static final String CELL_ID = "C12";

  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  private Sheets service;

  private Authentication auth;

  @BeforeEach
  public void before() {
    auth = new Authentication();
  }

  @DisplayName("Should add value")
  @Test
  void addValue_shouldAddValue() throws IOException {
    // Given
    List<List<Object>> values = List.of(List.of(1));
    ValueRange valueRange = new ValueRange().setValues(values);

    AddValues model = new AddValues(SPREADSHEET_ID, WORKSHEET_NAME, CELL_ID, 1, null);

    try (MockedStatic<GoogleSheetsServiceSupplier> mockedServiceSupplier =
        mockStatic(GoogleSheetsServiceSupplier.class)) {
      mockedServiceSupplier
          .when(() -> GoogleSheetsServiceSupplier.getGoogleSheetsService(any()))
          .thenReturn(service);

      when(service
              .spreadsheets()
              .values()
              .update(anyString(), any(), any())
              .setValueInputOption(any())
              .execute())
          .thenReturn(null);

      // When
      new AddValuesOperation(model).execute(auth);

      // Then
      mockedServiceSupplier.verify(() -> GoogleSheetsServiceSupplier.getGoogleSheetsService(auth));
      verify(
              service
                  .spreadsheets()
                  .values()
                  .update(SPREADSHEET_ID, CELL_ID, valueRange)
                  .setValueInputOption("USER_ENTERED"))
          .execute();
    }
  }
}
