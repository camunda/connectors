/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.http.graphql;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.camunda.connector.api.error.ConnectorInputException;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.http.graphql.model.GraphQLRequest;
import io.camunda.connector.validation.impl.DefaultValidationProvider;
import java.io.IOException;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
    functionUnderTest = new GraphQLFunction();
  }

  @ParameterizedTest
  @ValueSource(strings = {"", " "})
  void shouldRaiseException_WhenExecuted_MethodMalformed(final String input) {
    // Given
    OutboundConnectorContext ctx =
        getContextBuilderWithSecrets()
            .validation(new DefaultValidationProvider())
            .variables(String.format(REQUEST_METHOD_OBJECT_PLACEHOLDER, input))
            .build();

    // When
    Throwable exception =
        assertThrows(RuntimeException.class, () -> functionUnderTest.execute(ctx));

    // Then
    assertThat(exception.getMessage()).contains("HttpMethod");
  }

  @ParameterizedTest
  @ValueSource(strings = {"iAmWrongUrl", "ftp://camunda.org/", "camunda@camunda.com"})
  void shouldRaiseException_WhenExecuted_EndpointMalformed(final String input) {
    // Given
    OutboundConnectorContext ctx =
        getContextBuilderWithSecrets()
            .validation(new DefaultValidationProvider())
            .variables(String.format(REQUEST_ENDPOINT_OBJECT_PLACEHOLDER, input))
            .build();
    // When
    Throwable exception =
        assertThrows(ConnectorInputException.class, () -> functionUnderTest.execute(ctx));
    // Then
    assertThat(exception.getMessage())
        .contains("Found constraints violated while validating input", "graphql.url");
  }

  /**
   * A blank URL is normalized to absent (see {@code GraphQLRequest.GraphQL}'s constructor), so it
   * is now reported as missing rather than malformed - the accurate diagnosis, and what lets a
   * bound credential supply the URL when the optional override is left empty.
   */
  @ParameterizedTest
  @ValueSource(strings = {"", " "})
  void shouldRaiseException_WhenExecuted_EndpointBlank(final String input) {
    // Given
    OutboundConnectorContext ctx =
        getContextBuilderWithSecrets()
            .validation(new DefaultValidationProvider())
            .variables(String.format(REQUEST_ENDPOINT_OBJECT_PLACEHOLDER, input))
            .build();
    // When
    Throwable exception =
        assertThrows(ConnectorInputException.class, () -> functionUnderTest.execute(ctx));
    // Then
    assertThat(exception.getMessage())
        .contains(
            "Found constraints violated while validating input",
            "No URL provided by the credential or the element template");
  }

  /**
   * An OAuth credential legitimately carries no URL (see {@code
   * RestAuthenticationConfiguration#carriesUrl}), so binding one with neither an inline URL nor an
   * override must fail with a message pointing at both possible sources, not a bare "URL is
   * required" that gives no hint where to provide it.
   */
  @Test
  void shouldRaiseException_WhenExecuted_OAuthCredentialCarriesNoUrlAndNoInlineOverride() {
    // Given
    String variables =
        """
        {
          "graphql": {
            "method": "get",
            "query": "query { field }"
          },
          "authenticationConfiguration": {
            "authentication": {
              "type": "oauth-client-credentials-flow",
              "oauthTokenEndpoint": "https://camunda.io/token",
              "clientId": "id",
              "clientSecret": "secret",
              "clientAuthentication": "credentialsBody"
            }
          }
        }
        """;
    OutboundConnectorContext ctx =
        getContextBuilderWithSecrets()
            .validation(new DefaultValidationProvider())
            .variables(variables)
            .build();

    // When
    Throwable exception =
        assertThrows(ConnectorInputException.class, () -> functionUnderTest.execute(ctx));

    // Then
    assertThat(exception.getMessage())
        .contains(
            "Found constraints violated while validating input",
            "No URL provided by the credential or the element template");
  }

  @ParameterizedTest(name = "Validate null field # {index}")
  @MethodSource("failRequestCases")
  void validate_shouldThrowExceptionWhenLeastOneNotExistRequestField(String input) {
    // Given request without one required field
    OutboundConnectorContext context =
        getContextBuilderWithSecrets()
            .validation(new DefaultValidationProvider())
            .variables(input)
            .build();
    // When context.validate(request);
    // Then expect exception that one required field not set
    ConnectorInputException thrown =
        assertThrows(
            ConnectorInputException.class,
            () -> context.bindVariables(GraphQLRequest.class),
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
    assertThrows(
        RuntimeException.class,
        () -> context.bindVariables(GraphQLRequest.class),
        "ConnectorInputException was expected");
  }

  protected static Stream<String> failRequestCases() throws IOException {
    return loadTestCasesFromResourceFile(FAIL_REQUEST_CASES_PATH);
  }

  private static Stream<String> failTimeOutConnectionCases() throws IOException {
    return loadTestCasesFromResourceFile(FAIL_CASES_TIMEOUT_CONNECTION_RESOURCE_PATH);
  }
}
