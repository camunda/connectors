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

import static org.apache.http.entity.ContentType.APPLICATION_JSON;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.doThrow;
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
import com.google.api.client.json.gson.GsonFactory;
import io.camunda.connector.api.error.ConnectorException;
import io.camunda.connector.http.model.HttpJsonRequest;
import io.camunda.connector.http.model.HttpJsonResult;
import io.camunda.connector.impl.ConnectorInputException;
import io.camunda.connector.test.outbound.OutboundConnectorContextBuilder;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
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
public class HttpJsonFunctionTest extends BaseTest {

  private static final String SUCCESS_CASES_RESOURCE_PATH =
      "src/test/resources/requests/success-test-cases.json";

  private static final String SUCCESS_CASES_OAUTH_RESOURCE_PATH =
      "src/test/resources/requests/success-test-cases-oauth.json";
  private static final String FAIL_CASES_RESOURCE_PATH =
      "src/test/resources/requests/fail-test-cases.json";

  @Mock private GsonFactory gsonFactory;
  @Mock private HttpRequestFactory requestFactory;
  @Mock private HttpRequest httpRequest;
  @Mock private HttpResponse httpResponse;

  private HttpJsonFunction functionUnderTest;

  @BeforeEach
  public void setup() {
    functionUnderTest = new HttpJsonFunction(gson, requestFactory, gsonFactory);
  }

  @ParameterizedTest(name = "Executing test case: {0}")
  @MethodSource("successCases")
  public void shouldReturnResult_WhenExecuted(final String input) throws IOException {
    // given - minimal required entity
    Object functionCallResponseAsObject = arrange(input);

    // then
    verify(httpRequest).execute();
    assertThat(functionCallResponseAsObject).isInstanceOf(HttpJsonResult.class);
    assertThat(((HttpJsonResult) functionCallResponseAsObject).getHeaders())
        .containsValue(APPLICATION_JSON.getMimeType());
  }

  @ParameterizedTest(name = "Executing test case: {0}")
  @MethodSource("successCasesOauth")
  public void shouldReturnResultOAuth_WhenExecuted(final String input)
      throws IOException, InvocationTargetException, IllegalAccessException {
    Object functionCallResponseAsObject = arrange(input);

    // then
    verify(httpRequest, times(2)).execute();
    assertThat(functionCallResponseAsObject).isInstanceOf(HttpJsonResult.class);
    assertThat(((HttpJsonResult) functionCallResponseAsObject).getHeaders())
        .containsValue(APPLICATION_JSON.getMimeType());
  }

  private Object arrange(String input) throws IOException {
    final var context =
        OutboundConnectorContextBuilder.create().variables(input).secrets(name -> "foo").build();
    when(requestFactory.buildRequest(
            anyString(), any(GenericUrl.class), nullable(HttpContent.class)))
        .thenReturn(httpRequest);
    when(httpResponse.getHeaders())
        .thenReturn(new HttpHeaders().setContentType(APPLICATION_JSON.getMimeType()));
    when(httpRequest.execute()).thenReturn(httpResponse);

    // when
    var functionCallResponseAsObject = functionUnderTest.execute(context);
    return functionCallResponseAsObject;
  }

  @ParameterizedTest(name = "Executing test case: {0}")
  @MethodSource("failCases")
  public void shouldReturnFallbackResult_WhenMalformedRequest(final String input) {
    final var context =
        OutboundConnectorContextBuilder.create().variables(input).secrets(name -> "foo").build();

    // when
    var exceptionThrown = catchException(() -> functionUnderTest.execute(context));

    // then
    assertThat(exceptionThrown)
        .isInstanceOf(ConnectorInputException.class)
        .hasMessageContaining("ValidationException");
  }

  @Test
  public void execute_shouldReturnNullFieldWhenResponseWithContainNullField() throws IOException {
    // given request, and response body with null field value
    final var request =
        "{ \"method\": \"get\", \"url\": \"https://camunda.io/http-endpoint\", \"authentication\": { \"type\": \"noAuth\" } }";
    final var response =
        "{ \"createdAt\": \"2022-10-10T05:03:14.723Z\", \"name\": \"Marvin Cremin\", \"unknown\": null, \"id\": \"1\" }";

    final var context = OutboundConnectorContextBuilder.create().variables(request).build();
    when(requestFactory.buildRequest(
            anyString(), any(GenericUrl.class), nullable(HttpContent.class)))
        .thenReturn(httpRequest);
    when(httpResponse.getHeaders())
        .thenReturn(new HttpHeaders().setContentType(APPLICATION_JSON.getMimeType()));
    when(httpResponse.getContent())
        .thenReturn(new ByteArrayInputStream(response.getBytes(StandardCharsets.UTF_8)));
    when(httpRequest.execute()).thenReturn(httpResponse);
    // when connector execute
    var functionCallResponseAsObject = functionUnderTest.execute(context);
    // then null field 'unknown' exists in response body and has a null value
    var asJsonObject =
        gson.toJsonTree(((HttpJsonResult) functionCallResponseAsObject).getBody())
            .getAsJsonObject();
    assertThat(asJsonObject.has("unknown")).isTrue();
    assertThat(asJsonObject.get("unknown").isJsonNull()).isTrue();
  }

  @ParameterizedTest(name = "Executing test case: {0}")
  @MethodSource("successCases")
  public void execute_shouldSetConnectTime(final String input) throws IOException {
    // given - minimal required entity
    final var context =
        OutboundConnectorContextBuilder.create().variables(input).secrets(name -> "foo").build();
    final var expectedTimeInMilliseconds =
        Integer.parseInt(
                gson.fromJson(input, HttpJsonRequest.class).getConnectionTimeoutInSeconds())
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
  public void execute_shouldPassOnHttpErrorAsErrorCode(final int input) throws IOException {
    // given
    final var request =
        "{ \"method\": \"get\", \"url\": \"https://camunda.io/http-endpoint\", \"authentication\": { \"type\": \"noAuth\" } }";
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
