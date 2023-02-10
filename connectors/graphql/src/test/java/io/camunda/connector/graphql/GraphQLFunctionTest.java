/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.graphql;

import static org.apache.http.entity.ContentType.APPLICATION_JSON;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpContent;
import com.google.api.client.http.HttpHeaders;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpRequestFactory;
import com.google.api.client.http.HttpResponse;
import com.google.api.client.http.HttpResponseException;
import com.google.gson.JsonObject;
import io.camunda.connector.api.error.ConnectorException;
import io.camunda.connector.graphql.model.GraphQLRequest;
import io.camunda.connector.graphql.model.GraphQLResult;
import io.camunda.connector.impl.ConnectorInputException;
import io.camunda.connector.test.outbound.OutboundConnectorContextBuilder;
import java.io.IOException;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class GraphQLFunctionTest extends BaseTest {

  private static final String SUCCESS_CASES_RESOURCE_PATH =
      "src/test/resources/requests/success-test-cases.json";

  private static final String SUCCESS_CASES_OAUTH_RESOURCE_PATH =
      "src/test/resources/requests/success-test-cases-oauth.json";
  private static final String FAIL_CASES_RESOURCE_PATH =
      "src/test/resources/requests/fail-test-cases.json";

  @Mock private HttpRequestFactory requestFactory;
  @Mock private HttpRequest httpRequest;
  @Mock private HttpResponse httpResponse;

  private GraphQLFunction functionUnderTest;

  @BeforeEach
  public void setup() {
    functionUnderTest = new GraphQLFunction(gson, requestFactory, gsonFactory, null);
  }

  @ParameterizedTest(name = "Executing test case: {0}")
  @MethodSource("successCases")
  void shouldReturnResult_WhenExecuted(final String input)
      throws IOException, InstantiationException, IllegalAccessException {
    // given - minimal required entity
    Object functionCallResponseAsObject = arrange(input);

    // then
    verify(httpRequest).execute();
    assertThat(functionCallResponseAsObject).isInstanceOf(GraphQLResult.class);
    assertThat(((GraphQLResult) functionCallResponseAsObject).getHeaders())
        .containsValue(APPLICATION_JSON.getMimeType());
  }

  @ParameterizedTest(name = "Executing test case: {0}")
  @MethodSource("successCasesOauth")
  void shouldReturnResultOAuth_WhenExecuted(final String input)
      throws IOException, InstantiationException, IllegalAccessException {
    Object functionCallResponseAsObject = arrange(input);

    // then
    verify(httpRequest, times(2)).execute();
    assertThat(functionCallResponseAsObject).isInstanceOf(GraphQLResult.class);
    assertThat(((GraphQLResult) functionCallResponseAsObject).getHeaders())
        .containsValue(APPLICATION_JSON.getMimeType());
  }

  private Object arrange(String input)
      throws IOException, InstantiationException, IllegalAccessException {
    final var context =
        OutboundConnectorContextBuilder.create().variables(input).secrets(name -> "foo").build();
    when(requestFactory.buildRequest(
            anyString(), any(GenericUrl.class), nullable(HttpContent.class)))
        .thenReturn(httpRequest);
    when(httpResponse.getHeaders())
        .thenReturn(new HttpHeaders().setContentType(APPLICATION_JSON.getMimeType()));
    when(httpRequest.execute()).thenReturn(httpResponse);

    // when
    return functionUnderTest.execute(context);
  }

  @ParameterizedTest(name = "Executing test case: {0}")
  @MethodSource("failCases")
  void shouldReturnFallbackResult_WhenMalformedRequest(final String input) {
    final var context =
        OutboundConnectorContextBuilder.create().variables(input).secrets(name -> "foo").build();

    // when
    var exceptionThrown = catchException(() -> functionUnderTest.execute(context));

    // then
    assertThat(exceptionThrown)
        .isInstanceOf(ConnectorInputException.class)
        .hasMessageContaining("ValidationException");
  }

  @ParameterizedTest(name = "Executing test case: {0}")
  @MethodSource("successCases")
  void execute_shouldSetConnectTime(final String input)
      throws IOException, InstantiationException, IllegalAccessException {
    // given - minimal required entity
    final var context =
        OutboundConnectorContextBuilder.create().variables(input).secrets(name -> "foo").build();
    final var expectedTimeInMilliseconds =
        Integer.parseInt(
                gson.fromJson(
                        gson.fromJson(input, JsonObject.class).get("graphql").toString(),
                        GraphQLRequest.class)
                    .getConnectionTimeoutInSeconds())
            * 1000;

    when(requestFactory.buildRequest(
            anyString(), any(GenericUrl.class), nullable(HttpContent.class)))
        .thenReturn(httpRequest);
    when(httpResponse.getHeaders())
        .thenReturn(new HttpHeaders().setContentType(APPLICATION_JSON.getMimeType()));
    when(httpRequest.execute()).thenReturn(httpResponse);
    // when
    functionUnderTest.execute(context);
    // then
    verify(httpRequest).setConnectTimeout(expectedTimeInMilliseconds);
  }

  @ParameterizedTest
  @ValueSource(ints = {400, 404, 500})
  void execute_shouldPassOnHttpErrorAsErrorCode(final int input) throws IOException {
    // given
    final var request =
        "{\"graphql\": { \"method\": \"get\", \"url\": \"https://camunda.io/http-endpoint\", \"query\": \"testQuery\"}, \"authentication\": { \"type\": \"noAuth\" } }";
    final var context = OutboundConnectorContextBuilder.create().variables(request).build();

    when(requestFactory.buildRequest(
            anyString(), any(GenericUrl.class), nullable(HttpContent.class)))
        .thenReturn(httpRequest);
    when(httpResponse.getHeaders())
        .thenReturn(new HttpHeaders().setContentType(APPLICATION_JSON.getMimeType()));
    when(httpResponse.getStatusCode()).thenReturn(input);
    when(httpResponse.parseAsString()).thenReturn("message");
    doThrow(new HttpResponseException(httpResponse)).when(httpRequest).execute();
    // when
    final var result = catchException(() -> functionUnderTest.execute(context));
    // then HTTP status code is passed on as error code
    assertThat(result)
        .isInstanceOf(ConnectorException.class)
        .extracting("errorCode")
        .isEqualTo(String.valueOf(input));
  }

  @Test
  void execute_shouldNotUseErrorDataOnHttpError() throws IOException {
    // given
    final var request =
        "{\"graphql\": {\"method\": \"get\", \"url\": \"https://camunda.io/http-endpoint\", \"query\": \"testQuery\"}, \"authentication\": { \"type\": \"noAuth\" } }";
    final var context = OutboundConnectorContextBuilder.create().variables(request).build();
    final var httpException = mock(HttpResponseException.class);

    when(requestFactory.buildRequest(
            anyString(), any(GenericUrl.class), nullable(HttpContent.class)))
        .thenReturn(httpRequest);
    when(httpException.getStatusCode()).thenReturn(500);
    when(httpException.getMessage()).thenReturn("message");
    doThrow(httpException).when(httpRequest).execute();
    // when
    final var result = catchException(() -> functionUnderTest.execute(context));
    // then HTTP status code is passed on as error code
    verify(httpException, times(0)).getContent();
    assertThat(result)
        .isInstanceOf(ConnectorException.class)
        .hasMessage("message")
        .extracting("errorCode")
        .isEqualTo("500");
  }

  private static Stream<String> successCases() throws IOException {
    return loadTestCasesFromResourceFile(SUCCESS_CASES_RESOURCE_PATH);
  }

  private static Stream<String> successCasesOauth() throws IOException {
    return loadTestCasesFromResourceFile(SUCCESS_CASES_OAUTH_RESOURCE_PATH);
  }

  private static Stream<String> failCases() throws IOException {
    return loadTestCasesFromResourceFile(FAIL_CASES_RESOURCE_PATH);
  }
}
