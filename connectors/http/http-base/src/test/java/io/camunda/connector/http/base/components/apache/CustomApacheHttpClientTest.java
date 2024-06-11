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
package io.camunda.connector.http.base.components.apache;

import static com.github.tomakehurst.wiremock.client.WireMock.and;
import static com.github.tomakehurst.wiremock.client.WireMock.any;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.created;
import static com.github.tomakehurst.wiremock.client.WireMock.delete;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.noContent;
import static com.github.tomakehurst.wiremock.client.WireMock.notFound;
import static com.github.tomakehurst.wiremock.client.WireMock.ok;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.put;
import static com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.serverError;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.unauthorized;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import io.camunda.connector.api.error.ConnectorException;
import io.camunda.connector.api.json.ConnectorsObjectMapperSupplier;
import io.camunda.connector.http.base.client.apache.CustomApacheHttpClient;
import io.camunda.connector.http.base.model.HttpCommonRequest;
import io.camunda.connector.http.base.model.HttpCommonResult;
import io.camunda.connector.http.base.model.HttpMethod;
import io.camunda.connector.http.base.model.auth.ApiKeyAuthentication;
import io.camunda.connector.http.base.model.auth.ApiKeyLocation;
import io.camunda.connector.http.base.model.auth.BasicAuthentication;
import io.camunda.connector.http.base.model.auth.BearerAuthentication;
import io.camunda.connector.http.base.model.auth.OAuthAuthentication;
import io.camunda.connector.http.base.model.auth.OAuthConstants;
import java.util.Map;
import org.apache.commons.text.StringEscapeUtils;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpStatus;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;
import wiremock.com.fasterxml.jackson.databind.node.JsonNodeFactory;

@WireMockTest
public class CustomApacheHttpClientTest {

  private final CustomApacheHttpClient customApacheHttpClient = CustomApacheHttpClient.getDefault();
  private final ObjectMapper objectMapper = ConnectorsObjectMapperSupplier.DEFAULT_MAPPER;

  private String getHostAndPort(WireMockRuntimeInfo wmRuntimeInfo) {
    return "http://localhost:" + wmRuntimeInfo.getHttpPort();
  }

  @Nested
  class GetTests {

    @Test
    public void shouldReturn200WithoutBody_whenEmptyGet(WireMockRuntimeInfo wmRuntimeInfo)
        throws Exception {
      stubFor(get("/path").willReturn(ok()));

      HttpCommonRequest request = new HttpCommonRequest();
      request.setMethod(HttpMethod.GET);
      request.setUrl(getHostAndPort(wmRuntimeInfo) + "/path");
      HttpCommonResult result = customApacheHttpClient.execute(request);
      assertThat(result).isNotNull();
      assertThat(result.status()).isEqualTo(200);
    }

    @Test
    public void shouldReturn200WithBody_whenGetWithBody(WireMockRuntimeInfo wmRuntimeInfo)
        throws Exception {
      stubFor(get("/path").willReturn(ok("Hello, world!")));

      HttpCommonRequest request = new HttpCommonRequest();
      request.setMethod(HttpMethod.GET);
      request.setUrl(getHostAndPort(wmRuntimeInfo) + "/path");
      HttpCommonResult result = customApacheHttpClient.execute(request);
      assertThat(result).isNotNull();
      assertThat(result.status()).isEqualTo(200);
      assertThat(result.body()).isEqualTo("Hello, world!");
    }

    @Test
    public void shouldReturn200WithBody_whenGetWithBodyJSON(WireMockRuntimeInfo wmRuntimeInfo)
        throws Exception {
      stubFor(
          get("/path")
              .willReturn(
                  ok().withJsonBody(
                          JsonNodeFactory.instance
                              .objectNode()
                              .put("name", "John")
                              .put("age", 30)
                              .putNull("message"))));

      HttpCommonRequest request = new HttpCommonRequest();
      request.setMethod(HttpMethod.GET);
      request.setHeaders(Map.of("Accept", "application/json"));
      request.setUrl(getHostAndPort(wmRuntimeInfo) + "/path");
      HttpCommonResult result = customApacheHttpClient.execute(request);
      assertThat(result).isNotNull();
      assertThat(result.status()).isEqualTo(200);
      JSONAssert.assertEquals(
          "{\"name\":\"John\",\"age\":30,\"message\":null}",
          objectMapper.writeValueAsString(result.body()),
          JSONCompareMode.STRICT);
    }

