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
package io.camunda.connector.http.base.services;

import static org.apache.http.entity.ContentType.APPLICATION_JSON;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpHeaders;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpRequestFactory;
import io.camunda.connector.http.base.auth.OAuthAuthentication;
import io.camunda.connector.http.base.components.HttpTransportComponentSupplier;
import io.camunda.connector.http.base.constants.Constants;
import io.camunda.connector.http.base.model.HttpCommonRequest;
import io.camunda.connector.http.base.model.HttpMethod;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class HttpRequestMapperTest {

  private final HttpRequestFactory requestFactory =
      HttpTransportComponentSupplier.httpRequestFactoryInstance();

  @Test
  public void testToHttpRequestWithMetadata() {
    // Given
    HttpCommonRequest commonRequest = new HttpCommonRequest();
    commonRequest.setMethod(HttpMethod.GET);
    commonRequest.setUrl("https://example.com/resource?metadata=true");

    HttpRequestFactory requestFactory = HttpTransportComponentSupplier.httpRequestFactoryInstance();

    assertDoesNotThrow(
        () -> {
          // When
          HttpRequest result =
              HttpRequestMapper.toHttpRequest(requestFactory, commonRequest, "someBearerToken");

          // Then
          GenericUrl expectedUrl = new GenericUrl("https://example.com/resource?metadata=true");
          assertThat(expectedUrl).isEqualTo(result.getUrl());
        });
  }

  @Test
  public void testToOAuthHttpRequest() throws IOException {
    // Given
    HttpCommonRequest commonRequest = new HttpCommonRequest();
    OAuthAuthentication authentication =
        new OAuthAuthentication(
            "https://example.com/oauth/token",
            "clientId",
            "clientSecret",
            null,
            Constants.BASIC_AUTH_HEADER,
            null);
    commonRequest.setAuthentication(authentication);
    // When
    HttpRequest result = HttpRequestMapper.toOAuthHttpRequest(requestFactory, commonRequest);
    // Then
    assertThat(result.getHeaders().getAuthorization())
        .startsWith("Basic Y2xpZW50SWQ6Y2xpZW50U2VjcmV0");
    assertThat(result.getHeaders().getContentType())
        .isEqualTo(Constants.APPLICATION_X_WWW_FORM_URLENCODED);
  }

  @Test
  public void testToHttpRequestWithQueryParameters() {
    assertDoesNotThrow(
        () -> {
          // Given
          HttpCommonRequest commonRequest = new HttpCommonRequest();
          commonRequest.setMethod(HttpMethod.GET);
          commonRequest.setUrl("https://example.com/resource");
          Map<String, String> queryParameters = new HashMap<>();
          queryParameters.put("key", "value");
          commonRequest.setQueryParameters(queryParameters);

          // When
          HttpRequest result = HttpRequestMapper.toHttpRequest(requestFactory, commonRequest, null);

          // Then
          assertThat(result.getUrl().toString())
              .isEqualTo("https://example.com/resource?key=value");
        });
  }

  @Test
  public void testToHttpRequestWithBearerTokenHeaders() throws IOException {
    // Given
    HttpCommonRequest commonRequest = new HttpCommonRequest();
    commonRequest.setAuthentication(new OAuthAuthentication(null, null, null, null, null, null));
    commonRequest.setMethod(HttpMethod.GET);
    commonRequest.setUrl("https://example.com/resource?metadata=true");

    // When
    HttpRequest result =
        HttpRequestMapper.toHttpRequest(requestFactory, commonRequest, "someBearerToken");

    // Then
    assertThat(result.getHeaders().getAuthorization()).isEqualTo("Bearer someBearerToken");
  }

  @Test
  public void testToHttpRequestDefaultContentType() throws IOException {
    // Given
    HttpCommonRequest commonRequest = new HttpCommonRequest();
    commonRequest.setMethod(HttpMethod.POST);
    commonRequest.setUrl("https://example.com/resource");
    commonRequest.setBody("somePayload");

    // When
    HttpRequest result = HttpRequestMapper.toHttpRequest(requestFactory, commonRequest, null);

    // Then
    HttpHeaders headers = result.getHeaders();
    assertThat(headers.getContentType()).isEqualTo(APPLICATION_JSON.getMimeType());
  }
}
