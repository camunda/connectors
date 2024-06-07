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

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.ok;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.unauthorized;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import io.camunda.connector.api.error.ConnectorException;
import io.camunda.connector.api.json.ConnectorsObjectMapperSupplier;
import io.camunda.connector.http.base.model.HttpCommonRequest;
import io.camunda.connector.http.base.model.HttpCommonResult;
import io.camunda.connector.http.base.model.HttpMethod;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;
import wiremock.com.fasterxml.jackson.databind.node.JsonNodeFactory;

@WireMockTest(extensionScanningEnabled = true)
public class HttpServiceTest {

  private static final CloudFunctionService cloudFunctionService = spy(new CloudFunctionService());
  private static final CloudFunctionService disabledCloudFunctionService =
      spy(new CloudFunctionService());
  private final HttpService httpService = new HttpService(cloudFunctionService);
  private final HttpService httpServiceWithoutCloudFunction =
      new HttpService(disabledCloudFunctionService);
  private final ObjectMapper objectMapper = ConnectorsObjectMapperSupplier.DEFAULT_MAPPER;

  @BeforeAll
  public static void setUp() {
    when(cloudFunctionService.isCloudFunctionEnabled()).thenReturn(true);
    when(disabledCloudFunctionService.isCloudFunctionEnabled()).thenReturn(false);
  }

  private String getHostAndPort(WireMockRuntimeInfo wmRuntimeInfo) {
    return "http://localhost:" + wmRuntimeInfo.getHttpPort();
  }

  private void stubCloudFunction(WireMockRuntimeInfo wmRuntimeInfo) {
    when(cloudFunctionService.getProxyFunctionUrl())
        .thenReturn(getHostAndPort(wmRuntimeInfo) + "/proxy");
    stubFor(
        post("/proxy")
            .willReturn(
                aResponse()
                    .withTransformers(
                        CloudFunctionResponseTransformer.CLOUD_FUNCTION_TRANSFORMER)));
  }

  @Nested
  class DisabledCloudFunctionTests {
    @Test
    public void shouldReturn200WithBody_whenGetRequestThroughHttpService(
        WireMockRuntimeInfo wmRuntimeInfo) throws Exception {
      stubFor(
          get("/path")
              .willReturn(
                  ok().withJsonBody(
                          JsonNodeFactory.instance
                              .objectNode()
                              .put("name", "John")
                              .put("age", 30)
                              .putNull("message"))));

      // given
      HttpCommonRequest request = new HttpCommonRequest();
      request.setMethod(HttpMethod.GET);
      request.setHeaders(Map.of("Accept", "application/json"));
      request.setUrl(getHostAndPort(wmRuntimeInfo) + "/path");

      // when
      HttpCommonResult result = httpServiceWithoutCloudFunction.executeConnectorRequest(request);

      // then
      assertThat(result).isNotNull();
      assertThat(result.status()).isEqualTo(200);
      JSONAssert.assertEquals(
          "{\"name\":\"John\",\"age\":30,\"message\":null}",
          objectMapper.writeValueAsString(result.body()),
          JSONCompareMode.STRICT);
    }

    @Test
    public void shouldReturn401_whenUnauthorizedGetRequestThroughHttpServiceWithBody(
        WireMockRuntimeInfo wmRuntimeInfo) throws Exception {
      stubFor(
          get("/path")
              .willReturn(
                  unauthorized()
                      .withStatusMessage("Unauthorized sorry!")
                      .withBody("Unauthorized sorry in the body!")));

      // given
      HttpCommonRequest request = new HttpCommonRequest();
      request.setMethod(HttpMethod.GET);
      request.setConnectionTimeoutInSeconds(10000);
      request.setReadTimeoutInSeconds(10000);
      request.setHeaders(Map.of("Accept", "application/json"));
      request.setUrl(getHostAndPort(wmRuntimeInfo) + "/path");

      // when
      var e =
          assertThrows(
              ConnectorException.class,
              () -> httpServiceWithoutCloudFunction.executeConnectorRequest(request));

      // then
      assertThat(e).isNotNull();
      assertThat(e.getErrorCode()).isEqualTo("401");
      assertThat(e.getMessage()).isEqualTo("Unauthorized sorry in the body!");
    }