    @ParameterizedTest
    @EnumSource(HttpMethod.class)
    public void shouldReturn200WithBody_whenGetWithBodyXML(
        HttpMethod method, WireMockRuntimeInfo wmRuntimeInfo) throws Exception {
      stubFor(
          any(urlEqualTo("/path?format=xml"))
              .willReturn(
                  ok().withBody(
                          "<note>\n"
                              + "  <to>Tove</to>\n"
                              + "  <from>Jani</from>\n"
                              + "  <heading>Reminder</heading>\n"
                              + "  <body>Don't forget me this weekend!</body>\n"
                              + "</note>")));

      HttpCommonRequest request = new HttpCommonRequest();
      request.setMethod(method);
      request.setQueryParameters(Map.of("format", "xml"));
      request.setHeaders(Map.of("Accept", "application/xml"));
      request.setUrl(getHostAndPort(wmRuntimeInfo) + "/path");
      HttpCommonResult result = customApacheHttpClient.execute(request);
      assertThat(result).isNotNull();
      assertThat(result.status()).isEqualTo(200);
      assertThat(result.body())
          .isEqualTo(
              "<note>\n"
                  + "  <to>Tove</to>\n"
                  + "  <from>Jani</from>\n"
                  + "  <heading>Reminder</heading>\n"
                  + "  <body>Don't forget me this weekend!</body>\n"
                  + "</note>");
    }

    @Test
    public void shouldReturn500_whenGetWithInvalidBody(WireMockRuntimeInfo wmRuntimeInfo) {
      stubFor(get("/path").willReturn(serverError().withStatusMessage("Invalid JSON")));
      HttpCommonRequest request = new HttpCommonRequest();
      request.setMethod(HttpMethod.GET);
      request.setUrl(getHostAndPort(wmRuntimeInfo) + "/path");
      ConnectorException e =
          assertThrows(ConnectorException.class, () -> customApacheHttpClient.execute(request));
      assertThat(e.getErrorCode()).isEqualTo("500");
      assertThat(e.getMessage()).contains("Invalid JSON");
    }

    @Test
    public void shouldReturn404_whenGetWithNonExistingPath(WireMockRuntimeInfo wmRuntimeInfo) {
      stubFor(get("/path").willReturn(notFound().withBody("Not Found: /path")));

      HttpCommonRequest request = new HttpCommonRequest();
      request.setMethod(HttpMethod.GET);
      request.setUrl(getHostAndPort(wmRuntimeInfo) + "/path");
      ConnectorException e =
          assertThrows(ConnectorException.class, () -> customApacheHttpClient.execute(request));
      assertThat(e.getErrorCode()).isEqualTo("404");
      assertThat(e.getMessage()).contains("Not Found: /path");
    }

    @Test
    public void shouldReturn408_whenGetWithTimeout(WireMockRuntimeInfo wmRuntimeInfo) {
      stubFor(get("/path").willReturn(ok().withFixedDelay(2000)));

      HttpCommonRequest request = new HttpCommonRequest();
      request.setMethod(HttpMethod.GET);
      request.setUrl(getHostAndPort(wmRuntimeInfo) + "/path");
      request.setReadTimeoutInSeconds(1);
      ConnectorException e =
          assertThrows(ConnectorException.class, () -> customApacheHttpClient.execute(request));
      assertThat(e.getErrorCode()).isEqualTo(String.valueOf(HttpStatus.SC_REQUEST_TIMEOUT));
      assertThat(e.getMessage())
          .contains("An error occurred while executing the request, or the connection was aborted");
    }
  }

  @Nested
  class PostTests {

