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

package io.camunda.connector.gdrive.request;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.camunda.connector.api.ConnectorContext;
import io.camunda.connector.gdrive.BaseTest;
import io.camunda.connector.gdrive.model.request.GoogleDriveRequest;
import io.camunda.connector.test.ConnectorContextBuilder;
import java.io.IOException;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class GoogleDriveRequestTest extends BaseTest {
  private static final String SUCCESS_CASES_RESOURCE_PATH =
      "src/test/resources/requests/request-success-test-cases.json";
  private static final String FAIL_CASES_RESOURCE_PATH =
      "src/test/resources/requests/request-fail-test-cases.json";

  @DisplayName("Should replace all secrets data if variable start with 'secret.' ")
  @ParameterizedTest(name = "Executing test case # {index}")
  @MethodSource("successRequestCases")
  public void replaceSecrets_shouldReplaceAllSecrets(final String input) {
    // Given
    GoogleDriveRequest request = parseInput(input, GoogleDriveRequest.class);
    ConnectorContext context =
        ConnectorContextBuilder.create()
            .secret(SECRET_BEARER_TOKEN, ACTUAL_BEARER_TOKEN)
            .secret(SECRET_REFRESH_TOKEN, ACTUAL_REFRESH_TOKEN)
            .secret(SECRET_OAUTH_CLIENT_ID, ACTUAL_OAUTH_CLIENT_ID)
            .secret(SECRET_OAUTH_SECRET_ID, ACTUAL_OAUTH_SECRET_ID)
            .build();

    // When
    context.replaceSecrets(request);

    // Then
    // FIXME: move to enum
    if ("bearer".equals(request.getAuthentication().getAuthType())) {
      assertThat(request.getAuthentication().getBearerToken())
          .isNotNull()
          .isEqualTo(ACTUAL_BEARER_TOKEN);
    }

    if ("refresh".equals(request.getAuthentication().getAuthType())) {
      assertThat(request.getAuthentication().getOauthClientId())
          .isNotNull()
          .isEqualTo(ACTUAL_OAUTH_CLIENT_ID);
      assertThat(request.getAuthentication().getOauthClientSecret())
          .isNotNull()
          .isEqualTo(ACTUAL_OAUTH_SECRET_ID);
      assertThat(request.getAuthentication().getOauthRefreshToken())
          .isNotNull()
          .isEqualTo(ACTUAL_REFRESH_TOKEN);
    }
  }

  @DisplayName("Throw IllegalArgumentException when request without require fields")
  @ParameterizedTest(name = "Executing test case # {index}")
  @MethodSource("failRequestCases")
  public void validateWith_shouldThrowExceptionWhenNonExistLeastOneRequireField(
      final String input) {
    // Given
    GoogleDriveRequest request = parseInput(input, GoogleDriveRequest.class);
    ConnectorContext context = ConnectorContextBuilder.create().build();
    // When
    IllegalArgumentException thrown =
        assertThrows(IllegalArgumentException.class, () -> context.validate(request));
    // Then
    assertThat(thrown).isInstanceOf(IllegalArgumentException.class);
  }

  private static Stream<String> successRequestCases() throws IOException {
    return BaseTest.loadTestCasesFromResourceFile(SUCCESS_CASES_RESOURCE_PATH);
  }

  private static Stream<String> failRequestCases() throws IOException {
    return BaseTest.loadTestCasesFromResourceFile(FAIL_CASES_RESOURCE_PATH);
  }
}
