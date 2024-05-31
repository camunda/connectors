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

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.notFound;
import static com.github.tomakehurst.wiremock.client.WireMock.ok;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import io.camunda.connector.api.error.ConnectorException;
import io.camunda.connector.api.json.ConnectorsObjectMapperSupplier;
import io.camunda.connector.http.base.model.HttpCommonRequest;
import io.camunda.connector.http.base.model.HttpCommonResult;
import io.camunda.connector.http.base.model.HttpMethod;
import java.util.Map;
import org.apache.hc.core5.http.HttpStatus;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;
import wiremock.com.fasterxml.jackson.databind.node.JsonNodeFactory;

@WireMockTest
public class CustomApacheHttpClientTest {

  CustomApacheHttpClient customApacheHttpClient = CustomApacheHttpClient.getDefault();
  ObjectMapper objectMapper = ConnectorsObjectMapperSupplier.DEFAULT_MAPPER;

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

    @Test
    public void shouldReturn200WithBody_whenGetWithBodyXML(WireMockRuntimeInfo wmRuntimeInfo)
        throws Exception {
      stubFor(
          get("/path?format=xml")
              .willReturn(
                  ok().withBody(
                          "<note>\n"
                              + "  <to>Tove</to>\n"
                              + "  <from>Jani</from>\n"
                              + "  <heading>Reminder</heading>\n"
                              + "  <body>Don't forget me this weekend!</body>\n"
                              + "</note>")));

      HttpCommonRequest request = new HttpCommonRequest();
      request.setMethod(HttpMethod.GET);
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
    public void shouldReturn200WithoutBody_whenEmptyPost(WireMockRuntimeInfo wmRuntimeInfo)
        throws Exception {
      stubFor(post("/path").willReturn(ok()));

      HttpCommonRequest request = new HttpCommonRequest();
      request.setMethod(HttpMethod.POST);
      request.setUrl(getHostAndPort(wmRuntimeInfo) + "/path");
      HttpCommonResult result = customApacheHttpClient.execute(request);
      assertThat(result).isNotNull();
      assertThat(result.status()).isEqualTo(200);
    }
  }
}
