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
package io.camunda.connector.http.client;

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
import com.github.tomakehurst.wiremock.matching.MultipartValuePatternBuilder;
import io.camunda.connector.api.document.DocumentCreationRequest;
import io.camunda.connector.api.error.ConnectorException;
import io.camunda.connector.http.client.cloudfunction.CloudFunctionCredentials;
import io.camunda.connector.http.client.cloudfunction.CloudFunctionResponseTransformer;
import io.camunda.connector.http.client.cloudfunction.CloudFunctionService;
import io.camunda.connector.http.client.model.HttpClientRequest;
import io.camunda.connector.http.client.model.HttpClientResult;
import io.camunda.connector.http.client.model.HttpMethod;
import io.camunda.connector.jackson.ConnectorsObjectMapperSupplier;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpHeaders;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;
import wiremock.com.fasterxml.jackson.databind.node.JsonNodeFactory;

@WireMockTest(extensionScanningEnabled = true)
public class HttpClientServiceTest {

  private static final CloudFunctionCredentials cloudFunctionCredentials =
      mock(CloudFunctionCredentials.class);
  private static final CloudFunctionService cloudFunctionService =
      spy(new CloudFunctionService(cloudFunctionCredentials));
  private static final CloudFunctionService disabledCloudFunctionService =
      spy(new CloudFunctionService());
  private final HttpClientService httpClientService = new HttpClientService(cloudFunctionService);
  private final HttpClientService httpClientServiceWithoutCloudFunction =
      new HttpClientService(disabledCloudFunctionService);
  private final ObjectMapper objectMapper = ConnectorsObjectMapperSupplier.getCopy();
  private final TestDocumentFactory documentFactory = new TestDocumentFactory();

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
  public void shouldReturn200WithFileBodyParam_whenPostMultipartFormDataRequest(
      boolean cloudFunctionEnabled, WireMockRuntimeInfo wmRuntimeInfo) throws Exception {
    var documentBytes =
        Objects.requireNonNull(ClassLoader.getSystemResourceAsStream("__files/fileName.jpg"))
            .readAllBytes();
    var document =
        documentFactory.create(
            DocumentCreationRequest.from(documentBytes)
                .fileName("The filename")
                .contentType("image/jpeg")
                .build());
    var document2 =
        documentFactory.create(
            DocumentCreationRequest.from("the content".getBytes(StandardCharsets.UTF_8))
                .fileName("The filename 2")
                .contentType("text/plain")
                .build());

    stubCloudFunction(wmRuntimeInfo);
    HttpClientService httpClientService =
        cloudFunctionEnabled
            ? HttpClientServiceTest.this.httpClientService
            : httpClientServiceWithoutCloudFunction;
    stubFor(post("/upload").willReturn(ok()));

    // given
    HttpClientRequest request = new HttpClientRequest();
    request.setMethod(HttpMethod.POST);
    request.setBody(
        Map.of(
            "otherField",
            "otherValue",
            "myFile",
            document,
            "otherFiles",
            List.of(document, document2)));
    request.setHeaders(
        Map.of(HttpHeaders.CONTENT_TYPE, ContentType.MULTIPART_FORM_DATA.getMimeType()));
    request.setUrl(getHostAndPort(wmRuntimeInfo) + "/upload");

    // when
    HttpClientResult result =
        httpClientService.executeConnectorRequest(request, new TestDocumentFactory());

    // then
    assertThat(result).isNotNull();
    assertThat(result.status()).isEqualTo(200);
    assertThat(result.body()).isNull();
    assertThat(result.document()).isNull();

    verify(
        postRequestedFor(urlEqualTo("/upload"))
            .withHeader(
                "Content-Type", and(containing("multipart/form-data"), containing("boundary=")))
            .withRequestBodyPart(
                new MultipartValuePatternBuilder()
                    .withName("otherField")
                    .withBody(equalTo("otherValue"))
                    .build())
            .withRequestBodyPart(
                new MultipartValuePatternBuilder()
                    .withName("myFile")
                    .withFileName("The filename")
                    .withBody(binaryEqualTo(documentBytes))
                    .withHeader("Content-Type", equalTo("image/jpeg"))
                    .build())
            .withRequestBodyPart(
                new MultipartValuePatternBuilder()
                    .withName("otherFiles")
                    .withFileName("The filename")
                    .withBody(binaryEqualTo(documentBytes))
                    .withHeader("Content-Type", equalTo("image/jpeg"))
                    .build())
            .withRequestBodyPart(
                new MultipartValuePatternBuilder()
                    .withName("otherFiles")
                    .withFileName("The filename 2")
                    .withBody(equalTo("the content"))
                    .withHeader("Content-Type", equalTo("text/plain"))
                    .build()));
    if (cloudFunctionEnabled) {
      verify(
          postRequestedFor(urlEqualTo("/proxy"))
              .withRequestBody(equalTo(objectMapper.writeValueAsString(request))));
    }
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  public void shouldReturn200WithFileBodyParam_whenPostFileRequest(
      boolean cloudFunctionEnabled, WireMockRuntimeInfo wmRuntimeInfo) throws Exception {
    var documentBytes =
        Objects.requireNonNull(ClassLoader.getSystemResourceAsStream("__files/fileName.jpg"))
            .readAllBytes();
    var document = documentFactory.create(DocumentCreationRequest.from(documentBytes).build());

    stubCloudFunction(wmRuntimeInfo);
    HttpClientService httpClientService =
        cloudFunctionEnabled
            ? HttpClientServiceTest.this.httpClientService
            : httpClientServiceWithoutCloudFunction;
    stubFor(post("/upload").willReturn(ok()));

    // given
    HttpClientRequest request = new HttpClientRequest();
    request.setMethod(HttpMethod.POST);
    request.setBody(Map.of("myFile", document));
    request.setUrl(getHostAndPort(wmRuntimeInfo) + "/upload");

    // when
    HttpClientResult result =
        httpClientService.executeConnectorRequest(request, new TestDocumentFactory());

    // then
    assertThat(result).isNotNull();
    assertThat(result.status()).isEqualTo(200);
    assertThat(result.body()).isNull();
    assertThat(result.document()).isNull();

    verify(
        postRequestedFor(urlEqualTo("/upload"))
            .withRequestBody(
                matchingJsonPath(
                    "$.myFile", equalTo(Base64.getEncoder().encodeToString(documentBytes)))));
    if (cloudFunctionEnabled) {
      verify(
          postRequestedFor(urlEqualTo("/proxy"))
              .withRequestBody(equalTo(objectMapper.writeValueAsString(request))));
    }
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  public void shouldReturn200WithFileBody_whenGetFileRequest(
      boolean cloudFunctionEnabled, WireMockRuntimeInfo wmRuntimeInfo) throws Exception {
    stubCloudFunction(wmRuntimeInfo);
    HttpClientService httpClientService =
        cloudFunctionEnabled
            ? HttpClientServiceTest.this.httpClientService
            : httpClientServiceWithoutCloudFunction;
    stubFor(
        get("/download")
            .willReturn(
                ok().withHeader(HttpHeaders.CONTENT_TYPE, ContentType.IMAGE_JPEG.getMimeType())
                    .withBodyFile("fileName.jpg")));

    // given
    HttpClientRequest request = new HttpClientRequest();
    request.setMethod(HttpMethod.GET);
    request.setStoreResponse(true);
    request.setUrl(getHostAndPort(wmRuntimeInfo) + "/download");

    // when
    HttpClientResult result =
        httpClientService.executeConnectorRequest(request, new TestDocumentFactory());

    // then
    assertThat(result).isNotNull();
    assertThat(result.status()).isEqualTo(200);
    assertThat(result.body()).isNull();
    assertThat(result.document()).isNotNull();
    var content = documentFactory.resolve(result.document().reference());
    assertThat(content.asByteArray())
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
    HttpClientService httpClientService =
        cloudFunctionEnabled
            ? HttpClientServiceTest.this.httpClientService
            : httpClientServiceWithoutCloudFunction;

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
    HttpClientRequest request = new HttpClientRequest();
    request.setMethod(HttpMethod.POST);
    request.setBody(Map.of("name", "John", "age", 30, "message", "{\"key\":\"value\"}"));
    request.setHeaders(Map.of("Accept", "application/json"));
    request.setUrl(getHostAndPort(wmRuntimeInfo) + "/path");

    // when
    HttpClientResult result = httpClientService.executeConnectorRequest(request);

    // then
    assertThat(result).isNotNull();
    assertThat(result.status()).isEqualTo(200);
    assertThat(result.headers()).containsEntry("Set-Cookie", List.of("key=value", "key2=value2"));
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
    HttpClientService httpClientService =
        cloudFunctionEnabled
            ? HttpClientServiceTest.this.httpClientService
            : httpClientServiceWithoutCloudFunction;
    stubFor(
        get("/path")
            .willReturn(
                unauthorized()
                    .withHeader("Content-Type", "text/plain")
                    .withStatusMessage("Unauthorized sorry!")
                    .withBody("Unauthorized sorry in the body!")));

    // given
    HttpClientRequest request = new HttpClientRequest();
    request.setMethod(HttpMethod.GET);
    request.setHeaders(Map.of("Accept", "application/json"));
    request.setUrl(getHostAndPort(wmRuntimeInfo) + "/path");

    // when
    var e =
        assertThrows(
            ConnectorException.class, () -> httpClientService.executeConnectorRequest(request));

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
    HttpClientService httpClientService =
        cloudFunctionEnabled
            ? HttpClientServiceTest.this.httpClientService
            : httpClientServiceWithoutCloudFunction;
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
    HttpClientRequest request = new HttpClientRequest();
    request.setMethod(HttpMethod.GET);
    request.setHeaders(Map.of("Accept", "application/json"));
    request.setUrl(getHostAndPort(wmRuntimeInfo) + "/path");

    // when
    var e =
        assertThrows(
            ConnectorException.class, () -> httpClientService.executeConnectorRequest(request));

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
    HttpClientService httpClientService =
        cloudFunctionEnabled
            ? HttpClientServiceTest.this.httpClientService
            : httpClientServiceWithoutCloudFunction;
    stubFor(get("/path").willReturn(unauthorized().withStatusMessage("Unauthorized sorry!")));

    // given
    HttpClientRequest request = new HttpClientRequest();
    request.setMethod(HttpMethod.GET);
    request.setConnectionTimeoutInSeconds(10000);
    request.setReadTimeoutInSeconds(10000);
    request.setHeaders(Map.of("Accept", "application/json"));
    request.setUrl(getHostAndPort(wmRuntimeInfo) + "/path");

    // when
    var e =
        assertThrows(
            ConnectorException.class, () -> httpClientService.executeConnectorRequest(request));

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
    HttpClientService httpClientService =
        cloudFunctionEnabled
            ? HttpClientServiceTest.this.httpClientService
            : httpClientServiceWithoutCloudFunction;

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
    HttpClientRequest request = new HttpClientRequest();
    request.setMethod(HttpMethod.POST);
    request.setBody(Map.of("name", "John", "age", 30, "message", "{\"key\":\"value\"}"));
    Map<String, String> headers = new HashMap<>();
    headers.put("Content-Type", null);
    headers.put("Other", null);
    request.setHeaders(headers);
    request.setUrl(getHostAndPort(wmRuntimeInfo) + "/path");

    // when
    HttpClientResult result = httpClientService.executeConnectorRequest(request);

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
