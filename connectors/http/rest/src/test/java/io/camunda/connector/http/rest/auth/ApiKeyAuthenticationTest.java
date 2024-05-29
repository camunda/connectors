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
package io.camunda.connector.http.rest.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpHeaders;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpRequestFactory;
import com.google.api.client.http.HttpResponse;
import io.camunda.connector.http.base.model.HttpMethod;
import io.camunda.connector.http.rest.BaseTest;
import io.camunda.connector.http.rest.HttpJsonFunction;
import java.io.IOException;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class ApiKeyAuthenticationTest extends BaseTest {

  private static final String SUCCESS_CASES_API_KEY_AUTH_IN_QUERY =
      "src/test/resources/requests/success-test-cases-api-key-query.json";

  private static final String SUCCESS_CASES_API_KEY_AUTH_IN_HEADERS =
      "src/test/resources/requests/success-test-cases-api-key-headers.json";

  private HttpJsonFunction connector;

  @Mock private HttpRequestFactory requestFactory;
  @Mock private HttpRequest httpRequest;
  @Mock private HttpResponse httpResponse;

  @Captor private ArgumentCaptor<GenericUrl> urlCaptor;
  @Captor private ArgumentCaptor<HttpHeaders> headersCaptor;

  private static Stream<String> successCasesApiKeyAuthInQueryParameters() throws IOException {
    return loadTestCasesFromResourceFile(SUCCESS_CASES_API_KEY_AUTH_IN_QUERY);
  }

  private static Stream<String> successCasesApiKeyAuthInHeaders() throws IOException {
    return loadTestCasesFromResourceFile(SUCCESS_CASES_API_KEY_AUTH_IN_HEADERS);
  }

  @BeforeEach
  public void beforeEach() throws IOException {
    connector = new HttpJsonFunction();
    when(requestFactory.buildRequest(eq(HttpMethod.GET.name()), urlCaptor.capture(), any()))
        .thenReturn(httpRequest);

    when(httpRequest.setFollowRedirects(anyBoolean())).thenReturn(httpRequest);
    when(httpRequest.execute()).thenReturn(httpResponse);
    when(httpResponse.getHeaders()).thenReturn(new HttpHeaders());
    when(httpResponse.getStatusCode()).thenReturn(200);

    when(httpRequest.setHeaders(headersCaptor.capture())).thenReturn(httpRequest);
  }

  @ParameterizedTest(name = "Executing test case: {0}")
  @MethodSource("successCasesApiKeyAuthInQueryParameters")
  void apiKeyAuthenticationWithApiKeyAsQueryParamsTest(final String input) throws Exception {
    // given
    final var context = getContextBuilderWithSecrets().variables(input).build();
    // when
    Object execute = connector.execute(context);
    // then
    assertThat(execute).isNotNull();
    // api key in url as query parameter
    assertThat(urlCaptor.getValue().getFirst(ActualValue.Authentication.API_KEY_NAME))
        .isEqualTo(ActualValue.Authentication.API_KEY_VALUE);
    assertThat(
            headersCaptor
                .getValue()
                .getFirstHeaderStringValue(ActualValue.Authentication.API_KEY_NAME))
        .isNull();
  }

  @ParameterizedTest(name = "Executing test case: {0}")
  @MethodSource("successCasesApiKeyAuthInHeaders")
  void apiKeyAuthenticationWithApiKeyInHeadersTest(final String input) throws Exception {
    // given
    final var context = getContextBuilderWithSecrets().variables(input).build();
    // when
    Object execute = connector.execute(context);
    // then
    assertThat(execute).isNotNull();
    // api key in headers
    assertThat(urlCaptor.getValue().getFirst(ActualValue.Authentication.API_KEY_NAME)).isNull();
    assertThat(
            headersCaptor
                .getValue()
                .getFirstHeaderStringValue(ActualValue.Authentication.API_KEY_NAME))
        .isEqualTo(ActualValue.Authentication.API_KEY_VALUE);
  }
}