    @Test
    public void shouldReturn201WithoutBody_whenEmptyPost(WireMockRuntimeInfo wmRuntimeInfo)
        throws Exception {
      stubFor(post("/path").willReturn(created()));

      HttpCommonRequest request = new HttpCommonRequest();
      request.setMethod(HttpMethod.POST);
      request.setUrl(getHostAndPort(wmRuntimeInfo) + "/path");
      HttpCommonResult result = customApacheHttpClient.execute(request);
      assertThat(result).isNotNull();
      assertThat(result.status()).isEqualTo(201);
    }

    @Test
    public void shouldReturn201WithBody_whenPostBody(WireMockRuntimeInfo wmRuntimeInfo)
        throws Exception {
      stubFor(post("/path").willReturn(created()));

      HttpCommonRequest request = new HttpCommonRequest();
      request.setMethod(HttpMethod.POST);
      request.setHeaders(Map.of("header", "headerValue"));
      request.setBody(Map.of("key1", "value1"));
      request.setUrl(getHostAndPort(wmRuntimeInfo) + "/path");
      HttpCommonResult result = customApacheHttpClient.execute(request);
      assertThat(result).isNotNull();
      assertThat(result.status()).isEqualTo(201);

      verify(
          postRequestedFor(urlEqualTo("/path"))
              .withHeader("Content-Type", equalTo("application/json"))
              .withHeader("header", equalTo("headerValue"))
              .withRequestBody(equalTo(StringEscapeUtils.unescapeJson("{\"key1\":\"value1\"}"))));
    }

    @Test
    public void shouldReturn201WithBody_whenPostBodyURLEncoded(WireMockRuntimeInfo wmRuntimeInfo)
        throws Exception {
      stubFor(post("/path").willReturn(created()));

      HttpCommonRequest request = new HttpCommonRequest();
      request.setMethod(HttpMethod.POST);
      request.setHeaders(
          Map.of(HttpHeaders.CONTENT_TYPE, ContentType.APPLICATION_FORM_URLENCODED.getMimeType()));
      request.setBody(Map.of("key1", "value1", "key2", "value2"));
      request.setUrl(getHostAndPort(wmRuntimeInfo) + "/path");
      HttpCommonResult result = customApacheHttpClient.execute(request);
      assertThat(result).isNotNull();
      assertThat(result.status()).isEqualTo(201);

      verify(
          postRequestedFor(urlEqualTo("/path"))
              .withHeader("Content-Type", equalTo("application/x-www-form-urlencoded"))
              .withRequestBody(
                  and(containing("key1=value1"), containing("&"), containing("key2=value2"))));
    }

    @Test
    public void shouldReturn201WithBody_whenPostBodyTextPlainWithStringBody(
        WireMockRuntimeInfo wmRuntimeInfo) throws Exception {
      stubFor(post("/path").willReturn(created()));

      HttpCommonRequest request = new HttpCommonRequest();
      request.setMethod(HttpMethod.POST);
      request.setHeaders(Map.of(HttpHeaders.CONTENT_TYPE, ContentType.TEXT_PLAIN.getMimeType()));
      request.setBody("Hello, world!");
      request.setUrl(getHostAndPort(wmRuntimeInfo) + "/path");
      HttpCommonResult result = customApacheHttpClient.execute(request);
      assertThat(result).isNotNull();
      assertThat(result.status()).isEqualTo(201);

      verify(
          postRequestedFor(urlEqualTo("/path"))
              .withHeader("Content-Type", equalTo("text/plain"))
              .withRequestBody(equalTo("Hello, world!")));
    }

    @Test
    public void shouldReturn201WithBody_whenPostBodyTextPlainWithIntegerBody(
        WireMockRuntimeInfo wmRuntimeInfo) throws Exception {
      stubFor(post("/path").willReturn(created()));

      HttpCommonRequest request = new HttpCommonRequest();
      request.setMethod(HttpMethod.POST);
      request.setHeaders(Map.of(HttpHeaders.CONTENT_TYPE, ContentType.TEXT_PLAIN.getMimeType()));
      request.setBody(123);
      request.setUrl(getHostAndPort(wmRuntimeInfo) + "/path");
      HttpCommonResult result = customApacheHttpClient.execute(request);
      assertThat(result).isNotNull();
      assertThat(result.status()).isEqualTo(201);

      verify(
          postRequestedFor(urlEqualTo("/path"))
              .withHeader("Content-Type", equalTo("text/plain"))
              .withRequestBody(equalTo("123")));
    }

