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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import io.camunda.connector.http.base.client.apache.ApacheRequestFactory;
import io.camunda.connector.http.base.client.apache.CustomApacheHttpClient;
import io.camunda.connector.http.base.model.HttpCommonResult;
import io.camunda.connector.http.base.model.auth.OAuthAuthentication;
import io.camunda.connector.http.base.model.auth.OAuthConstants;
import io.camunda.connector.http.base.model.auth.OAuthService;
import io.camunda.connector.http.rest.model.HttpJsonRequest;
import java.io.IOException;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class HttpServiceTest extends BaseTest {

  public static final String ACCESS_TOKEN =
      "{\"access_token\": \"abcd\", \"scope\":\"read:clients\", \"expires_in\":86400,\"token_type\":\"Bearer\"}";
  private static final String SUCCESS_CASES_OAUTH_RESOURCE_PATH =
      "src/test/resources/requests/success-test-cases-oauth.json";

  private final OAuthService oAuthService = new OAuthService();

  private static Stream<String> successCasesOauth() throws IOException {
    return loadTestCasesFromResourceFile(SUCCESS_CASES_OAUTH_RESOURCE_PATH);
  }

  @ParameterizedTest(name = "Executing test case: {0}")
  @MethodSource("successCasesOauth")
  void checkIfOAuthBearerTokenIsAddedOnTheRequestHeader(final String input) throws Exception {
    // given
    final var context = getContextBuilderWithSecrets().variables(input).build();
    final var httpJsonRequest = context.bindVariables(HttpJsonRequest.class);

    // Mock OAuth request result
    var oauthRequest =
        oAuthService.createOAuthRequestFrom(
            (OAuthAuthentication) httpJsonRequest.getAuthentication());
    HttpCommonResult oauthResult =
        new HttpCommonResult(200, null, Map.of(OAuthConstants.ACCESS_TOKEN, ACCESS_TOKEN));
    var mockedClient = mock(CustomApacheHttpClient.class);
    try (MockedStatic<CustomApacheHttpClient> mockedClientSupplier =
        mockStatic(CustomApacheHttpClient.class)) {
      mockedClientSupplier.when(CustomApacheHttpClient::getDefault).thenReturn(mockedClient);
      when(mockedClient.execute(oauthRequest)).thenReturn(oauthResult);
      // when
      String bearerToken = oAuthService.extractTokenFromResponse(oauthResult.body());
      var apacheRequest = ApacheRequestFactory.get().createHttpRequest(httpJsonRequest);

      // check if the bearer token is correctly added on the header of the main request
      assertEquals("Bearer " + bearerToken, apacheRequest.getHeader("Authorization").getValue());
      assertNotEquals("Bearer abcde", apacheRequest.getHeader("Authorization").getValue());
    }
  }
}
