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
import io.camunda.connector.api.error.ConnectorException;
import io.camunda.connector.api.error.ConnectorInputException;
import io.camunda.connector.http.model.HttpJsonRequest;
import io.camunda.connector.http.model.HttpJsonResult;
import io.camunda.connector.test.outbound.OutboundConnectorContextBuilder;
import io.camunda.connector.validation.impl.DefaultValidationProvider;
import java.io.ByteArrayInputStream;
import java.io.IOException;
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
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class HttpJsonFunctionTest extends BaseTest {

  private static final String SUCCESS_CASES_RESOURCE_PATH =
      "src/test/resources/requests/success-test-cases.json";

  private static final String SUCCESS_CASES_OAUTH_RESOURCE_PATH =
      "src/test/resources/requests/success-test-cases-oauth.json";
  private static final String SUCCESS_CASES_CUSTOM_AUTH_RESOURCE_PATH =
      "src/test/resources/requests/success-test-custom-auth.json";
  private static final String FAIL_CASES_RESOURCE_PATH =
      "src/test/resources/requests/fail-test-cases.json";

  @Mock private HttpRequestFactory requestFactory;
  @Mock private HttpRequest httpRequest;
  @Mock private HttpResponse httpResponse;

  private HttpJsonFunction functionUnderTest;

  @BeforeEach
  public void setup() {
    functionUnderTest = new HttpJsonFunction(gson, requestFactory, null);
  }

  @ParameterizedTest(name = "Executing test case: {0}")
  @MethodSource("successCases")
  void shouldReturnResult_WhenExecuted(final String input)
      throws IOException, InstantiationException, IllegalAccessException {
    // given - minimal required entity
    Object functionCallResponseAsObject = arrange(input);

    // then
    verify(httpRequest).execute();
    assertThat(functionCallResponseAsObject).isInstanceOf(HttpJsonResult.class);
    assertThat(((HttpJsonResult) functionCallResponseAsObject).getHeaders())
        .containsValue(APPLICATION_JSON.getMimeType());
  }

  @ParameterizedTest(name = "Executing test case: {0}")
  @MethodSource("successCasesCustomAuth")
  void shouldReturnResultCustom_WhenExecuted(final String input)
      throws IOException, InstantiationException, IllegalAccessException {
    String response =
        "{\"token\":\"eyJhbJNtIbehBWQLAGapcHIctws7gavjTCSCCC0Xd5sIn7DaB52Pwmabdj-9AkrVru_fZwLQseAq38n1-DkiyAaewxB0VbQgQ\",\"user\":{\"id\":331707,\"principalId\":331707,\"deleted\":false,\"permissions\":[{\"id\":13044559,\"resourceType\":\"processdiscovery\"},{\"id\":13044527,\"resourceType\":\"credentials\"},],\"emailVerified\":true,\"passwordSet\":true},\"tenantUuid\":\"08b93cfe-a6dd-4d6b-94aa-9369fdd2a026\"}";

    when(httpResponse.parseAsString()).thenReturn(response);
    when(httpResponse.isSuccessStatusCode()).thenReturn(true);
    Object functionCallResponseAsObject = arrange(input);

    // then
    verify(httpRequest, times(2)).execute();
    assertThat(functionCallResponseAsObject).isInstanceOf(HttpJsonResult.class);
    assertThat(((HttpJsonResult) functionCallResponseAsObject).getHeaders())
        .containsValue(APPLICATION_JSON.getMimeType());
  }

  @ParameterizedTest(name = "Executing test case: {0}")
  @MethodSource("successCasesOauth")
  void shouldReturnResultOAuth_WhenExecuted(final String input)
      throws IOException, InstantiationException, IllegalAccessException {
    Object functionCallResponseAsObject = arrange(input);

    // then
    verify(httpRequest, times(2)).execute();
    assertThat(functionCallResponseAsObject).isInstanceOf(HttpJsonResult.class);
    assertThat(((HttpJsonResult) functionCallResponseAsObject).getHeaders())
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

  @Test
  void execute_shouldReturnNullFieldWhenResponseWithContainNullField()
      throws IOException, InstantiationException, IllegalAccessException {
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
  void execute_shouldSetConnectTime(final String input)
      throws IOException, InstantiationException, IllegalAccessException {
    // given - minimal required entity
    final var context =
        OutboundConnectorContextBuilder.create().variables(input).secrets(name -> "foo").build();
    final var expectedTimeInMilliseconds =
        Integer.parseInt(
                context.bindVariables(HttpJsonRequest.class).getConnectionTimeoutInSeconds())
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

  @Test
  void execute_shouldNotUseErrorDataOnHttpError() throws IOException {
    // given
    final var request =
        "{ \"method\": \"get\", \"url\": \"https://camunda.io/http-endpoint\", \"authentication\": { \"type\": \"noAuth\" } }";
    final var context = OutboundConnectorContextBuilder.create().variables(request).build();
    final var httpException = mock(HttpResponseException.class);

    when(requestFactory.buildRequest(
            anyString(), any(GenericUrl.class), nullable(HttpContent.class)))
        .thenReturn(httpRequest);
    when(httpException.getStatusCode()).thenReturn(500);
    when(httpException.getMessage()).thenReturn("message");
    when(httpException.getHeaders()).thenReturn(null);
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

  private static Stream<String> successCasesCustomAuth() throws IOException {
    return loadTestCasesFromResourceFile(SUCCESS_CASES_CUSTOM_AUTH_RESOURCE_PATH);
  }

  private static Stream<String> failCases() throws IOException {
    return loadTestCasesFromResourceFile(FAIL_CASES_RESOURCE_PATH);
  }
}
