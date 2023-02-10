/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.graphql;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.graphql.model.GraphQLRequest;
import io.camunda.connector.graphql.utils.JsonSerializeHelper;
import io.camunda.connector.impl.ConnectorInputException;
import io.camunda.connector.test.outbound.OutboundConnectorContextBuilder;
import java.io.IOException;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class GraphQLFunctionInputValidationTest extends BaseTest {

  private static final String FAIL_REQUEST_CASES_PATH =
      "src/test/resources/requests/fail-cases-request-without-one-required-field.json";

  private static final String FAIL_CASES_TIMEOUT_CONNECTION_RESOURCE_PATH =
      "src/test/resources/requests/fail-cases-connection-timeout-validation.json";

  private static final String SUCCESS_CASES_TIMEOUT_CONNECTION_RESOURCE_PATH =
      "src/test/resources/requests/success-cases-connection-timeout-validation.json";

  private static final String REQUEST_METHOD_OBJECT_PLACEHOLDER =
      "{\"graphql\":{\n \"method\": \"%s\",\n \"url\": \"https://camunda.io/http-endpoint\"\n}}";

  private static final String REQUEST_ENDPOINT_OBJECT_PLACEHOLDER =
      "{\"graphql\":{\n \"method\": \"get\",\n \"url\": \"%s\"\n}}";

  private GraphQLFunction functionUnderTest;

  @BeforeEach
  void setup() {
    functionUnderTest = new GraphQLFunction(null);
  }

  @ParameterizedTest
  @ValueSource(strings = {"", " ", "\r\n"})
  void shouldRaiseException_WhenExecuted_MethodMalformed(final String input) {
    // Given
    OutboundConnectorContext ctx =
        OutboundConnectorContextBuilder.create()
            .variables(String.format(REQUEST_METHOD_OBJECT_PLACEHOLDER, input))
            .build();

    // When
    Throwable exception =
        assertThrows(ConnectorInputException.class, () -> functionUnderTest.execute(ctx));

    // Then
    assertThat(exception.getMessage())
        .contains("Found constraints violated while validating input", "method: must not be blank");
  }

  @ParameterizedTest
  @ValueSource(strings = {"", " ", "iAmWrongUrl", "ftp://camunda.org/", "camunda@camunda.com"})
  void shouldRaiseException_WhenExecuted_EndpointMalformed(final String input) {
    // Given
    OutboundConnectorContext ctx =
        OutboundConnectorContextBuilder.create()
            .variables(String.format(REQUEST_ENDPOINT_OBJECT_PLACEHOLDER, input))
            .build();
    // When
    Throwable exception =
        assertThrows(ConnectorInputException.class, () -> functionUnderTest.execute(ctx));
    // Then
    assertThat(exception.getMessage())
        .contains(
            "Found constraints violated while validating input",
            "must match \"^(http://|https://|secrets).*$\"");
  }

  @ParameterizedTest(name = "Validate null field # {index}")
  @MethodSource("failRequestCases")
  void validate_shouldThrowExceptionWhenLeastOneNotExistRequestField(String input) {
    // Given request without one required field
    GraphQLRequest httpJsonRequest = JsonSerializeHelper.serializeRequest(gson, input);
    OutboundConnectorContext context =
        OutboundConnectorContextBuilder.create().variables(httpJsonRequest).build();
    // When context.validate(request);
    // Then expect exception that one required field not set
    ConnectorInputException thrown =
        assertThrows(
            ConnectorInputException.class,
            () -> context.validate(httpJsonRequest),
            "ConnectorInputException was expected");
    assertThat(thrown.getMessage()).contains("Found constraints violated while validating input");
  }

  @ParameterizedTest(name = "Validate connectionTimeout # {index}")
  @MethodSource("failTimeOutConnectionCases")
  void validate_shouldThrowExceptionConnectionTimeoutIsWrong(String input) {
    // Given request without one required field
    GraphQLRequest httpJsonRequest = JsonSerializeHelper.serializeRequest(gson, input);
    OutboundConnectorContext context =
        OutboundConnectorContextBuilder.create().variables(httpJsonRequest).build();
    // When context.validate(request);
    // Then expect exception
    ConnectorInputException thrown =
        assertThrows(
            ConnectorInputException.class,
            () -> context.validate(httpJsonRequest),
            "ConnectorInputException was expected");
    assertThat(thrown.getMessage()).contains("Found constraints violated while validating input");
  }

  @ParameterizedTest(name = "Success validate connectionTimeout # {index}")
  @MethodSource("successTimeOutConnectionCases")
  void validate_shouldValidateWithoutException(String input) {
    // Given request without one required field
    GraphQLRequest httpJsonRequest = JsonSerializeHelper.serializeRequest(gson, input);
    OutboundConnectorContext context =
        OutboundConnectorContextBuilder.create().variables(httpJsonRequest).build();
    // When context.validate(request);
    // Then expect normal validate without exception
    context.validate(httpJsonRequest);
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