    @Test
    public void shouldReturn200WithBody_whenPostBodyTextPlainWithBooleanBody(
        WireMockRuntimeInfo wmRuntimeInfo) throws Exception {
      stubFor(post("/path").willReturn(created()));

      HttpCommonRequest request = new HttpCommonRequest();
      request.setMethod(HttpMethod.POST);
      request.setHeaders(Map.of(HttpHeaders.CONTENT_TYPE, ContentType.TEXT_PLAIN.getMimeType()));
      request.setBody(true);
      request.setUrl(getHostAndPort(wmRuntimeInfo) + "/path");
      HttpCommonResult result = customApacheHttpClient.execute(request);
      assertThat(result).isNotNull();
      assertThat(result.status()).isEqualTo(201);

      verify(
          postRequestedFor(urlEqualTo("/path"))
              .withHeader("Content-Type", equalTo("text/plain"))
              .withRequestBody(equalTo("true")));
    }
  }

  @Nested
  class DeleteTests {
    @Test
    public void shouldReturn204WithoutBody_whenDelete(WireMockRuntimeInfo wmRuntimeInfo)
        throws Exception {
      stubFor(delete("/path/id").willReturn(noContent()));

      HttpCommonRequest request = new HttpCommonRequest();
      request.setMethod(HttpMethod.DELETE);
      request.setUrl(getHostAndPort(wmRuntimeInfo) + "/path/id");
      HttpCommonResult result = customApacheHttpClient.execute(request);
      assertThat(result).isNotNull();
      assertThat(result.body()).isNull();
      assertThat(result.status()).isEqualTo(204);
    }
  }

  @Nested
  class PutTests {
    @Test
    public void shouldReturn200WithoutBody_whenEmptyPut(WireMockRuntimeInfo wmRuntimeInfo)
        throws Exception {
      stubFor(put("/path").willReturn(ok()));

      HttpCommonRequest request = new HttpCommonRequest();
      request.setMethod(HttpMethod.PUT);
      request.setUrl(getHostAndPort(wmRuntimeInfo) + "/path");
      HttpCommonResult result = customApacheHttpClient.execute(request);
      assertThat(result).isNotNull();
      assertThat(result.status()).isEqualTo(200);
    }

    @Test
    public void shouldReturn200WithBody_whenPutBody(WireMockRuntimeInfo wmRuntimeInfo)
        throws Exception {
      stubFor(put("/path").willReturn(ok()));

      HttpCommonRequest request = new HttpCommonRequest();
      request.setMethod(HttpMethod.PUT);
      request.setHeaders(Map.of("header", "headerValue"));
      request.setBody(Map.of("key1", "value1"));
      request.setUrl(getHostAndPort(wmRuntimeInfo) + "/path");
      HttpCommonResult result = customApacheHttpClient.execute(request);
      assertThat(result).isNotNull();
      assertThat(result.status()).isEqualTo(200);

      verify(
          putRequestedFor(urlEqualTo("/path"))
              .withHeader("Content-Type", equalTo("application/json"))
              .withHeader("header", equalTo("headerValue"))
              .withRequestBody(equalTo(StringEscapeUtils.unescapeJson("{\"key1\":\"value1\"}"))));
    }

    @Test
    public void shouldReturn200WithBody_whenPutBodyURLEncoded(WireMockRuntimeInfo wmRuntimeInfo)
        throws Exception {
      stubFor(put("/path").willReturn(ok()));

      HttpCommonRequest request = new HttpCommonRequest();
      request.setMethod(HttpMethod.PUT);
      request.setHeaders(
          Map.of(HttpHeaders.CONTENT_TYPE, ContentType.APPLICATION_FORM_URLENCODED.getMimeType()));
      request.setBody(Map.of("key1", "value1", "key2", "value2"));
      request.setUrl(getHostAndPort(wmRuntimeInfo) + "/path");
      HttpCommonResult result = customApacheHttpClient.execute(request);
      assertThat(result).isNotNull();
      assertThat(result.status()).isEqualTo(200);

      verify(
          putRequestedFor(urlEqualTo("/path"))
              .withHeader("Content-Type", equalTo("application/x-www-form-urlencoded"))
              .withRequestBody(
                  and(containing("key1=value1"), containing("&"), containing("key2=value2"))));
    }

