/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.http.base;

import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import com.github.tomakehurst.wiremock.matching.MultipartValuePatternBuilder;
import io.camunda.connector.api.document.Document;
import io.camunda.connector.api.document.DocumentCreationRequest;
import io.camunda.connector.api.document.DocumentFactory;
import io.camunda.connector.api.document.DocumentReference;
import io.camunda.connector.api.error.ConnectorException;
import io.camunda.connector.http.base.model.HttpCommonRequest;
import io.camunda.connector.http.base.model.HttpCommonResult;
import io.camunda.connector.http.base.model.HttpMethod;
import io.camunda.connector.http.client.HttpClientObjectMapperSupplier;
import io.camunda.connector.runtime.test.document.TestDocumentFactory;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpHeaders;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;
import wiremock.com.fasterxml.jackson.databind.node.JsonNodeFactory;

@WireMockTest(extensionScanningEnabled = true)
public class HttpServiceTest {

  private final HttpService httpService = new HttpService();
  private final ObjectMapper objectMapper = HttpClientObjectMapperSupplier.getCopy();
  private final TestDocumentFactory documentFactory = new TestDocumentFactory();
  private final DocumentFactory failingDocumentFactory =
      new DocumentFactory() {
        @Override
        public Document resolve(DocumentReference reference) {
          throw new RuntimeException("Document resolution failed");
        }

        @Override
        public Document create(DocumentCreationRequest request) {
          throw new RuntimeException("Document creation failed");
        }
      };

  private String getHostAndPort(WireMockRuntimeInfo wmRuntimeInfo) {
    return "http://localhost:" + wmRuntimeInfo.getHttpPort();
  }

  @Test
  public void shouldReturn200WithFileBodyParam_whenPostMultipartFormDataRequest(
      WireMockRuntimeInfo wmRuntimeInfo) throws Exception {
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

    WireMock.stubFor(WireMock.post("/upload").willReturn(WireMock.ok()));

    // given
    HttpCommonRequest request = new HttpCommonRequest();
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
    HttpCommonResult result = httpService.executeConnectorRequest(request, documentFactory);

    // then
    Assertions.assertThat(result).isNotNull();
    Assertions.assertThat(result.status()).isEqualTo(200);
    Assertions.assertThat(result.body()).isNull();

    WireMock.verify(
        WireMock.postRequestedFor(WireMock.urlEqualTo("/upload"))
            .withHeader(
                "Content-Type",
                WireMock.and(
                    WireMock.containing("multipart/form-data"), WireMock.containing("boundary=")))
            .withRequestBodyPart(
                new MultipartValuePatternBuilder()
                    .withName("otherField")
                    .withBody(WireMock.equalTo("otherValue"))
                    .build())
            .withRequestBodyPart(
                new MultipartValuePatternBuilder()
                    .withName("myFile")
                    .withFileName("The filename")
                    .withBody(WireMock.binaryEqualTo(documentBytes))
                    .withHeader("Content-Type", WireMock.equalTo("image/jpeg"))
                    .build())
            .withRequestBodyPart(
                new MultipartValuePatternBuilder()
                    .withName("otherFiles")
                    .withFileName("The filename")
                    .withBody(WireMock.binaryEqualTo(documentBytes))
                    .withHeader("Content-Type", WireMock.equalTo("image/jpeg"))
                    .build())
            .withRequestBodyPart(
                new MultipartValuePatternBuilder()
                    .withName("otherFiles")
                    .withFileName("The filename 2")
                    .withBody(WireMock.equalTo("the content"))
                    .withHeader("Content-Type", WireMock.equalTo("text/plain"))
                    .build()));
  }

  @Test
  public void shouldReturn200WithFileBodyParam_whenPostFileRequest(
      WireMockRuntimeInfo wmRuntimeInfo) throws Exception {
    var documentBytes =
        Objects.requireNonNull(ClassLoader.getSystemResourceAsStream("__files/fileName.jpg"))
            .readAllBytes();
    var document = documentFactory.create(DocumentCreationRequest.from(documentBytes).build());

    WireMock.stubFor(WireMock.post("/upload").willReturn(WireMock.ok()));

    // given
    HttpCommonRequest request = new HttpCommonRequest();
    request.setMethod(HttpMethod.POST);
    request.setBody(Map.of("myFile", document));
    request.setUrl(getHostAndPort(wmRuntimeInfo) + "/upload");

    // when
    HttpCommonResult result = httpService.executeConnectorRequest(request, documentFactory);

    // then
    Assertions.assertThat(result).isNotNull();
    Assertions.assertThat(result.status()).isEqualTo(200);
    Assertions.assertThat(result.body()).isNull();

    WireMock.verify(
        WireMock.postRequestedFor(WireMock.urlEqualTo("/upload"))
            .withRequestBody(
                WireMock.matchingJsonPath(
                    "$.myFile",
                    WireMock.equalTo(Base64.getEncoder().encodeToString(documentBytes)))));
  }

