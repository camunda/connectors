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
package io.camunda.connector.http.base;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import io.camunda.connector.api.error.ConnectorException;
import io.camunda.connector.api.json.ConnectorsObjectMapperSupplier;
import io.camunda.connector.http.base.cloudfunction.CloudFunctionCredentials;
import io.camunda.connector.http.base.cloudfunction.CloudFunctionResponseTransformer;
import io.camunda.connector.http.base.cloudfunction.CloudFunctionService;
import io.camunda.connector.http.base.model.HttpCommonRequest;
import io.camunda.connector.http.base.model.HttpCommonResult;
import io.camunda.connector.http.base.model.HttpMethod;
import io.camunda.document.reference.DocumentReference;
import io.camunda.document.store.InMemoryDocumentStore;
import java.util.HashMap;
import java.util.Map;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpHeaders;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;
import wiremock.com.fasterxml.jackson.databind.node.JsonNodeFactory;

@WireMockTest(extensionScanningEnabled = true)
public class HttpServiceTest {

  private static final CloudFunctionCredentials cloudFunctionCredentials =
      mock(CloudFunctionCredentials.class);
  private static final CloudFunctionService cloudFunctionService =
      spy(new CloudFunctionService(cloudFunctionCredentials));
  private static final CloudFunctionService disabledCloudFunctionService =
      spy(new CloudFunctionService());
  private final HttpService httpService = new HttpService(cloudFunctionService);
  private final HttpService httpServiceWithoutCloudFunction =
      new HttpService(disabledCloudFunctionService);
  private final ObjectMapper objectMapper = ConnectorsObjectMapperSupplier.getCopy();
  private final InMemoryDocumentStore store = InMemoryDocumentStore.INSTANCE;

