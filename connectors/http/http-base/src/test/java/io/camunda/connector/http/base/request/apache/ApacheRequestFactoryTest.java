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
package io.camunda.connector.http.base.request.apache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import io.camunda.connector.api.json.ConnectorsObjectMapperSupplier;
import io.camunda.connector.http.base.auth.ApiKeyAuthentication;
import io.camunda.connector.http.base.auth.ApiKeyLocation;
import io.camunda.connector.http.base.auth.BasicAuthentication;
import io.camunda.connector.http.base.auth.BearerAuthentication;
import io.camunda.connector.http.base.auth.OAuthAuthentication;
import io.camunda.connector.http.base.components.apache.CustomApacheHttpClient;
import io.camunda.connector.http.base.constants.Constants;
import io.camunda.connector.http.base.model.HttpCommonRequest;
import io.camunda.connector.http.base.model.HttpCommonResult;
import io.camunda.connector.http.base.model.HttpMethod;
import io.camunda.connector.http.base.utils.Base64Helper;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpHeaders;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

public class ApacheRequestFactoryTest {

  @Nested
  class AuthenticationTests {

    @Test
    public void shouldNotSetAuthentication_whenNotProvided() throws Exception {
      // given request without authentication
      HttpCommonRequest request = new HttpCommonRequest();
      request.setMethod(HttpMethod.GET);

      // when
      ClassicHttpRequest httpRequest = ApacheRequestFactory.get().createHttpRequest(request);

      // then
      assertThat(httpRequest.getHeader(HttpHeaders.AUTHORIZATION)).isNull();
    }

    @Test
    public void shouldSetBasicAuthentication_whenProvided() throws Exception {
      // given request with basic authentication
      HttpCommonRequest request = new HttpCommonRequest();
      request.setMethod(HttpMethod.GET);
      request.setAuthentication(new BasicAuthentication("user", "password"));

      // when
      ClassicHttpRequest httpRequest = ApacheRequestFactory.get().createHttpRequest(request);

      // then
      assertThat(httpRequest.getHeader(HttpHeaders.AUTHORIZATION).getValue())
          .isEqualTo(Base64Helper.buildBasicAuthenticationHeader("user", "password"));
    }

    @Test
    public void shouldSetBearerAuthentication_whenProvided() throws Exception {
      // given request with bearer authentication
      HttpCommonRequest request = new HttpCommonRequest();
      request.setMethod(HttpMethod.GET);
      request.setAuthentication(new BearerAuthentication("token"));

      // when
      ClassicHttpRequest httpRequest = ApacheRequestFactory.get().createHttpRequest(request);

      // then
      assertThat(httpRequest.getHeader(HttpHeaders.AUTHORIZATION).getValue())
          .isEqualTo("Bearer token");
    }

    @Test
    public void shouldSetOAuthAuthentication_whenProvided() throws Exception {
      // given request with oauth authentication
      HttpCommonResult result = new HttpCommonResult(200, null, "{\"access_token\":\"token\"}");
      HttpCommonRequest request = new HttpCommonRequest();
      request.setMethod(HttpMethod.GET);
      request.setAuthentication(
          new OAuthAuthentication(
              "url", "clientId", "secret", "audience", Constants.CREDENTIALS_BODY, "scopes"));
      var mockedClient = mock(CustomApacheHttpClient.class);
      try (MockedStatic<CustomApacheHttpClient> mockedClientSupplier =
          mockStatic(CustomApacheHttpClient.class)) {
        mockedClientSupplier.when(CustomApacheHttpClient::getDefault).thenReturn(mockedClient);
        when(mockedClient.execute(any(HttpCommonRequest.class))).thenReturn(result);

        // when
        ClassicHttpRequest httpRequest = ApacheRequestFactory.get().createHttpRequest(request);

        // then
        assertThat(httpRequest.getHeader(HttpHeaders.AUTHORIZATION).getValue())
            .isEqualTo("Bearer token");
      }
    }

    @Test
    public void shouldSetApiKeyAuthenticationInHeaders_whenProvided() throws Exception {
      // given request with api key authentication
      HttpCommonRequest request = new HttpCommonRequest();
      request.setMethod(HttpMethod.GET);
      request.setAuthentication(new ApiKeyAuthentication(ApiKeyLocation.HEADERS, "name", "value"));

      // when
      ClassicHttpRequest httpRequest = ApacheRequestFactory.get().createHttpRequest(request);

      // then
      assertThat(httpRequest.getHeader("name").getValue()).isEqualTo("value");
    }