  @Test
  public void shouldReturn200WithFileBody_whenGetFileRequest(WireMockRuntimeInfo wmRuntimeInfo)
      throws Exception {
    WireMock.stubFor(
        WireMock.get("/download")
            .willReturn(
                WireMock.ok()
                    .withHeader(HttpHeaders.CONTENT_TYPE, ContentType.IMAGE_JPEG.getMimeType())
                    .withBodyFile("fileName.jpg")));

    // given
    HttpCommonRequest request = new HttpCommonRequest();
    request.setMethod(HttpMethod.GET);
    request.setStoreResponse(true);
    request.setUrl(getHostAndPort(wmRuntimeInfo) + "/download");

    // when
    HttpCommonResult result = httpService.executeConnectorRequest(request, documentFactory);

    // then
    Assertions.assertThat(result).isNotNull();
    Assertions.assertThat(result.status()).isEqualTo(200);
    Assertions.assertThat(result.body()).isNull();
    Assertions.assertThat(result.document()).isNotNull();
    var content = documentFactory.resolve(result.document().reference());
    Assertions.assertThat(content.asByteArray())
        .isEqualTo(getClass().getResourceAsStream("/__files/fileName.jpg").readAllBytes());
    WireMock.verify(WireMock.getRequestedFor(WireMock.urlEqualTo("/download")));
  }

