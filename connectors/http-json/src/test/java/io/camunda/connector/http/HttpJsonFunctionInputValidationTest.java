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
package io.camunda.connector.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.http.model.HttpJsonRequest;
import io.camunda.connector.impl.ConnectorInputException;
import io.camunda.connector.validation.impl.DefaultValidationProvider;
import java.io.IOException;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class HttpJsonFunctionInputValidationTest extends BaseTest {

  private static final String FAIL_REQUEST_CASES_PATH =
      "src/test/resources/requests/fail-cases-request-witout-one-requered-field.json";

  private static final String FAIL_CASES_TIMEOUT_CONNECTION_RESOURCE_PATH =
      "src/test/resources/requests/fail-cases-connection-timeout-validation.json";

  private static final String SUCCESS_CASES_TIMEOUT_CONNECTION_RESOURCE_PATH =
      "src/test/resources/requests/success-cases-connection-timeout-validation.json";

  private static final String REQUEST_METHOD_OBJECT_PLACEHOLDER =
      "{\n \"method\": \"%s\",\n \"url\": \"https://camunda.io/http-endpoint\"\n}";

  private static final String REQUEST_ENDPOINT_OBJECT_PLACEHOLDER =
      "{\n \"method\": \"get\",\n \"url\": \"%s\"\n}";

  private HttpJsonFunction functionUnderTest;

  @BeforeEach
  void setup() {
    functionUnderTest = new HttpJsonFunction();
  }

  @ParameterizedTest
  @ValueSource(strings = {"", " "})
  void shouldRaiseException_WhenExecuted_MethodMalformed(final String input) {
    var json = String.format(REQUEST_METHOD_OBJECT_PLACEHOLDER, input);
    // Given
    OutboundConnectorContext ctx =
        getContextBuilderWithSecrets()
            .variables(json)
            .validation(new DefaultValidationProvider())
            .build();

    // When
    Throwable exception =
        assertThrows(ConnectorInputException.class, () -> functionUnderTest.execute(ctx));

    // Then
    assertThat(exception.getMessage())
        .contains("Found constraints violated while validating input", "method");
  }

  @ParameterizedTest
  @ValueSource(strings = {"", " ", "iAmWrongUrl", "ftp://camunda.org/", "camunda@camunda.com"})
  void shouldRaiseException_WhenExecuted_EndpointMalformed(final String input) {
    // Given
    OutboundConnectorContext ctx =
        getContextBuilderWithSecrets()
            .variables(String.format(REQUEST_ENDPOINT_OBJECT_PLACEHOLDER, input))
            .validation(new DefaultValidationProvider())
            .build();
    // When
    Throwable exception =
        assertThrows(ConnectorInputException.class, () -> functionUnderTest.execute(ctx));
    // Then
    assertThat(exception.getMessage())
        .contains("Found constraints violated while validating input", "url");
  }

  @ParameterizedTest(name = "Validate null field # {index}")
  @MethodSource("failRequestCases")
  void validate_shouldThrowExceptionWhenLeastOneNotExistRequestField(String input) {
    // Given request without one required field
    OutboundConnectorContext context =
        getContextBuilderWithSecrets()
            .variables(input)
            .validation(new DefaultValidationProvider())
            .build();
    // When context.validate(request);
    // Then expect exception that one required field not set
    ConnectorInputException thrown =
        assertThrows(
            ConnectorInputException.class,
            () -> context.bindVariables(HttpJsonRequest.class),
            "ConnectorInputException was expected");
    assertThat(thrown.getMessage()).contains("Found constraints violated while validating input");
  }

  @ParameterizedTest(name = "Validate connectionTimeout # {index}")
  @MethodSource("failTimeOutConnectionCases")
  void validate_shouldThrowExceptionConnectionTimeoutIsWrong(String input) {
    // Given request without one required field
    OutboundConnectorContext context =
        getContextBuilderWithSecrets()
            .variables(input)
            .validation(new DefaultValidationProvider())
            .build();
    // When context.validate(request);
    // Then expect exception
    ConnectorInputException thrown =
        assertThrows(
            ConnectorInputException.class,
            () -> context.bindVariables(HttpJsonRequest.class),
            "ConnectorInputException was expected");
    assertThat(thrown.getMessage()).contains("Found constraints violated while validating input");
  }

  @ParameterizedTest(name = "Success validate connectionTimeout # {index}")
  @MethodSource("successTimeOutConnectionCases")
  void validate_shouldValidateWithoutException(String input) {
    // Given request without one required field
    OutboundConnectorContext context =
        getContextBuilderWithSecrets()
            .variables(input)
            .validation(new DefaultValidationProvider())
            .build();
    // When context.validate(request);
    // Then expect normal validate without exception
    context.bindVariables(HttpJsonRequest.class);
  }

  protected static Stream<String> failRequestCases() throws IOException {
    return loadTestCasesFromResourceFile(FAIL_REQUEST_CASES_PATH);
  }

  private static Stream<String> failTimeOutConnectionCases() throws IOException {
    return loadTestCasesFromResourceFile(FAIL_CASES_TIMEOUT_CONNECTION_RESOURCE_PATH);
  }

  private static Stream<String> successTimeOutConnectionCases() throws IOException {
    return loadTestCasesFromResourceFile(SUCCESS_CASES_TIMEOUT_CONNECTION_RESOURCE_PATH);
  }
}