    @Test
    public void shouldSetApiKeyAuthenticationInQuery_whenProvided() throws Exception {
      // given request with api key authentication
      HttpCommonRequest request = new HttpCommonRequest();
      request.setMethod(HttpMethod.GET);
      request.setAuthentication(new ApiKeyAuthentication(ApiKeyLocation.QUERY, "name", "value"));

      // when
      ClassicHttpRequest httpRequest = ApacheRequestFactory.get().createHttpRequest(request);

      // then
      assertThat(httpRequest.getHeader("name")).isNull();
      assertThat(httpRequest.getUri().getQuery()).isEqualTo("name=value");
    }
  }

  @Nested
  class QueryParametersTests {

    @Test
    public void shouldNotSetQueryParameters_whenNotProvided() throws Exception {
      // given request without query parameters
      HttpCommonRequest request = new HttpCommonRequest();
      request.setMethod(HttpMethod.GET);

      // when
      ClassicHttpRequest httpRequest = ApacheRequestFactory.get().createHttpRequest(request);

      // then
      assertThat(httpRequest.getUri().getQuery()).isNull();
    }

    @Test
    public void shouldSetQueryParameters_whenProvided() throws Exception {
      // given request with query parameters
      HttpCommonRequest request = new HttpCommonRequest();
      request.setMethod(HttpMethod.GET);
      request.setQueryParameters(Map.of("key", "value"));

      // when
      ClassicHttpRequest httpRequest = ApacheRequestFactory.get().createHttpRequest(request);

      // then
      assertThat(httpRequest.getUri().getQuery()).isEqualTo("key=value");
    }

    @Test
    public void shouldSetQueryParameters_whenProvidedMultiple() throws Exception {
      // given request with query parameters
      HttpCommonRequest request = new HttpCommonRequest();
      request.setMethod(HttpMethod.GET);
      request.setQueryParameters(Map.of("key", "value", "key2", "value2"));

      // when
      ClassicHttpRequest httpRequest = ApacheRequestFactory.get().createHttpRequest(request);

      // then
      assertThat(httpRequest.getUri().getQuery()).contains("key=value");
      assertThat(httpRequest.getUri().getQuery()).contains("key2=value2");
      assertThat(httpRequest.getUri().getQuery()).contains("&");
    }
  }

  @Nested
  class UriTests {

    @Test
    public void shouldSetUri() throws Exception {
      // given request with url
      HttpCommonRequest request = new HttpCommonRequest();
      request.setMethod(HttpMethod.GET);
      request.setUrl("http://localhost:8080");

      // when
      ClassicHttpRequest httpRequest = ApacheRequestFactory.get().createHttpRequest(request);

      // then
      assertThat(httpRequest.getUri().toString()).isEqualTo("http://localhost:8080/");
    }

    @Test
    public void shouldSetUri_whenQueryHasParameters() throws Exception {
      // given request with url
      HttpCommonRequest request = new HttpCommonRequest();
      request.setMethod(HttpMethod.GET);
      request.setUrl("http://localhost:8080");
      request.setQueryParameters(Map.of("key", "value"));

      // when
      ClassicHttpRequest httpRequest = ApacheRequestFactory.get().createHttpRequest(request);

      // then
      assertThat(httpRequest.getUri().toString()).isEqualTo("http://localhost:8080/?key=value");
    }

    @Test
    public void shouldSetUri_whenQueryHasParametersInUrl() throws Exception {
      // given request with url
      HttpCommonRequest request = new HttpCommonRequest();
      request.setMethod(HttpMethod.GET);
      request.setUrl("http://localhost:8080/path?key=value");

      // when
      ClassicHttpRequest httpRequest = ApacheRequestFactory.get().createHttpRequest(request);

      // then
      assertThat(httpRequest.getUri().toString()).isEqualTo("http://localhost:8080/path?key=value");
    }
  }

  @Nested
  class BodyTests {

    @Test
    public void shouldNotSetBody_whenBodyNotSupported() throws Exception {
      // given request with body
      HttpCommonRequest request = new HttpCommonRequest();
      request.setMethod(HttpMethod.GET);

      // when
      ClassicHttpRequest httpRequest = ApacheRequestFactory.get().createHttpRequest(request);

      // then
      assertThat(httpRequest.getEntity()).isNull();
    }