    @Test
    public void shouldReturn401_whenUnauthorizedGetRequestThroughHttpServiceWithReason(
        WireMockRuntimeInfo wmRuntimeInfo) throws Exception {
      stubFor(get("/path").willReturn(unauthorized().withStatusMessage("Unauthorized sorry!")));

      // given
      HttpCommonRequest request = new HttpCommonRequest();
      request.setMethod(HttpMethod.GET);
      request.setConnectionTimeoutInSeconds(10000);
      request.setReadTimeoutInSeconds(10000);
      request.setHeaders(Map.of("Accept", "application/json"));
      request.setUrl(getHostAndPort(wmRuntimeInfo) + "/path");

      // when
      var e =
          assertThrows(
              ConnectorException.class,
              () -> httpServiceWithoutCloudFunction.executeConnectorRequest(request));

      // then
      assertThat(e).isNotNull();
      assertThat(e.getErrorCode()).isEqualTo("401");
      assertThat(e.getMessage()).isEqualTo("Unauthorized sorry!");
    }
  }

  @Nested
  class CloudFunctionTests {
    @Test
    public void shouldReturn200WithBody_whenGetRequestThroughCloudFunction(
        WireMockRuntimeInfo wmRuntimeInfo) throws Exception {
      stubCloudFunction(wmRuntimeInfo);
      stubFor(
          get("/path")
              .willReturn(
                  ok().withJsonBody(
                          JsonNodeFactory.instance
                              .objectNode()
                              .put("name", "John")
                              .put("age", 30)
                              .putNull("message"))));

      // given
      HttpCommonRequest request = new HttpCommonRequest();
      request.setMethod(HttpMethod.GET);
      request.setHeaders(Map.of("Accept", "application/json"));
      request.setUrl(getHostAndPort(wmRuntimeInfo) + "/path");

      // when
      HttpCommonResult result = httpService.executeConnectorRequest(request);

      // then
      verify(
          postRequestedFor(urlEqualTo("/proxy"))
              .withRequestBody(equalTo(objectMapper.writeValueAsString(request))));
      assertThat(result).isNotNull();
      assertThat(result.status()).isEqualTo(200);
      JSONAssert.assertEquals(
          "{\"name\":\"John\",\"age\":30,\"message\":null}",
          objectMapper.writeValueAsString(result.body()),
          JSONCompareMode.STRICT);
    }

    @Test
    public void shouldReturn401_whenUnauthorizedGetRequestThroughCloudFunctionWithBody(
        WireMockRuntimeInfo wmRuntimeInfo) throws Exception {
      stubCloudFunction(wmRuntimeInfo);
      stubFor(
          get("/path")
              .willReturn(
                  unauthorized()
                      .withStatusMessage("Unauthorized sorry!")
                      .withBody("Unauthorized sorry in the body!")));

      // given
      HttpCommonRequest request = new HttpCommonRequest();
      request.setMethod(HttpMethod.GET);
      request.setConnectionTimeoutInSeconds(10000);
      request.setReadTimeoutInSeconds(10000);
      request.setHeaders(Map.of("Accept", "application/json"));
      request.setUrl(getHostAndPort(wmRuntimeInfo) + "/path");

      // when
      var e =
          assertThrows(
              ConnectorException.class, () -> httpService.executeConnectorRequest(request));

      // then
      verify(
          postRequestedFor(urlEqualTo("/proxy"))
              .withRequestBody(equalTo(objectMapper.writeValueAsString(request))));
      assertThat(e).isNotNull();
      assertThat(e.getErrorCode()).isEqualTo("401");
      assertThat(e.getMessage()).isEqualTo("Unauthorized sorry in the body!");
    }

    @Test
    public void shouldReturn401_whenUnauthorizedGetRequestThroughCloudFunctionWithReason(
        WireMockRuntimeInfo wmRuntimeInfo) throws Exception {
      stubCloudFunction(wmRuntimeInfo);
      stubFor(get("/path").willReturn(unauthorized().withStatusMessage("Unauthorized sorry!")));

      // given
      HttpCommonRequest request = new HttpCommonRequest();
      request.setMethod(HttpMethod.GET);
      request.setConnectionTimeoutInSeconds(10000);
      request.setReadTimeoutInSeconds(10000);
      request.setHeaders(Map.of("Accept", "application/json"));
      request.setUrl(getHostAndPort(wmRuntimeInfo) + "/path");

      // when
      var e =
          assertThrows(
              ConnectorException.class, () -> httpService.executeConnectorRequest(request));

      // then
      verify(
          postRequestedFor(urlEqualTo("/proxy"))
              .withRequestBody(equalTo(objectMapper.writeValueAsString(request))));
      assertThat(e).isNotNull();
      assertThat(e.getErrorCode()).isEqualTo("401");
      assertThat(e.getMessage()).isEqualTo("Unauthorized sorry!");
    }
  }
}
