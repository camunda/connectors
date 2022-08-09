/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information regarding copyright
 * ownership. Camunda licenses this file to you under the Apache License,
 * Version 2.0; you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.camunda.connector.gdrive;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.ArgumentMatchers.any;

import io.camunda.connector.api.ConnectorContext;
import io.camunda.connector.gdrive.model.GoogleDriveResult;
import io.camunda.connector.gdrive.model.request.GoogleDriveRequest;
import io.camunda.connector.gdrive.model.request.Resource;
import io.camunda.connector.gdrive.supliers.GsonComponentSupplier;
import io.camunda.connector.test.ConnectorContextBuilder;
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
  public void execute_shouldExecuteAndReturnResultWhenGiveContext(String input) {
    // Given
    GoogleDriveService googleDriveServiceMock = Mockito.mock(GoogleDriveService.class);
    GoogleDriveFunction service =
        new GoogleDriveFunction(googleDriveServiceMock, GsonComponentSupplier.getJsonFactory());

    ConnectorContext context =
        ConnectorContextBuilder.create()
            .variables(input)
            .secret(SECRET_TOKEN, ACTUAL_TOKEN)
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