    @Test
    public void shouldSetJsonBody_whenBodySupportedAndContentTypeNotProvided() throws Exception {
      // given request with body
      HttpCommonRequest request = new HttpCommonRequest();
      request.setMethod(HttpMethod.POST);
      request.setBody(Map.of("key", "value"));

      // when
      ClassicHttpRequest httpRequest = ApacheRequestFactory.get().createHttpRequest(request);

      // then
      assertThat(httpRequest.getEntity()).isNotNull();
      assertThat(httpRequest.getEntity().getContentLength()).isGreaterThan(0);
      assertThat(httpRequest.getEntity().getContentType())
          .isEqualTo(ContentType.APPLICATION_JSON.withCharset(StandardCharsets.UTF_8).toString());
      var jsonNode =
          ConnectorsObjectMapperSupplier.DEFAULT_MAPPER.readTree(
              httpRequest.getEntity().getContent());
      assertThat(jsonNode.get("key").asText()).isEqualTo("value");
    }

    @Test
    public void shouldNotSetJsonBody_whenBodySupportedAndContentTypeProvided() throws Exception {
      // given request with body
      HttpCommonRequest request = new HttpCommonRequest();
      request.setMethod(HttpMethod.POST);
      request.setBody("{\"key\":\"value\"}");
      request.setHeaders(
          Map.of(
              HttpHeaders.CONTENT_TYPE,
              ContentType.TEXT_PLAIN.withCharset(StandardCharsets.UTF_8).toString()));

      // when
      ClassicHttpRequest httpRequest = ApacheRequestFactory.get().createHttpRequest(request);

      // then
      assertThat(httpRequest.getEntity()).isNotNull();
      assertThat(httpRequest.getEntity().getContentLength()).isGreaterThan(0);
      assertThat(httpRequest.getEntity().getContentType())
          .isEqualTo(ContentType.TEXT_PLAIN.withCharset(StandardCharsets.UTF_8).toString());
      var jsonNode =
          ConnectorsObjectMapperSupplier.DEFAULT_MAPPER.readTree(
              httpRequest.getEntity().getContent());
      assertThat(jsonNode.get("key").asText()).isEqualTo("value");
    }

    @Test
    public void shouldSetJsonBody_whenBodySupportedAndContentTypeProvided() throws Exception {
      // given request with body
      HttpCommonRequest request = new HttpCommonRequest();
      request.setMethod(HttpMethod.POST);
      request.setBody("{\"key\":\"value\"}");
      request.setHeaders(
          Map.of(
              HttpHeaders.CONTENT_TYPE,
              ContentType.APPLICATION_JSON.withCharset(StandardCharsets.UTF_8).toString()));

      // when
      ClassicHttpRequest httpRequest = ApacheRequestFactory.get().createHttpRequest(request);

      // then
      assertThat(httpRequest.getEntity()).isNotNull();
      assertThat(httpRequest.getEntity().getContentLength()).isGreaterThan(0);
      assertThat(httpRequest.getEntity().getContentType())
          .isEqualTo(ContentType.APPLICATION_JSON.withCharset(StandardCharsets.UTF_8).toString());
      var jsonNode =
          ConnectorsObjectMapperSupplier.DEFAULT_MAPPER.readTree(
              httpRequest.getEntity().getContent());
      assertThat(jsonNode.get("key").asText()).isEqualTo("value");
    }

    @Test
    public void shouldSetJsonBody_whenBodySupportedAndContentTypeProvidedAndBodyIsMap()
        throws Exception {
      // given request with body
      HttpCommonRequest request = new HttpCommonRequest();
      request.setMethod(HttpMethod.POST);
      request.setBody(Map.of("key", "value"));
      request.setHeaders(
          Map.of(
              HttpHeaders.CONTENT_TYPE,
              ContentType.APPLICATION_JSON.withCharset(StandardCharsets.UTF_8).toString()));

      // when
      ClassicHttpRequest httpRequest = ApacheRequestFactory.get().createHttpRequest(request);

      // then
      assertThat(httpRequest.getEntity()).isNotNull();
      assertThat(httpRequest.getEntity().getContentLength()).isGreaterThan(0);
      assertThat(httpRequest.getEntity().getContentType())
          .isEqualTo(ContentType.APPLICATION_JSON.withCharset(StandardCharsets.UTF_8).toString());
      var jsonNode =
          ConnectorsObjectMapperSupplier.DEFAULT_MAPPER.readTree(
              httpRequest.getEntity().getContent());
      assertThat(jsonNode.get("key").asText()).isEqualTo("value");
    }