  @BeforeAll
  public static void setUp() {
    when(cloudFunctionService.isCloudFunctionEnabled()).thenReturn(true);
    when(cloudFunctionCredentials.getOAuthToken(anyString())).thenReturn("token");
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

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  public void shouldReturn200WithFileBody_whenGetFileRequest(
      boolean cloudFunctionEnabled, WireMockRuntimeInfo wmRuntimeInfo) throws Exception {
    stubCloudFunction(wmRuntimeInfo);
    HttpService httpService =
        cloudFunctionEnabled ? HttpServiceTest.this.httpService : httpServiceWithoutCloudFunction;
    stubFor(
        get("/download")
            .willReturn(
                ok().withHeader(HttpHeaders.CONTENT_TYPE, ContentType.IMAGE_JPEG.getMimeType())
                    .withBodyFile("fileName.jpg")));

    // given
    HttpCommonRequest request = new HttpCommonRequest();
    request.setMethod(HttpMethod.GET);
    request.setStoreResponse(true);
    request.setUrl(getHostAndPort(wmRuntimeInfo) + "/download");

    // when
    HttpCommonResult result =
        httpService.executeConnectorRequest(request, new DocumentOutboundContext());

    // then
    assertThat(result).isNotNull();
    assertThat(result.status()).isEqualTo(200);
    assertThat(result.body()).isNull();
    assertThat(result.document()).isNotNull();
    var documentId =
        ((DocumentReference.CamundaDocumentReference) (result.document().reference())).documentId();
    var content = store.getDocuments().get(documentId);
    assertThat(content)
        .isEqualTo(getClass().getResourceAsStream("/__files/fileName.jpg").readAllBytes());
    verify(getRequestedFor(urlEqualTo("/download")));
    if (cloudFunctionEnabled) {
      verify(
          postRequestedFor(urlEqualTo("/proxy"))
              .withRequestBody(equalTo(objectMapper.writeValueAsString(request))));
    }
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  public void shouldReturn200WithBody_whenPostRequest(
      boolean cloudFunctionEnabled, WireMockRuntimeInfo wmRuntimeInfo) throws Exception {
    stubCloudFunction(wmRuntimeInfo);
    HttpService httpService =
        cloudFunctionEnabled ? HttpServiceTest.this.httpService : httpServiceWithoutCloudFunction;

    stubFor(
        post("/path")
            .willReturn(
                ok().withHeader("Set-Cookie", "key=value")
                    .withHeader("Set-Cookie", "key2=value2")
                    .withJsonBody(
                        JsonNodeFactory.instance
                            .objectNode()
                            .put("responseKey1", "value1")
                            .put("responseKey2", 40)
                            .putNull("responseKey3"))));

    // given
    HttpCommonRequest request = new HttpCommonRequest();
    request.setMethod(HttpMethod.POST);
    request.setBody(Map.of("name", "John", "age", 30, "message", "{\"key\":\"value\"}"));
    request.setHeaders(Map.of("Accept", "application/json"));
    request.setUrl(getHostAndPort(wmRuntimeInfo) + "/path");

    // when
    HttpCommonResult result = httpService.executeConnectorRequest(request);

    // then
    assertThat(result).isNotNull();
    assertThat(result.status()).isEqualTo(200);
    assertThat(result.headers()).contains(Map.entry("Set-Cookie", "key=value"));
    assertThat(result.headers()).doesNotContain(Map.entry("Set-Cookie", "key2=value2"));
    JSONAssert.assertEquals(
        "{\"responseKey1\":\"value1\",\"responseKey2\":40,\"responseKey3\":null}",
        objectMapper.writeValueAsString(result.body()),
        JSONCompareMode.STRICT);
    verify(
        postRequestedFor(urlEqualTo("/path"))
            .withHeader("Accept", equalTo("application/json"))
            .withRequestBody(
                matchingJsonPath("$.name", equalTo("John"))
                    .and(matchingJsonPath("$.message", equalTo("{\"key\":\"value\"}")))
                    .and(matchingJsonPath("$.age", equalTo("30")))));
    if (cloudFunctionEnabled) {
      verify(
          postRequestedFor(urlEqualTo("/proxy"))
              .withRequestBody(equalTo(objectMapper.writeValueAsString(request))));
    }
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  public void shouldReturn401_whenUnauthorizedGetRequestWithBody(
      boolean cloudFunctionEnabled, WireMockRuntimeInfo wmRuntimeInfo) throws Exception {
    stubCloudFunction(wmRuntimeInfo);
    HttpService httpService =
        cloudFunctionEnabled ? HttpServiceTest.this.httpService : httpServiceWithoutCloudFunction;
    stubFor(
        get("/path")
            .willReturn(
                unauthorized()
                    .withHeader("Content-Type", "text/plain")
                    .withStatusMessage("Unauthorized sorry!")
                    .withBody("Unauthorized sorry in the body!")));

    // given
    HttpCommonRequest request = new HttpCommonRequest();
    request.setMethod(HttpMethod.GET);
    request.setHeaders(Map.of("Accept", "application/json"));
    request.setUrl(getHostAndPort(wmRuntimeInfo) + "/path");

    // when
    var e =
        assertThrows(ConnectorException.class, () -> httpService.executeConnectorRequest(request));

    // then
    assertThat(e).isNotNull();
    assertThat(e.getErrorCode()).isEqualTo("401");
    assertThat(e.getMessage()).isEqualTo("Unauthorized sorry!");
    var response = (Map<String, Object>) e.getErrorVariables().get("response");
    assertThat((Map) response.get("headers")).containsEntry("Content-Type", "text/plain");
    assertThat(response).containsEntry("body", "Unauthorized sorry in the body!");
    if (cloudFunctionEnabled) {
      verify(
          postRequestedFor(urlEqualTo("/proxy"))
              .withRequestBody(equalTo(objectMapper.writeValueAsString(request))));
    }
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  public void shouldReturn401_whenUnauthorizedGetRequestWithJsonBody(
      boolean cloudFunctionEnabled, WireMockRuntimeInfo wmRuntimeInfo) throws Exception {
    stubCloudFunction(wmRuntimeInfo);
    HttpService httpService =
        cloudFunctionEnabled ? HttpServiceTest.this.httpService : httpServiceWithoutCloudFunction;
    stubFor(
        get("/path")
            .willReturn(
                unauthorized()
                    .withHeader("Content-Type", "application/json")
                    .withStatusMessage("Unauthorized sorry!")
                    .withJsonBody(
                        JsonNodeFactory.instance
                            .objectNode()
                            .put("responseKey1", "value1")
                            .put("responseKey2", 40)
                            .putNull("responseKey3"))));

    // given
    HttpCommonRequest request = new HttpCommonRequest();
    request.setMethod(HttpMethod.GET);
    request.setHeaders(Map.of("Accept", "application/json"));
    request.setUrl(getHostAndPort(wmRuntimeInfo) + "/path");

    // when
    var e =
        assertThrows(ConnectorException.class, () -> httpService.executeConnectorRequest(request));

    // then
    assertThat(e).isNotNull();
    assertThat(e.getErrorCode()).isEqualTo("401");
    assertThat(e.getMessage()).isEqualTo("Unauthorized sorry!");
    var response = (Map<String, Object>) e.getErrorVariables().get("response");
    assertThat((Map) response.get("headers")).containsEntry("Content-Type", "application/json");
    var expectedBody = new HashMap<>();
    expectedBody.put("responseKey1", "value1");
    expectedBody.put("responseKey2", 40);
    expectedBody.put("responseKey3", null);
    assertThat((Map) response.get("body")).containsAllEntriesOf(expectedBody);
    if (cloudFunctionEnabled) {
      verify(
          postRequestedFor(urlEqualTo("/proxy"))
              .withRequestBody(equalTo(objectMapper.writeValueAsString(request))));
    }
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  public void shouldReturn401_whenUnauthorizedGetRequestWithReason(
      boolean cloudFunctionEnabled, WireMockRuntimeInfo wmRuntimeInfo) throws Exception {
    stubCloudFunction(wmRuntimeInfo);
    HttpService httpService =
        cloudFunctionEnabled ? HttpServiceTest.this.httpService : httpServiceWithoutCloudFunction;
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
        assertThrows(ConnectorException.class, () -> httpService.executeConnectorRequest(request));

    // then
    assertThat(e).isNotNull();
    assertThat(e.getErrorCode()).isEqualTo("401");
    assertThat(e.getMessage()).isEqualTo("Unauthorized sorry!");
    if (cloudFunctionEnabled) {
      verify(
          postRequestedFor(urlEqualTo("/proxy"))
              .withRequestBody(equalTo(objectMapper.writeValueAsString(request))));
    }
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  public void shouldReturn200WithBody_whenPostRequestWithNullContentType(
      boolean cloudFunctionEnabled, WireMockRuntimeInfo wmRuntimeInfo) throws Exception {
    stubCloudFunction(wmRuntimeInfo);
    HttpService httpService =
        cloudFunctionEnabled ? HttpServiceTest.this.httpService : httpServiceWithoutCloudFunction;

    stubFor(
        post("/path")
            .willReturn(
                ok().withJsonBody(
                        JsonNodeFactory.instance
                            .objectNode()
                            .put("responseKey1", "value1")
                            .put("responseKey2", 40)
                            .putNull("responseKey3"))));

    // given
    HttpCommonRequest request = new HttpCommonRequest();
    request.setMethod(HttpMethod.POST);
    request.setBody(Map.of("name", "John", "age", 30, "message", "{\"key\":\"value\"}"));
    Map<String, String> headers = new HashMap<>();
    headers.put("Content-Type", null);
    headers.put("Other", null);
    request.setHeaders(headers);
    request.setUrl(getHostAndPort(wmRuntimeInfo) + "/path");

    // when
    HttpCommonResult result = httpService.executeConnectorRequest(request);

    // then
    assertThat(result).isNotNull();
    assertThat(result.status()).isEqualTo(200);
    JSONAssert.assertEquals(
        "{\"responseKey1\":\"value1\",\"responseKey2\":40,\"responseKey3\":null}",
        objectMapper.writeValueAsString(result.body()),
        JSONCompareMode.STRICT);
    verify(
        postRequestedFor(urlEqualTo("/path"))
            .withHeader("Content-Type", equalTo("application/json"))
            .withHeader("Other", equalTo(""))
            .withRequestBody(
                matchingJsonPath("$.name", equalTo("John"))
                    .and(matchingJsonPath("$.message", equalTo("{\"key\":\"value\"}")))
                    .and(matchingJsonPath("$.age", equalTo("30")))));
    if (cloudFunctionEnabled) {
      verify(
          postRequestedFor(urlEqualTo("/proxy"))
              .withRequestBody(equalTo(objectMapper.writeValueAsString(request))));
    }
  }
}
