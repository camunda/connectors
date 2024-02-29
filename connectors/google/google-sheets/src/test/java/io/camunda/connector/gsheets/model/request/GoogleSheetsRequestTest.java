/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.gsheets.model.request;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.camunda.connector.api.error.ConnectorInputException;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.gsheets.BaseTest;
import io.camunda.connector.gsheets.model.request.input.AddValues;
import io.camunda.connector.gsheets.model.request.input.CreateEmptyColumnOrRow;
import io.camunda.connector.gsheets.model.request.input.CreateRow;
import io.camunda.connector.gsheets.model.request.input.CreateSpreadsheet;
import io.camunda.connector.gsheets.model.request.input.CreateWorksheet;
import io.camunda.connector.gsheets.model.request.input.DeleteColumn;
import io.camunda.connector.gsheets.model.request.input.DeleteWorksheet;
import io.camunda.connector.gsheets.model.request.input.GetRowByIndex;
import io.camunda.connector.gsheets.model.request.input.GetSpreadsheetDetails;
import io.camunda.connector.gsheets.model.request.input.GetWorksheetData;
import io.camunda.connector.gsheets.model.request.input.Input;
import io.camunda.connector.test.outbound.OutboundConnectorContextBuilder;
import io.camunda.google.model.Authentication;
import io.camunda.google.model.AuthenticationType;
import java.io.IOException;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class GoogleSheetsRequestTest extends BaseTest {

  private static final String SUCCESS_CASES_RESOURCE_PATH =
      "src/test/resources/requests/request-success-test-cases.json";
  private static final String FAIL_CASES_RESOURCE_PATH =
      "src/test/resources/requests/request-fail-test-cases.json";

  private static Stream<String> successRequestCases() throws IOException {
    return BaseTest.loadTestCasesFromResourceFile(SUCCESS_CASES_RESOURCE_PATH);
  }

  private static Stream<String> failRequestCases() throws IOException {
    return BaseTest.loadTestCasesFromResourceFile(FAIL_CASES_RESOURCE_PATH);
  }

  @DisplayName("Should replace all secrets data if variable start with 'secret.' ")
  @ParameterizedTest(name = "Executing test case # {index}")
  @MethodSource("successRequestCases")
  void replaceSecrets_shouldReplaceAllSecrets(final String input) {
    // Given
    OutboundConnectorContext context = buildContext(input);

    // When
    var request = context.bindVariables(GoogleSheetsRequest.class);

    // Then
    verifyAuthentication(request.getAuthentication());
    verifyOperationInput(request.getOperation());
  }

  @DisplayName("Throw IllegalArgumentException when request without require fields")
  @ParameterizedTest(name = "Executing test case # {index}")
  @MethodSource("failRequestCases")
  void validateWith_shouldThrowExceptionWhenNonExistLeastOneRequireField(final String input) {
    // Given
    OutboundConnectorContext context = buildContext(input);
    // When and Then
    ConnectorInputException thrown =
        assertThrows(
            ConnectorInputException.class, () -> context.bindVariables(GoogleSheetsRequest.class));
    assertThat(thrown.getMessage()).contains("Found constraints violated while validating input:");
  }

  private static OutboundConnectorContext buildContext(String input) {
    return OutboundConnectorContextBuilder.create()
        .secret(SECRET_BEARER_TOKEN, ACTUAL_BEARER_TOKEN)
        .secret(SECRET_REFRESH_TOKEN, ACTUAL_REFRESH_TOKEN)
        .secret(SECRET_OAUTH_CLIENT_ID, ACTUAL_OAUTH_CLIENT_ID)
        .secret(SECRET_OAUTH_SECRET_ID, ACTUAL_OAUTH_SECRET_ID)
        .secret(SECRET_PARENT, ACTUAl_PARENT)
        .secret(SECRET_SPREADSHEET_NAME, SPREADSHEET_NAME)
        .secret(SECRET_SPREADSHEET_ID, SPREADSHEET_ID)
        .secret(SECRET_WORKSHEET_NAME, WORKSHEET_NAME)
        .secret(SECRET_CELL_ID, ACTUAL_CELL_ID)
        .secret(SECRET_CELL_VALUE, ACTUAL_CELL_VALUE)
        .secret(SECRET_ROW, ACTUAL_ROW)
        .variables(input)
        .build();
  }

  private static void verifyAuthentication(Authentication authentication) {
    if (authentication.authType() == AuthenticationType.BEARER) {
      assertThat(authentication.bearerToken()).isNotNull().isEqualTo(ACTUAL_BEARER_TOKEN);
    }

    if (authentication.authType() == AuthenticationType.REFRESH) {
      assertThat(authentication.oauthClientId()).isNotNull().isEqualTo(ACTUAL_OAUTH_CLIENT_ID);
      assertThat(authentication.oauthClientSecret()).isNotNull().isEqualTo(ACTUAL_OAUTH_SECRET_ID);
      assertThat(authentication.oauthRefreshToken()).isNotNull().isEqualTo(ACTUAL_REFRESH_TOKEN);
    }
  }

  private static void verifyOperationInput(Input input) {
    if (input instanceof CreateSpreadsheet operationDetails) {
      assertThat(operationDetails.parent()).isNotNull().isEqualTo(ACTUAl_PARENT);
      assertThat(operationDetails.spreadsheetName()).isNotNull().isEqualTo(SPREADSHEET_NAME);
    } else if (input instanceof CreateWorksheet operationDetails) {
      assertThat(operationDetails.spreadsheetId()).isNotNull().isEqualTo(SPREADSHEET_ID);
      assertThat(operationDetails.worksheetName()).isNotNull().isEqualTo(WORKSHEET_NAME);
    } else if (input instanceof GetSpreadsheetDetails operationDetails) {
      assertThat(operationDetails.spreadsheetId()).isNotNull().isEqualTo(SPREADSHEET_ID);
    } else if (input instanceof DeleteWorksheet operationDetails) {
      assertThat(operationDetails.spreadsheetId()).isNotNull().isEqualTo(SPREADSHEET_ID);
    } else if (input instanceof AddValues operationDetails) {
      assertThat(operationDetails.spreadsheetId()).isNotNull().isEqualTo(SPREADSHEET_ID);
      assertThat(operationDetails.worksheetName()).isNotNull().isEqualTo(WORKSHEET_NAME);
      assertThat(operationDetails.cellId()).isNotNull().isEqualTo(ACTUAL_CELL_ID);
      assertThat(operationDetails.value()).isNotNull().isEqualTo(ACTUAL_CELL_VALUE);
    } else if (input instanceof CreateRow operationDetails) {
      assertThat(operationDetails.spreadsheetId()).isNotNull().isEqualTo(SPREADSHEET_ID);
      assertThat(operationDetails.worksheetName()).isNotNull().isEqualTo(WORKSHEET_NAME);
      assertThat(operationDetails.values()).isNotNull().isEqualTo(List.of(ACTUAL_ROW));
    } else if (input instanceof GetRowByIndex operationDetails) {
      assertThat(operationDetails.spreadsheetId()).isNotNull().isEqualTo(SPREADSHEET_ID);
      assertThat(operationDetails.worksheetName()).isNotNull().isEqualTo(WORKSHEET_NAME);
    } else if (input instanceof GetWorksheetData operationDetails) {
      assertThat(operationDetails.spreadsheetId()).isNotNull().isEqualTo(SPREADSHEET_ID);
      assertThat(operationDetails.worksheetName()).isNotNull().isEqualTo(WORKSHEET_NAME);
    } else if (input instanceof DeleteColumn operationDetails) {
      assertThat(operationDetails.spreadsheetId()).isNotNull().isEqualTo(SPREADSHEET_ID);
    } else if (input instanceof CreateEmptyColumnOrRow operationDetails) {
      assertThat(operationDetails.spreadsheetId()).isNotNull().isEqualTo(SPREADSHEET_ID);
    } else {
      throw new UnsupportedOperationException("Unsupported operation : [" + input.getClass() + "]");
    }
  }
}