    @Test
    public void shouldReturn200WithBody_whenPutBodyTextPlainWithStringBody(
        WireMockRuntimeInfo wmRuntimeInfo) throws Exception {
      stubFor(put("/path").willReturn(ok().withBody("Hello, world updated!")));

      HttpCommonRequest request = new HttpCommonRequest();
      request.setMethod(HttpMethod.PUT);
      request.setHeaders(Map.of(HttpHeaders.CONTENT_TYPE, ContentType.TEXT_PLAIN.getMimeType()));
      request.setBody("Hello, world!");
      request.setUrl(getHostAndPort(wmRuntimeInfo) + "/path");
      HttpCommonResult result = customApacheHttpClient.execute(request);
      assertThat(result).isNotNull();
      assertThat(result.status()).isEqualTo(200);
      assertThat(result.body()).isEqualTo("Hello, world updated!");
    }

    @Test
    public void shouldReturn200WithBody_whenPutBodyTextPlainWithIntegerBody(
        WireMockRuntimeInfo wmRuntimeInfo) throws Exception {
      stubFor(put("/path").willReturn(ok().withBody("123")));

      HttpCommonRequest request = new HttpCommonRequest();
      request.setMethod(HttpMethod.PUT);
      request.setHeaders(Map.of(HttpHeaders.CONTENT_TYPE, ContentType.TEXT_PLAIN.getMimeType()));
      request.setBody(123);
      request.setUrl(getHostAndPort(wmRuntimeInfo) + "/path");
      HttpCommonResult result = customApacheHttpClient.execute(request);
      assertThat(result).isNotNull();
      assertThat(result.status()).isEqualTo(200);
      assertThat(result.body()).isEqualTo("123");
    }
  }

  @Nested
  class AuthenticationTests {

    @Test
    public void shouldReturn200WithBody_whenGetWithBasicAuth(WireMockRuntimeInfo wmRuntimeInfo)
        throws Exception {
      stubFor(
          get("/path")
              .withBasicAuth("user", "password")
              .willReturn(
                  ok().withJsonBody(
                          JsonNodeFactory.instance
                              .objectNode()
                              .put("name", "John")
                              .put("age", 30)
                              .putNull("message"))));

      HttpCommonRequest request = new HttpCommonRequest();
      request.setMethod(HttpMethod.GET);
      request.setHeaders(Map.of("Accept", "application/json"));
      request.setUrl(getHostAndPort(wmRuntimeInfo) + "/path");
      request.setAuthentication(new BasicAuthentication("user", "password"));
      HttpCommonResult result = customApacheHttpClient.execute(request);
      assertThat(result).isNotNull();
      assertThat(result.status()).isEqualTo(200);
      JSONAssert.assertEquals(
          "{\"name\":\"John\",\"age\":30,\"message\":null}",
          objectMapper.writeValueAsString(result.body()),
          JSONCompareMode.STRICT);
    }

    @Test
    public void shouldReturn401_whenGetWithWrongBasicAuth(WireMockRuntimeInfo wmRuntimeInfo) {
      stubFor(
          get("/path")
              .withBasicAuth("user", "password")
              .willReturn(unauthorized().withBody("Unauthorized")));

      HttpCommonRequest request = new HttpCommonRequest();
      request.setMethod(HttpMethod.GET);
      request.setUrl(getHostAndPort(wmRuntimeInfo) + "/path");
      request.setAuthentication(new BasicAuthentication("user", "password"));
      ConnectorException e =
          assertThrows(ConnectorException.class, () -> customApacheHttpClient.execute(request));
      assertThat(e.getErrorCode()).isEqualTo("401");
      assertThat(e.getMessage()).contains("Unauthorized");
    }

