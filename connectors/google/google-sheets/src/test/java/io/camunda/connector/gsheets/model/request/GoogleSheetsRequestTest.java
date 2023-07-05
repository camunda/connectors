/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.gsheets.model.request;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.google.gson.Gson;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.gsheets.BaseTest;
import io.camunda.connector.gsheets.model.request.impl.AddValues;
import io.camunda.connector.gsheets.model.request.impl.CreateEmptyColumnOrRow;
import io.camunda.connector.gsheets.model.request.impl.CreateRow;
import io.camunda.connector.gsheets.model.request.impl.CreateSpreadsheet;
import io.camunda.connector.gsheets.model.request.impl.CreateWorksheet;
import io.camunda.connector.gsheets.model.request.impl.DeleteColumn;
import io.camunda.connector.gsheets.model.request.impl.DeleteWorksheet;
import io.camunda.connector.gsheets.model.request.impl.GetRowByIndex;
import io.camunda.connector.gsheets.model.request.impl.GetSpreadsheetDetails;
import io.camunda.connector.gsheets.model.request.impl.GetWorksheetData;
import io.camunda.connector.gsheets.supplier.GsonSheetsComponentSupplier;
import io.camunda.connector.impl.ConnectorInputException;
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

  private static final Gson GSON = GsonSheetsComponentSupplier.gsonInstance();

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
    if (authentication.getAuthType() == AuthenticationType.BEARER) {
      assertThat(authentication.getBearerToken()).isNotNull().isEqualTo(ACTUAL_BEARER_TOKEN);
    }

    if (authentication.getAuthType() == AuthenticationType.REFRESH) {
      assertThat(authentication.getOauthClientId()).isNotNull().isEqualTo(ACTUAL_OAUTH_CLIENT_ID);
      assertThat(authentication.getOauthClientSecret())
          .isNotNull()
          .isEqualTo(ACTUAL_OAUTH_SECRET_ID);
      assertThat(authentication.getOauthRefreshToken()).isNotNull().isEqualTo(ACTUAL_REFRESH_TOKEN);
    }
  }

  private static void verifyOperationInput(Input input) {
    if (input instanceof CreateSpreadsheet operationDetails) {
      assertThat(operationDetails.getParent()).isNotNull().isEqualTo(ACTUAl_PARENT);
      assertThat(operationDetails.getSpreadsheetName()).isNotNull().isEqualTo(SPREADSHEET_NAME);
    } else if (input instanceof CreateWorksheet operationDetails) {
      assertThat(operationDetails.getSpreadsheetId()).isNotNull().isEqualTo(SPREADSHEET_ID);
      assertThat(operationDetails.getWorksheetName()).isNotNull().isEqualTo(WORKSHEET_NAME);
    } else if (input instanceof GetSpreadsheetDetails operationDetails) {
      assertThat(operationDetails.getSpreadsheetId()).isNotNull().isEqualTo(SPREADSHEET_ID);
    } else if (input instanceof DeleteWorksheet operationDetails) {
      assertThat(operationDetails.getSpreadsheetId()).isNotNull().isEqualTo(SPREADSHEET_ID);
    } else if (input instanceof AddValues operationDetails) {
      assertThat(operationDetails.getSpreadsheetId()).isNotNull().isEqualTo(SPREADSHEET_ID);
      assertThat(operationDetails.getWorksheetName()).isNotNull().isEqualTo(WORKSHEET_NAME);
      assertThat(operationDetails.getCellId()).isNotNull().isEqualTo(ACTUAL_CELL_ID);
      assertThat(operationDetails.getValue()).isNotNull().isEqualTo(ACTUAL_CELL_VALUE);
    } else if (input instanceof CreateRow operationDetails) {
      assertThat(operationDetails.getSpreadsheetId()).isNotNull().isEqualTo(SPREADSHEET_ID);
      assertThat(operationDetails.getWorksheetName()).isNotNull().isEqualTo(WORKSHEET_NAME);
      assertThat(operationDetails.getValues()).isNotNull().isEqualTo(List.of(ACTUAL_ROW));
    } else if (input instanceof GetRowByIndex operationDetails) {
      assertThat(operationDetails.getSpreadsheetId()).isNotNull().isEqualTo(SPREADSHEET_ID);
      assertThat(operationDetails.getWorksheetName()).isNotNull().isEqualTo(WORKSHEET_NAME);
    } else if (input instanceof GetWorksheetData operationDetails) {
      assertThat(operationDetails.getSpreadsheetId()).isNotNull().isEqualTo(SPREADSHEET_ID);
      assertThat(operationDetails.getWorksheetName()).isNotNull().isEqualTo(WORKSHEET_NAME);
    } else if (input instanceof DeleteColumn operationDetails) {
      assertThat(operationDetails.getSpreadsheetId()).isNotNull().isEqualTo(SPREADSHEET_ID);
    } else if (input instanceof CreateEmptyColumnOrRow operationDetails) {
      assertThat(operationDetails.getSpreadsheetId()).isNotNull().isEqualTo(SPREADSHEET_ID);
    } else {
      throw new UnsupportedOperationException("Unsupported operation : [" + input.getClass() + "]");
    }
  }
}