  @Test
  public void shouldReturn200WithBody_whenPostRequest(WireMockRuntimeInfo wmRuntimeInfo)
      throws Exception {

    WireMock.stubFor(
        WireMock.post("/path")
            .willReturn(
                WireMock.ok()
                    .withHeader("Set-Cookie", "key=value")
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
    Assertions.assertThat(result).isNotNull();
    Assertions.assertThat(result.status()).isEqualTo(200);
    Assertions.assertThat(result.headers())
        .containsEntry("Set-Cookie", List.of("key=value", "key2=value2"));
    JSONAssert.assertEquals(
        "{\"responseKey1\":\"value1\",\"responseKey2\":40,\"responseKey3\":null}",
        objectMapper.writeValueAsString(result.body()),
        JSONCompareMode.STRICT);
    WireMock.verify(
        WireMock.postRequestedFor(WireMock.urlEqualTo("/path"))
            .withHeader("Accept", WireMock.equalTo("application/json"))
            .withRequestBody(
                WireMock.matchingJsonPath("$.name", WireMock.equalTo("John"))
                    .and(
                        WireMock.matchingJsonPath(
                            "$.message", WireMock.equalTo("{\"key\":\"value\"}")))
                    .and(WireMock.matchingJsonPath("$.age", WireMock.equalTo("30")))));
  }

  @Test
  public void shouldReturn401_whenUnauthorizedGetRequestWithBody(
      WireMockRuntimeInfo wmRuntimeInfo) {
    WireMock.stubFor(
        WireMock.get("/path")
            .willReturn(
                WireMock.unauthorized()
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
        assertThrows(
            ConnectorException.class,
            () -> httpService.executeConnectorRequest(request, documentFactory));

    // then
    Assertions.assertThat(e).isNotNull();
    Assertions.assertThat(e.getErrorCode()).isEqualTo("401");
    Assertions.assertThat(e.getMessage()).isEqualTo("Unauthorized sorry!");
    var response = (Map<String, Object>) e.getErrorVariables().get("response");
    Assertions.assertThat((Map) response.get("headers"))
        .containsEntry("Content-Type", "text/plain");
    Assertions.assertThat(response).containsEntry("body", "Unauthorized sorry in the body!");
  }

  @Test
  public void shouldReturn500WithErrorMessage_whenCreateFileRequestFails(
      WireMockRuntimeInfo wmRuntimeInfo) {
    WireMock.stubFor(
        WireMock.get("/download")
            .willReturn(
                WireMock.ok()
                    .withHeader(HttpHeaders.CONTENT_TYPE, ContentType.IMAGE_JPEG.getMimeType())
                    .withBodyFile("fileName.jpg")));

    // given
    HttpCommonRequest request = new HttpCommonRequest();
    request.setMethod(HttpMethod.GET);
    request.setStoreResponse(true);
    request.setUrl(getHostAndPort(wmRuntimeInfo) + "/download");

    // when
    var e =
        assertThrows(
            ConnectorException.class,
            () -> httpService.executeConnectorRequest(request, failingDocumentFactory));

    // then
    Assertions.assertThat(e).isNotNull();
    Assertions.assertThat(e.getErrorCode()).isEqualTo("500");
    Assertions.assertThat(e.getMessage())
        .isEqualTo("Failed to create document: Document creation failed");
  }

  @Test
  public void shouldReturn401_whenUnauthorizedGetRequestWithJsonBody(
      WireMockRuntimeInfo wmRuntimeInfo) {
    WireMock.stubFor(
        WireMock.get("/path")
            .willReturn(
                WireMock.unauthorized()
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
        assertThrows(
            ConnectorException.class,
            () -> httpService.executeConnectorRequest(request, documentFactory));
    // then
    Assertions.assertThat(e).isNotNull();
    Assertions.assertThat(e.getErrorCode()).isEqualTo("401");
    Assertions.assertThat(e.getMessage()).isEqualTo("Unauthorized sorry!");
    var response = (Map<String, Object>) e.getErrorVariables().get("response");
    Assertions.assertThat((Map) response.get("headers"))
        .containsEntry("Content-Type", "application/json");
    var expectedBody = new HashMap<>();
    expectedBody.put("responseKey1", "value1");
    expectedBody.put("responseKey2", 40);
    expectedBody.put("responseKey3", null);
    Assertions.assertThat((Map) response.get("body")).containsAllEntriesOf(expectedBody);
  }

  @Test
  public void shouldReturn401_whenUnauthorizedGetRequestWithReason(
      WireMockRuntimeInfo wmRuntimeInfo) {
    WireMock.stubFor(
        WireMock.get("/path")
            .willReturn(WireMock.unauthorized().withStatusMessage("Unauthorized sorry!")));

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
            () -> httpService.executeConnectorRequest(request, documentFactory));

    // then
    Assertions.assertThat(e).isNotNull();
    Assertions.assertThat(e.getErrorCode()).isEqualTo("401");
    Assertions.assertThat(e.getMessage()).isEqualTo("Unauthorized sorry!");
  }

  @Test
  public void shouldReturn200WithBody_whenPostRequestWithNullContentType(
      WireMockRuntimeInfo wmRuntimeInfo) throws Exception {

    WireMock.stubFor(
        WireMock.post("/path")
            .willReturn(
                WireMock.ok()
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
    Map<String, String> headers = new HashMap<>();
    headers.put("Content-Type", null);
    headers.put("Other", null);
    request.setHeaders(headers);
    request.setUrl(getHostAndPort(wmRuntimeInfo) + "/path");

    // when
    HttpCommonResult result = httpService.executeConnectorRequest(request, documentFactory);

    // then
    Assertions.assertThat(result).isNotNull();
    Assertions.assertThat(result.status()).isEqualTo(200);
    JSONAssert.assertEquals(
        "{\"responseKey1\":\"value1\",\"responseKey2\":40,\"responseKey3\":null}",
        objectMapper.writeValueAsString(result.body()),
        JSONCompareMode.STRICT);
    WireMock.verify(
        WireMock.postRequestedFor(WireMock.urlEqualTo("/path"))
            .withHeader("Content-Type", WireMock.equalTo("application/json"))
            .withHeader("Other", WireMock.equalTo(""))
            .withRequestBody(
                WireMock.matchingJsonPath("$.name", WireMock.equalTo("John"))
                    .and(
                        WireMock.matchingJsonPath(
                            "$.message", WireMock.equalTo("{\"key\":\"value\"}")))
                    .and(WireMock.matchingJsonPath("$.age", WireMock.equalTo("30")))));
  }
}