    @Test
    public void shouldReturn200WithBody_whenGetWithBearerAuth(WireMockRuntimeInfo wmRuntimeInfo)
        throws Exception {
      stubFor(
          get("/path")
              .withHeader("Authorization", equalTo("Bearer token"))
              .willReturn(
                  ok().withJsonBody(
                          JsonNodeFactory.instance
                              .objectNode()
                              .put("name", "John")
                              .put("age", 30)
                              .putNull("message"))));

      HttpCommonRequest request = new HttpCommonRequest();
      request.setMethod(HttpMethod.GET);
      request.setAuthentication(new BearerAuthentication("token"));
      request.setUrl(getHostAndPort(wmRuntimeInfo) + "/path");
      HttpCommonResult result = customApacheHttpClient.execute(request);
      assertThat(result).isNotNull();
      assertThat(result.status()).isEqualTo(200);
      JSONAssert.assertEquals(
          "{\"name\":\"John\",\"age\":30,\"message\":null}",
          objectMapper.writeValueAsString(result.body()),
          JSONCompareMode.STRICT);
    }

    @Test
    public void shouldReturn200WithBody_whenGetWithApiKeyAuthInHeaders(
        WireMockRuntimeInfo wmRuntimeInfo) throws Exception {
      stubFor(
          get("/path")
              .withHeader("theName", equalTo("theValue"))
              .willReturn(
                  ok().withJsonBody(
                          JsonNodeFactory.instance
                              .objectNode()
                              .put("name", "John")
                              .put("age", 30)
                              .putNull("message"))));

      HttpCommonRequest request = new HttpCommonRequest();
      request.setMethod(HttpMethod.GET);
      request.setAuthentication(
          new ApiKeyAuthentication(ApiKeyLocation.HEADERS, "theName", "theValue"));
      request.setUrl(getHostAndPort(wmRuntimeInfo) + "/path");
      HttpCommonResult result = customApacheHttpClient.execute(request);
      assertThat(result).isNotNull();
      assertThat(result.status()).isEqualTo(200);
      JSONAssert.assertEquals(
          "{\"name\":\"John\",\"age\":30,\"message\":null}",
          objectMapper.writeValueAsString(result.body()),
          JSONCompareMode.STRICT);
    }

    @Test
    public void shouldReturn200WithBody_whenGetWithApiKeyAuthInQueryParams(
        WireMockRuntimeInfo wmRuntimeInfo) throws Exception {
      stubFor(
          get(urlPathEqualTo("/path"))
              .withQueryParam("theName", equalTo("theValue"))
              .willReturn(
                  ok().withJsonBody(
                          JsonNodeFactory.instance
                              .objectNode()
                              .put("name", "John")
                              .put("age", 30)
                              .putNull("message"))));

      HttpCommonRequest request = new HttpCommonRequest();
      request.setMethod(HttpMethod.GET);
      request.setAuthentication(
          new ApiKeyAuthentication(ApiKeyLocation.QUERY, "theName", "theValue"));
      request.setUrl(getHostAndPort(wmRuntimeInfo) + "/path");
      HttpCommonResult result = customApacheHttpClient.execute(request);
      assertThat(result).isNotNull();
      assertThat(result.status()).isEqualTo(200);
      JSONAssert.assertEquals(
          "{\"name\":\"John\",\"age\":30,\"message\":null}",
          objectMapper.writeValueAsString(result.body()),
          JSONCompareMode.STRICT);
    }

