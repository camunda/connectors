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
package io.camunda.connector.http.rest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpRequestFactory;
import com.google.api.client.http.HttpResponse;
import com.google.api.client.testing.http.MockHttpTransport;
import io.camunda.connector.http.base.model.HttpMethod;
import io.camunda.connector.http.base.services.AuthenticationService;
import io.camunda.connector.http.base.services.HttpRequestMapper;
import io.camunda.connector.http.rest.model.HttpJsonRequest;
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

  public static final String ACCESS_TOKEN =
      "{\"access_token\": \"abcd\", \"scope\":\"read:clients\", \"expires_in\":86400,\"token_type\":\"Bearer\"}";

  @Mock private HttpRequestFactory requestFactory;
  @Mock private HttpResponse httpResponse;

  @ParameterizedTest(name = "Executing test case: {0}")
  @MethodSource("successCasesOauth")
  void checkIfOAuthBearerTokenIsAddedOnTheRequestHeader(final String input) throws IOException {
    // given
    final var context = getContextBuilderWithSecrets().variables(input).build();
    final var httpJsonRequest = context.bindVariables(HttpJsonRequest.class);

    HttpRequestFactory factory = new MockHttpTransport().createRequestFactory();
    HttpRequest httpRequest =
        factory.buildRequest(
            HttpMethod.POST.name().toUpperCase(), new GenericUrl("http://test.bearer.com"), null);
    when(requestFactory.buildRequest(any(), any(), any())).thenReturn(httpRequest);
    when(httpResponse.parseAsString()).thenReturn(ACCESS_TOKEN);

    // when
    AuthenticationService authenticationService =
        new AuthenticationService(objectMapper, requestFactory);
    String bearerToken = authenticationService.extractOAuthAccessToken(httpResponse);
    HttpRequest request =
        HttpRequestMapper.toHttpRequest(requestFactory, httpJsonRequest, bearerToken);
    // check if the bearer token is correctly added on the header of the main request
    assertEquals("Bearer " + bearerToken, request.getHeaders().getAuthorization());
    assertNotEquals("Bearer abcde", request.getHeaders().getAuthorization());
  }

  private static Stream<String> successCasesOauth() throws IOException {
    return loadTestCasesFromResourceFile(SUCCESS_CASES_OAUTH_RESOURCE_PATH);
  }
}
