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
package io.camunda.connector.http.base.request.apache.bulider.parts;

import static org.apache.hc.core5.http.HttpHeaders.AUTHORIZATION;

import io.camunda.connector.http.base.auth.ApiKeyAuthentication;
import io.camunda.connector.http.base.auth.BasicAuthentication;
import io.camunda.connector.http.base.auth.BearerAuthentication;
import io.camunda.connector.http.base.auth.NoAuthentication;
import io.camunda.connector.http.base.auth.OAuthAuthentication;
import io.camunda.connector.http.base.components.apache.CustomApacheHttpClient;
import io.camunda.connector.http.base.model.HttpCommonRequest;
import io.camunda.connector.http.base.model.HttpCommonResult;
import io.camunda.connector.http.base.services.OAuthService;
import io.camunda.connector.http.base.utils.Base64Helper;
import org.apache.hc.core5.http.io.support.ClassicRequestBuilder;

public class ApacheRequestAuthenticationBuilder implements ApacheRequestPartBuilder {

  private static final String BEARER = "Bearer %s";
  private final OAuthService oAuthService = new OAuthService();

  @Override
  public void build(ClassicRequestBuilder builder, HttpCommonRequest request) throws Exception {
    if (request.hasAuthentication()) {
      switch (request.getAuthentication()) {
        case NoAuthentication ignored -> {}
        case BasicAuthentication auth ->
            builder.addHeader(
                AUTHORIZATION,
                Base64Helper.buildBasicAuthenticationHeader(auth.username(), auth.password()));
        case OAuthAuthentication auth -> {
          String token = fetchOAuthToken(auth);
          builder.addHeader(AUTHORIZATION, String.format(BEARER, token));
        }
        case BearerAuthentication auth ->
            builder.addHeader(AUTHORIZATION, String.format(BEARER, auth.token()));
        case ApiKeyAuthentication auth -> {
          if (auth.isQueryLocationApiKeyAuthentication()) {
            builder.addParameter(auth.name(), auth.value());
          } else {
            builder.addHeader(auth.name(), auth.value());
          }
        }
      }
    }
  }

  private String fetchOAuthToken(OAuthAuthentication authentication) throws Exception {
    HttpCommonRequest oAuthRequest = oAuthService.createOAuthRequestFrom(authentication);
    HttpCommonResult response = CustomApacheHttpClient.getDefault().execute(oAuthRequest);
    return oAuthService.extractTokenFromResponse(response.getBody());
  }
}
