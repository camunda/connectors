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
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpContent;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpRequestFactory;
import com.google.api.client.http.HttpResponse;
import com.google.api.client.http.HttpResponseException;
import io.camunda.connector.api.error.ConnectorException;
import io.camunda.connector.http.constants.Constants;
import io.camunda.connector.http.model.HttpJsonResult;
import io.camunda.connector.test.outbound.OutboundConnectorContextBuilder;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class HttpJsonFunctionProxyTest extends BaseTest {

  private static final String SUCCESS_CASES_RESOURCE_PATH =
      "src/test/resources/requests/success-test-cases.json";

  private static final String PROXY_FUNCTION_URL = "http://localhost/my-proxy/";

  @Mock private HttpRequestFactory requestFactory;
  @Mock private HttpRequest httpRequest;
  @Mock private HttpResponse httpResponse;

  private HttpJsonFunction functionUnderTest;

  @BeforeEach
  public void setup() {
    functionUnderTest = new HttpJsonFunction(gson, requestFactory, PROXY_FUNCTION_URL);
  }

  @ParameterizedTest(name = "Executing test case: {0}")
  @MethodSource("successCases")
  void shouldReturnResult_WhenExecuted(final String input) throws IOException {
    // given - minimal required entity
    final var context =
        OutboundConnectorContextBuilder.create().variables(input).secrets(name -> "foo").build();

    when(requestFactory.buildRequest(
            eq(Constants.POST),
            eq(new GenericUrl(PROXY_FUNCTION_URL)),
            nullable(HttpContent.class)))
        .thenReturn(httpRequest);
    String responseContent = "{ headers: { 'someHeader': 'someValue'}}";
    when(httpResponse.getContent())
        .thenReturn(new ByteArrayInputStream(responseContent.getBytes(StandardCharsets.UTF_8)));
    when(httpRequest.execute()).thenReturn(httpResponse);

    // when
    HttpJsonResult functionCallResponseAsObject =
        (HttpJsonResult) functionUnderTest.execute(context);

    // then
    verify(httpRequest).execute();
    assertThat(functionCallResponseAsObject.getHeaders()).containsEntry("someHeader", "someValue");
  }

  @ParameterizedTest(name = "Executing test case: {0}")
  @MethodSource("successCases")
  void shouldReuseErrorData_WhenProxyCallFailed(final String input) throws IOException {
    // given - minimal required entity
    final var context =
        OutboundConnectorContextBuilder.create().variables(input).secrets(name -> "foo").build();

    final var httpException = mock(HttpResponseException.class);
    String errorResponseContent = "{ errorCode: 'XYZ', error: 'some message' }";
    when(httpException.getContent()).thenReturn(errorResponseContent);
    when(httpException.getStatusCode()).thenReturn(500);
    when(httpException.getMessage()).thenReturn("my error message");
    when(requestFactory.buildRequest(
            eq(Constants.POST),
            eq(new GenericUrl(PROXY_FUNCTION_URL)),
            nullable(HttpContent.class)))
        .thenReturn(httpRequest);
    doThrow(httpException).when(httpRequest).execute();
    // when
    final var result = catchThrowable(() -> functionUnderTest.execute(context));
    // then
    assertThat(result)
        .isInstanceOf(ConnectorException.class)
        .hasMessage("some message")
        .extracting("errorCode")
        .isEqualTo("XYZ");
  }

  @ParameterizedTest(name = "Executing test case: {0}")
  @MethodSource("successCases")
  void shouldUseExceptionData_WhenProxyCallFailed_ErrorDataNoJson(final String input)
      throws IOException {
    // given - minimal required entity
    final var context =
        OutboundConnectorContextBuilder.create().variables(input).secrets(name -> "foo").build();

    final var httpException = mock(HttpResponseException.class);
    String errorResponseContent = "XYZ";
    when(httpException.getContent()).thenReturn(errorResponseContent);
    when(httpException.getStatusCode()).thenReturn(500);
    when(httpException.getMessage()).thenReturn("my error message");
    when(requestFactory.buildRequest(
            eq(Constants.POST),
            eq(new GenericUrl(PROXY_FUNCTION_URL)),
            nullable(HttpContent.class)))
        .thenReturn(httpRequest);
    doThrow(httpException).when(httpRequest).execute();
    // when
    final var result = catchThrowable(() -> functionUnderTest.execute(context));
    // then
    assertThat(result)
        .isInstanceOf(ConnectorException.class)
        .hasMessage("my error message")
        .extracting("errorCode")
        .isEqualTo("500");
  }

  @ParameterizedTest(name = "Executing test case: {0}")
  @MethodSource("successCases")
  void shouldUseExceptionData_WhenProxyCallFailed_NoErrorData(final String input)
      throws IOException {
    // given - minimal required entity
    final var context =
        OutboundConnectorContextBuilder.create().variables(input).secrets(name -> "foo").build();

    final var httpException = mock(HttpResponseException.class);
    when(httpException.getStatusCode()).thenReturn(500);
    when(httpException.getMessage()).thenReturn("my error message");
    when(requestFactory.buildRequest(
            eq(Constants.POST),
            eq(new GenericUrl(PROXY_FUNCTION_URL)),
            nullable(HttpContent.class)))
        .thenReturn(httpRequest);
    doThrow(httpException).when(httpRequest).execute();
    // when
    final var result = catchThrowable(() -> functionUnderTest.execute(context));
    // then
    assertThat(result)
        .isInstanceOf(ConnectorException.class)
        .hasMessage("my error message")
        .extracting("errorCode")
        .isEqualTo("500");
  }

  private static Stream<String> successCases() throws IOException {
    return loadTestCasesFromResourceFile(SUCCESS_CASES_RESOURCE_PATH);
  }
}
