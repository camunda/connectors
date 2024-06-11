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
import static org.apache.http.entity.ContentType.APPLICATION_JSON;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchException;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import io.camunda.connector.api.error.ConnectorInputException;
import io.camunda.connector.api.json.ConnectorsObjectMapperSupplier;
import io.camunda.connector.http.base.model.HttpCommonResult;
import io.camunda.connector.http.graphql.model.GraphQLRequest;
import io.camunda.connector.http.graphql.utils.GraphQLRequestMapper;
import io.camunda.connector.test.outbound.OutboundConnectorContextBuilder;
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

  private GraphQLFunction functionUnderTest;

  private static Stream<String> successCases() throws IOException {
    return loadTestCasesFromResourceFile(SUCCESS_CASES_RESOURCE_PATH);
  }

  private static Stream<String> failCases() throws IOException {
    return loadTestCasesFromResourceFile(FAIL_CASES_RESOURCE_PATH);
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
            .secrets(name -> "foo")
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
        OutboundConnectorContextBuilder.create().variables(input).secrets(name -> "foo").build();
    final var expectedTimeInSeconds =
        objectMapper
            .readValue(
                objectMapper.readValue(input, ObjectNode.class).get("graphql").toString(),
                GraphQLRequest.GraphQL.class)
            .connectionTimeoutInSeconds();
    var graphQLRequestMapper =
        new GraphQLRequestMapper(ConnectorsObjectMapperSupplier.DEFAULT_MAPPER);

    // when
    var request =
        graphQLRequestMapper.toHttpCommonRequest(context.bindVariables(GraphQLRequest.class));
    // then
    assertThat(request.getConnectionTimeoutInSeconds()).isEqualTo(expectedTimeInSeconds);
  }

  private HttpCommonResult arrange(String input) throws Exception {
    final var context =
        OutboundConnectorContextBuilder.create().variables(input).secrets(name -> "foo").build();
    return (HttpCommonResult) functionUnderTest.execute(context);
  }
}
