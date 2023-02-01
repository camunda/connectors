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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpRequestFactory;
import com.google.api.client.http.HttpResponse;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.LowLevelHttpRequest;
import com.google.api.client.http.LowLevelHttpResponse;
import com.google.api.client.json.Json;
import com.google.api.client.testing.http.MockHttpTransport;
import com.google.api.client.testing.http.MockLowLevelHttpRequest;
import com.google.api.client.testing.http.MockLowLevelHttpResponse;
import io.camunda.connector.http.constants.Constants;
import io.camunda.connector.http.model.HttpJsonRequest;
import io.camunda.connector.http.model.HttpJsonResult;
import io.camunda.connector.impl.config.ConnectorConfigurationUtil;
import io.camunda.connector.test.outbound.OutboundConnectorContextBuilder;
import java.io.IOException;
import java.util.stream.Stream;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class HttpServiceTest extends BaseTest {

  private static final String SUCCESS_CASES_OAUTH_RESOURCE_PATH =
      "src/test/resources/requests/success-test-cases-oauth.json";

  private static final String SUCCESS_CASES_CUSTOM_AUTH_RESOURCE_PATH =
      "src/test/resources/requests/success-test-custom-auth.json";

  public static final String ACCESS_TOKEN =
      "{\"access_token\": \"abcd\", \"scope\":\"read:clients\", \"expires_in\":86400,\"token_type\":\"Bearer\"}";

  public static final String CUSTOM_AUTH_RESPONSE =
      "{\"token\":\"eyJhbJNtIbehBWQLAGapcHIctws7gavjTCSCCC0Xd5sIn7DaB52Pwmabdj-9AkrVru_fZwLQseAq38n1-DkiyAaewxB0VbQgQ\",\"user\":{\"id\":331707,\"principalId\":331707,\"deleted\":false,\"permissions\":[{\"id\":13044559,\"resourceType\":\"processdiscovery\"},{\"id\":13044527,\"resourceType\":\"credentials\"},],\"emailVerified\":true,\"passwordSet\":true},\"tenantUuid\":\"08b93cfe-a6dd-4d6b-94aa-9369fdd2a026\"}";

  @Mock private HttpRequestFactory requestFactory;
  @Mock private HttpResponse httpResponse;

  @ParameterizedTest(name = "Executing test case: {0}")
  @MethodSource("successCasesOauth")
  void checkIfOAuthBearerTokenIsAddedOnTheRequestHeader(final String input) throws IOException {
    // given
    final var context = OutboundConnectorContextBuilder.create().variables(input).build();
    final var httpJsonRequest = gson.fromJson(context.getVariables(), HttpJsonRequest.class);

    HttpRequestFactory factory = new MockHttpTransport().createRequestFactory();
    HttpRequest httpRequest =
        factory.buildRequest(Constants.POST, new GenericUrl("http://test.bearer.com"), null);
    when(requestFactory.buildRequest(any(), any(), any())).thenReturn(httpRequest);
    when(httpResponse.parseAsString()).thenReturn(ACCESS_TOKEN);

    // when
    String bearerToken = ResponseParser.extractOAuthAccessToken(httpResponse);
    HttpRequest request =
        HttpRequestMapper.toHttpRequest(requestFactory, httpJsonRequest, bearerToken);
    // check if the bearer token is correctly added on the header of the main request
    assertEquals("Bearer " + bearerToken, request.getHeaders().getAuthorization());
    assertNotEquals("Bearer abcde", request.getHeaders().getAuthorization());
  }

  @ParameterizedTest(name = "Executing test case: {0}")
  @MethodSource("successCasesCustomAuth")
  void execute_shouldPassAllStepsAndParsing(final String input) throws IOException {
    // given
    final var context = OutboundConnectorContextBuilder.create().variables(input).build();
    final var httpJsonRequest = gson.fromJson(context.getVariables(), HttpJsonRequest.class);

    HttpTransport transport =
        new MockHttpTransport() {
          @Override
          public LowLevelHttpRequest buildRequest(String method, String url) {
            return new MockLowLevelHttpRequest() {
              @Override
              public LowLevelHttpResponse execute() {
                MockLowLevelHttpResponse response = new MockLowLevelHttpResponse();
                response.addHeader("custom_header", "value");
                response.setStatusCode(200);
                response.setContentType(Json.MEDIA_TYPE);
                response.setContent(CUSTOM_AUTH_RESPONSE);
                return response;
              }
            };
          }
        };
    HttpRequestFactory requestFactory = transport.createRequestFactory();

    // when
    HttpService httpService =
        new HttpService(
            gson,
            requestFactory,
            ConnectorConfigurationUtil.getProperty(Constants.PROXY_FUNCTION_URL_ENV_NAME));

    Object result = httpService.executeConnectorRequest(httpJsonRequest);

    // then
    assertThat(result).isInstanceOf(HttpJsonResult.class);
    HttpJsonResult httpJsonResult = (HttpJsonResult) result;
    assertThat(httpJsonResult.getStatus()).isEqualTo(200);
  }

  private static Stream<String> successCasesOauth() throws IOException {
    return loadTestCasesFromResourceFile(SUCCESS_CASES_OAUTH_RESOURCE_PATH);
  }

  private static Stream<String> successCasesCustomAuth() throws IOException {
    return loadTestCasesFromResourceFile(SUCCESS_CASES_CUSTOM_AUTH_RESOURCE_PATH);
  }
}