    @ParameterizedTest
    @ValueSource(strings = {OAuthConstants.BASIC_AUTH_HEADER, OAuthConstants.CREDENTIALS_BODY})
    public void shouldReturn200WithBody_whenGetWithOAuthAndCredentialsInBody(
        String credentialsLocation, WireMockRuntimeInfo wmRuntimeInfo) throws Exception {
      createAuthServer(credentialsLocation);
      stubFor(
          get("/path")
              .withHeader("Authorization", equalTo("Bearer token"))
              .willReturn(
                  ok().withJsonBody(
                          JsonNodeFactory.instance
                              .objectNode()
                              .put("name", "John")
                              .put("age", 30)
                              .putNull("message"))));

      HttpCommonRequest request = new HttpCommonRequest();
      request.setMethod(HttpMethod.GET);
      request.setAuthentication(
          new OAuthAuthentication(
              getHostAndPort(wmRuntimeInfo) + "/oauth",
              "clientId",
              "clientSecret",
              "theAudience",
              credentialsLocation,
              "read:resource"));
      request.setUrl(getHostAndPort(wmRuntimeInfo) + "/path");
      HttpCommonResult result = customApacheHttpClient.execute(request);
      assertThat(result).isNotNull();
      assertThat(result.status()).isEqualTo(200);
      JSONAssert.assertEquals(
          "{\"name\":\"John\",\"age\":30,\"message\":null}",
          objectMapper.writeValueAsString(result.body()),
          JSONCompareMode.STRICT);
    }

    @ParameterizedTest
    @ValueSource(strings = {OAuthConstants.BASIC_AUTH_HEADER, OAuthConstants.CREDENTIALS_BODY})
    public void shouldReturn401_whenGetWithOAuthReturns401(
        String credentialsLocation, WireMockRuntimeInfo wmRuntimeInfo) throws Exception {
      createFailingAuthServer(credentialsLocation);
      stubFor(
          get("/path")
              .withHeader("Authorization", equalTo("Bearer token"))
              .willReturn(
                  ok().withJsonBody(
                          JsonNodeFactory.instance
                              .objectNode()
                              .put("name", "John")
                              .put("age", 30)
                              .putNull("message"))));

      HttpCommonRequest request = new HttpCommonRequest();
      request.setMethod(HttpMethod.GET);
      request.setAuthentication(
          new OAuthAuthentication(
              getHostAndPort(wmRuntimeInfo) + "/oauth",
              "clientId",
              "clientSecret",
              "theAudience",
              credentialsLocation,
              "read:resource"));
      request.setUrl(getHostAndPort(wmRuntimeInfo) + "/path");
      var e = assertThrows(ConnectorException.class, () -> customApacheHttpClient.execute(request));
      assertThat(e).isNotNull();
      assertThat(e.getErrorCode()).isEqualTo("401");
      assertThat(e.getMessage()).contains("Unauthorized");
    }

    private void createAuthServer(String credentialsLocation) {
      var request =
          post("/oauth")
              .withHeader(
                  HttpHeaders.CONTENT_TYPE,
                  equalTo(ContentType.APPLICATION_FORM_URLENCODED.getMimeType()))
              .withFormParam("grant_type", equalTo("client_credentials"))
              .withFormParam("audience", equalTo("theAudience"))
              .withFormParam("scope", equalTo("read:resource"));
      if (OAuthConstants.CREDENTIALS_BODY.equals(credentialsLocation)) {
        request
            .withFormParam("client_id", equalTo("clientId"))
            .withFormParam("client_secret", equalTo("clientSecret"));
      } else {
        request.withBasicAuth("clientId", "clientSecret");
      }
      stubFor(
          request.willReturn(
              ok().withJsonBody(
                      JsonNodeFactory.instance
                          .objectNode()
                          .put("access_token", "token")
                          .put("token_type", "Bearer")
                          .put("expires_in", 3600))));
    }

    private void createFailingAuthServer(String credentialsLocation) {
      var request =
          post("/oauth")
              .withHeader(
                  HttpHeaders.CONTENT_TYPE,
                  equalTo(ContentType.APPLICATION_FORM_URLENCODED.getMimeType()))
              .withFormParam("grant_type", equalTo("client_credentials"))
              .withFormParam("audience", equalTo("theAudience"))
              .withFormParam("scope", equalTo("read:resource"));
      if (OAuthConstants.CREDENTIALS_BODY.equals(credentialsLocation)) {
        request
            .withFormParam("client_id", equalTo("clientId"))
            .withFormParam("client_secret", equalTo("clientSecret"));
      } else {
        request.withBasicAuth("clientId", "clientSecret");
      }
      stubFor(request.willReturn(unauthorized().withBody("Unauthorized")));
    }
  }
}
