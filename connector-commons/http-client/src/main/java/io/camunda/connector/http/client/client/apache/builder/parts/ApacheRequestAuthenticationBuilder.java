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
package io.camunda.connector.http.client.client.apache.builder.parts;

import static org.apache.hc.core5.http.HttpHeaders.AUTHORIZATION;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.api.error.ConnectorInputException;
import io.camunda.connector.http.client.HttpClientObjectMapperSupplier;
import io.camunda.connector.http.client.authentication.Base64Helper;
import io.camunda.connector.http.client.authentication.OAuthService;
import io.camunda.connector.http.client.client.apache.CustomApacheHttpClient;
import io.camunda.connector.http.client.model.HttpClientRequest;
import io.camunda.connector.http.client.model.auth.ApiKeyAuthentication;
import io.camunda.connector.http.client.model.auth.BasicAuthentication;
import io.camunda.connector.http.client.model.auth.BearerAuthentication;
import io.camunda.connector.http.client.model.auth.NoAuthentication;
import io.camunda.connector.http.client.model.auth.OAuthAuthentication;
import org.apache.hc.core5.http.io.support.ClassicRequestBuilder;

public class ApacheRequestAuthenticationBuilder implements ApacheRequestPartBuilder {

  private static final String BEARER = "Bearer %s";
  private final OAuthService oAuthService = new OAuthService();
  private final ObjectMapper objectMapper = HttpClientObjectMapperSupplier.getCopy();

  @Override
  public void build(ClassicRequestBuilder builder, HttpClientRequest request) {
    if (request.hasAuthentication()) {
      var auth = request.getAuthentication();
      if (auth instanceof NoAuthentication) {
        // Do nothing
      } else if (auth instanceof BasicAuthentication basicAuth) {
        builder.addHeader(
            AUTHORIZATION,
            Base64Helper.buildBasicAuthenticationHeader(
                basicAuth.username(), basicAuth.password()));
      } else if (auth instanceof OAuthAuthentication oauthAuth) {
        String token = fetchOAuthToken(oauthAuth);
        builder.addHeader(AUTHORIZATION, String.format(BEARER, token));
      } else if (auth instanceof BearerAuthentication bearerAuth) {
        builder.addHeader(AUTHORIZATION, String.format(BEARER, bearerAuth.token()));
      } else if (auth instanceof ApiKeyAuthentication apiKeyAuth) {
        if (apiKeyAuth.isQueryLocationApiKeyAuthentication()) {
          builder.addParameter(apiKeyAuth.name(), apiKeyAuth.value());
        } else {
          builder.addHeader(apiKeyAuth.name(), apiKeyAuth.value());
        }
      } else {
        throw new ConnectorInputException(
            "Unexpected Authentication value: " + request.getAuthentication());
      }
    }
  }

  String fetchOAuthToken(OAuthAuthentication authentication) {
    HttpClientRequest oAuthRequest = oAuthService.createOAuthRequestFrom(authentication);
    return new CustomApacheHttpClient()
        .execute(oAuthRequest, oAuthService::extractTokenFromResponse)
        .entity();
  }
}