    @Test
    public void shouldSetFormUrlEncodedBody_whenBodySupportedAndContentTypeProvidedAndBodyIsMap()
        throws Exception {
      // given request with body
      HttpCommonRequest request = new HttpCommonRequest();
      request.setMethod(HttpMethod.POST);
      request.setBody(Map.of("key", "value", "key2", "value2"));
      request.setHeaders(
          Map.of(
              HttpHeaders.CONTENT_TYPE,
              ContentType.APPLICATION_FORM_URLENCODED
                  .withCharset(StandardCharsets.UTF_8)
                  .toString()));

      // when
      ClassicHttpRequest httpRequest = ApacheRequestFactory.get().createHttpRequest(request);

      // then
      assertThat(httpRequest.getEntity()).isNotNull();
      assertThat(httpRequest.getEntity().getContentLength()).isGreaterThan(0);
      assertThat(httpRequest.getEntity().getContentType())
          .isEqualTo(
              ContentType.APPLICATION_FORM_URLENCODED
                  .withCharset(StandardCharsets.UTF_8)
                  .toString());
      String content = new String(httpRequest.getEntity().getContent().readAllBytes());
      assertThat(content).contains("key=value");
      assertThat(content).contains("key2=value2");
      assertThat(content).contains("&");
    }
  }

  @Nested
  class HeadersTests {

    @Test
    public void shouldSetJsonContentType_WhenNotProvidedAndSupportsBody() throws Exception {
      // given request without headers
      HttpCommonRequest request = new HttpCommonRequest();
      request.setMethod(HttpMethod.POST);

      // when
      ClassicHttpRequest httpRequest = ApacheRequestFactory.get().createHttpRequest(request);

      // then
      Header headers = httpRequest.getHeader(HttpHeaders.CONTENT_TYPE);
      assertNotNull(headers);
      assertThat(headers.getValue()).isEqualTo(ContentType.APPLICATION_JSON.getMimeType());
    }

    @Test
    public void shouldSetJsonContentType_WhenNotProvidedAndSupportsBodyAndSomeHeadersExist()
        throws Exception {
      // given request without headers
      HttpCommonRequest request = new HttpCommonRequest();
      request.setHeaders(Map.of("Authorization", "Bearer token"));
      request.setMethod(HttpMethod.POST);

      // when
      ClassicHttpRequest httpRequest = ApacheRequestFactory.get().createHttpRequest(request);

      // then
      assertThat(httpRequest.getHeaders().length).isEqualTo(2);
      Header headers = httpRequest.getHeader(HttpHeaders.CONTENT_TYPE);
      assertNotNull(headers);
      assertThat(headers.getValue()).isEqualTo(ContentType.APPLICATION_JSON.getMimeType());
    }

    @Test
    public void shouldNotSetJsonContentType_WhenNotProvidedAndDoesNotSupportBody()
        throws Exception {
      // given request without headers
      HttpCommonRequest request = new HttpCommonRequest();
      request.setMethod(HttpMethod.GET);

      // when
      ClassicHttpRequest httpRequest = ApacheRequestFactory.get().createHttpRequest(request);

      // then
      Header headers = httpRequest.getHeader(HttpHeaders.CONTENT_TYPE);
      assertNull(headers);
    }

    @Test
    public void shouldNotSetJsonContentType_WhenProvided() throws Exception {
      // given request without headers
      HttpCommonRequest request = new HttpCommonRequest();
      request.setHeaders(Map.of(HttpHeaders.CONTENT_TYPE, "text/plain"));
      request.setMethod(HttpMethod.POST);

      // when
      ClassicHttpRequest httpRequest = ApacheRequestFactory.get().createHttpRequest(request);

      // then
      Header headers = httpRequest.getHeader(HttpHeaders.CONTENT_TYPE);
      assertNotNull(headers);
      assertThat(headers.getValue()).isEqualTo(ContentType.TEXT_PLAIN.getMimeType());
    }
  }
}
