/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.gdrive;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.ArgumentMatchers.any;

import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.gdrive.model.GoogleDriveResult;
import io.camunda.connector.gdrive.model.request.GoogleDriveRequest;
import io.camunda.connector.gdrive.model.request.Resource;
import io.camunda.connector.test.outbound.OutboundConnectorContextBuilder;
import io.camunda.google.supplier.GsonComponentSupplier;
import java.io.IOException;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mockito;

class GoogleDriveFunctionTest extends BaseTest {

  private static final String SUCCESS_CASES_RESOURCE_PATH =
      "src/test/resources/requests/request-success-test-cases.json";

  @DisplayName("Should execute connector and return success result")
  @ParameterizedTest(name = "Executing test case # {index}")
  @MethodSource("successRequestCases")
  void execute_shouldExecuteAndReturnResultWhenGiveContext(String input) {
    // Given
    GoogleDriveService googleDriveServiceMock = Mockito.mock(GoogleDriveService.class);
    GoogleDriveFunction service =
        new GoogleDriveFunction(
            googleDriveServiceMock, GsonComponentSupplier.gsonFactoryInstance());

    OutboundConnectorContext context =
        OutboundConnectorContextBuilder.create()
            .variables(input)
            .secret(SECRET_BEARER_TOKEN, ACTUAL_BEARER_TOKEN)
            .secret(SECRET_REFRESH_TOKEN, ACTUAL_REFRESH_TOKEN)
            .secret(SECRET_OAUTH_CLIENT_ID, ACTUAL_OAUTH_CLIENT_ID)
            .secret(SECRET_OAUTH_SECRET_ID, ACTUAL_OAUTH_SECRET_ID)
            .build();

    GoogleDriveRequest request = parseInput(input, GoogleDriveRequest.class);

    context.replaceSecrets(request);

    GoogleDriveResult googleDriveResult = new GoogleDriveResult();
    googleDriveResult.setGoogleDriveResourceId(FILE_ID);
    googleDriveResult.setGoogleDriveResourceUrl(FILE_URL);

    Mockito.when(googleDriveServiceMock.execute(any(GoogleDriveClient.class), any(Resource.class)))
        .thenReturn(googleDriveResult);
    // When
    Object execute = service.execute(context);
    // Then
    assertThat(execute).isInstanceOf(GoogleDriveResult.class);
    assertThat(((GoogleDriveResult) execute).getGoogleDriveResourceId()).isEqualTo(FILE_ID);
    assertThat(((GoogleDriveResult) execute).getGoogleDriveResourceUrl()).isEqualTo(FILE_URL);
  }

  private static Stream<String> successRequestCases() throws IOException {
    return BaseTest.loadTestCasesFromResourceFile(SUCCESS_CASES_RESOURCE_PATH);
  }
}
