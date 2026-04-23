/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.http.graphql;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.any;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static io.camunda.connector.http.graphql.GraphQLFunction.GRAPHQL_ERROR_CODE;
import static org.apache.http.entity.ContentType.APPLICATION_JSON;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchException;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import io.camunda.connector.api.error.ConnectorException;
import io.camunda.connector.api.error.ConnectorInputException;
import io.camunda.connector.api.secret.SecretContext;
import io.camunda.connector.api.secret.SecretProvider;
import io.camunda.connector.http.base.model.HttpCommonResult;
import io.camunda.connector.http.graphql.model.GraphQLRequest;
import io.camunda.connector.http.graphql.utils.GraphQLRequestMapper;
import io.camunda.connector.jackson.ConnectorsObjectMapperSupplier;
import io.camunda.connector.runtime.test.outbound.OutboundConnectorContextBuilder;
import io.camunda.connector.validation.impl.DefaultValidationProvider;
import java.io.IOException;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.junit.jupiter.MockitoExtension;

@WireMockTest(httpPort = 8085)
@ExtendWith(MockitoExtension.class)
public class GraphQLFunctionTest extends BaseTest {

  private static final String SUCCESS_CASES_RESOURCE_PATH =
      "src/test/resources/requests/success-test-cases.json";
  private static final String FAIL_CASES_RESOURCE_PATH =
      "src/test/resources/requests/fail-test-cases.json";
  private static final String GRAPHQL_200_WITH_ERRORS_CASES_RESOURCE_PATH =
      "src/test/resources/requests/graphql-200-with-errors-test-cases.json";

  private GraphQLFunction functionUnderTest;

  private static Stream<String> successCases() throws IOException {
    return loadTestCasesFromResourceFile(SUCCESS_CASES_RESOURCE_PATH);
  }

  private static Stream<String> failCases() throws IOException {
    return loadTestCasesFromResourceFile(FAIL_CASES_RESOURCE_PATH);
  }

  private static Stream<String> graphql200WithErrorsCases() throws IOException {
    return loadTestCasesFromResourceFile(GRAPHQL_200_WITH_ERRORS_CASES_RESOURCE_PATH);
  }

  @BeforeEach
  public void setup() {
    functionUnderTest = new GraphQLFunction();
    stubFor(
        any(urlPathEqualTo("/http-endpoint"))
            .willReturn(aResponse().withHeader("Content-Type", "application/json")));
  }

  @ParameterizedTest(name = "Executing test case: {0}")
  @MethodSource("successCases")
  void shouldReturnResult_WhenExecuted(final String input) throws Exception {
    // given - minimal required entity
    var functionCallResponseAsObject = arrange(input);

    // then
    assertThat(functionCallResponseAsObject.headers())
        .containsValue(APPLICATION_JSON.getMimeType());
  }

  @ParameterizedTest(name = "Executing test case: {0}")
  @MethodSource("failCases")
  void shouldReturnFallbackResult_WhenMalformedRequest(final String input) {
    final var context =
        OutboundConnectorContextBuilder.create()
            .variables(input)
            .validation(new DefaultValidationProvider())
            .secrets(new StaticSecretProvider("foo"))
            .build();

    // when
    var exceptionThrown = catchException(() -> functionUnderTest.execute(context));

    // then
    assertThat(exceptionThrown)
        .isInstanceOf(ConnectorInputException.class)
        .hasMessageContaining("ValidationException");
  }

  @ParameterizedTest(name = "Executing test case: {0}")
  @MethodSource("successCases")
  void execute_shouldSetConnectTime(final String input) throws Exception {
    // given - minimal required entity
    final var context =
        OutboundConnectorContextBuilder.create()
            .variables(input)
            .secrets(new StaticSecretProvider("foo"))
            .build();
    final var expectedTimeInSeconds =
        objectMapper
            .readValue(
                objectMapper.readValue(input, ObjectNode.class).get("graphql").toString(),
                GraphQLRequest.GraphQL.class)
            .connectionTimeoutInSeconds();
    var graphQLRequestMapper = new GraphQLRequestMapper(ConnectorsObjectMapperSupplier.getCopy());

    // when
    var request =
        graphQLRequestMapper.toHttpCommonRequest(context.bindVariables(GraphQLRequest.class));
    // then
    assertThat(request.getConnectionTimeoutInSeconds()).isEqualTo(expectedTimeInSeconds);
  }

  @ParameterizedTest(name = "Executing test case: {0}")
  @MethodSource("graphql200WithErrorsCases")
  void shouldThrowConnectorException_WhenResponseContains200WithErrors(final String input) {
    stubFor(
        any(urlPathEqualTo("/http-endpoint"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        "{\"data\":null,\"errors\":[{\"message\":\"Cannot query field 'unknownField' on type 'Query'\"}]}")));

    final var context =
        OutboundConnectorContextBuilder.create()
            .variables(input)
            .secrets(new StaticSecretProvider("foo"))
            .build();

    assertThatThrownBy(() -> functionUnderTest.execute(context))
        .isInstanceOf(ConnectorException.class)
        .satisfies(
            ex -> {
              var ce = (ConnectorException) ex;
              assertThat(ce.getErrorCode()).isEqualTo(GRAPHQL_ERROR_CODE);
              assertThat(ce.getMessage())
                  .isEqualTo("Cannot query field 'unknownField' on type 'Query'");
              @SuppressWarnings("unchecked")
              var response = (java.util.Map<String, Object>) ce.getErrorVariables().get("response");
              assertThat(response).containsKey("body");
              assertThat(response).containsKey("headers");
            });
  }

  @ParameterizedTest(name = "Executing test case: {0}")
  @MethodSource("graphql200WithErrorsCases")
  void shouldReturnResult_WhenResponseContains200WithOnlyData(final String input) {
    stubFor(
        any(urlPathEqualTo("/http-endpoint"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody("{\"data\":{\"user\":{\"id\":\"1\",\"name\":\"Alice\"}}}")));

    final var context =
        OutboundConnectorContextBuilder.create()
            .variables(input)
            .secrets(new StaticSecretProvider("foo"))
            .build();

    var result = functionUnderTest.execute(context);
    assertThat(result).isInstanceOf(HttpCommonResult.class);
  }

  private HttpCommonResult arrange(String input) throws Exception {
    final var context =
        OutboundConnectorContextBuilder.create()
            .variables(input)
            .secrets(new StaticSecretProvider("foo"))
            .build();
    return (HttpCommonResult) functionUnderTest.execute(context);
  }

  private record StaticSecretProvider(String secret) implements SecretProvider {

    @Override
    public String getSecret(String name, SecretContext context) {
      return secret;
    }
  }
}
