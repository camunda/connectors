/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.http.base;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import io.camunda.connector.api.document.Document;
import io.camunda.connector.api.document.DocumentReturn;
import io.camunda.connector.api.document.DocumentReturnChoice;
import io.camunda.connector.api.document.RawPayload;
import io.camunda.connector.api.error.ConnectorException;
import io.camunda.connector.http.base.model.HttpCommonRequest;
import io.camunda.connector.http.base.model.HttpCommonResult;
import io.camunda.connector.http.base.model.HttpMethod;
import io.camunda.connector.runtime.core.document.DocumentFactoryImpl;
import io.camunda.connector.runtime.core.document.store.InMemoryDocumentStore;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Covers the {@code executeConnectorRequest(request, documentFactory, choice)} overload: a non-null
 * choice returns a {@link DocumentReturn}, a null choice takes the legacy {@code storeResponse}
 * path.
 */
@WireMockTest
public class HttpServiceDocumentReturnTest {

  private final HttpService httpService = new HttpService();
  private final InMemoryDocumentStore store = InMemoryDocumentStore.INSTANCE;
  private final DocumentFactoryImpl documentFactory = new DocumentFactoryImpl(store);

  @BeforeEach
  public void setUp() {
    store.clear();
  }

  private HttpCommonRequest getRequest(WireMockRuntimeInfo wm) {
    HttpCommonRequest request = new HttpCommonRequest();
    request.setMethod(HttpMethod.GET);
    request.setUrl(wm.getHttpBaseUrl() + "/endpoint");
    return request;
  }

  @Test
  void newPath_returnsDocumentReturn_withBodyStreamAndContentType(WireMockRuntimeInfo wm) {
    WireMock.stubFor(
        WireMock.get("/endpoint")
            .willReturn(
                WireMock.ok("{\"hello\":\"world\"}")
                    .withHeader("Content-Type", "application/json")));

    Object raw =
        httpService.executeConnectorRequest(
            getRequest(wm), documentFactory, DocumentReturnChoice.JSON);

    assertThat(raw).isInstanceOf(DocumentReturn.class);
    var documentReturn = (DocumentReturn<?>) raw;
    RawPayload payload = documentReturn.payload();
    assertThat(payload.contentType()).isEqualTo("application/json");
    try (InputStream stream = payload.stream()) {
      assertThat(new String(stream.readAllBytes(), StandardCharsets.UTF_8))
          .isEqualTo("{\"hello\":\"world\"}");
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  @Test
  void newPath_json_wrapPutsConvertedValueInBodyField(WireMockRuntimeInfo wm) {
    WireMock.stubFor(WireMock.get("/endpoint").willReturn(WireMock.ok("{}")));

    var documentReturn =
        (DocumentReturn<?>)
            httpService.executeConnectorRequest(
                getRequest(wm), documentFactory, DocumentReturnChoice.JSON);

    Map<String, Object> converted = Map.of("hello", "world");
    Object wrapped = documentReturn.wrap().apply(converted, DocumentReturnChoice.JSON);

    assertThat(wrapped).isInstanceOf(HttpCommonResult.class);
    HttpCommonResult result = (HttpCommonResult) wrapped;
    assertThat(result.body()).isEqualTo(converted);
    assertThat(result.document()).isNull();
    assertThat(result.status()).isEqualTo(200);
  }

  @Test
  void newPath_text_wrapPutsStringInBodyField(WireMockRuntimeInfo wm) {
    WireMock.stubFor(WireMock.get("/endpoint").willReturn(WireMock.ok("plain")));

    var documentReturn =
        (DocumentReturn<?>)
            httpService.executeConnectorRequest(
                getRequest(wm), documentFactory, DocumentReturnChoice.TEXT);

    Object wrapped = documentReturn.wrap().apply("plain text body", DocumentReturnChoice.TEXT);

    HttpCommonResult result = (HttpCommonResult) wrapped;
    assertThat(result.body()).isEqualTo("plain text body");
    assertThat(result.document()).isNull();
  }

  @Test
  void newPath_document_wrapPutsDocumentInDocumentField(WireMockRuntimeInfo wm) {
    WireMock.stubFor(WireMock.get("/endpoint").willReturn(WireMock.ok("binary")));

    var documentReturn =
        (DocumentReturn<?>)
            httpService.executeConnectorRequest(
                getRequest(wm), documentFactory, DocumentReturnChoice.DOCUMENT);

    Document document =
        documentFactory.create(
            io.camunda.connector.api.document.DocumentCreationRequest.from(
                    "binary".getBytes(StandardCharsets.UTF_8))
                .build());
    Object wrapped = documentReturn.wrap().apply(document, DocumentReturnChoice.DOCUMENT);

    HttpCommonResult result = (HttpCommonResult) wrapped;
    assertThat(result.document()).isSameAs(document);
    assertThat(result.body()).isNull();
  }

  @Test
  @SuppressWarnings("unchecked")
  void newPath_errorStatus_throwsWithResponseInErrorVariables(WireMockRuntimeInfo wm) {
    WireMock.stubFor(
        WireMock.get("/endpoint")
            .willReturn(
                WireMock.badRequest()
                    .withHeader("Content-Type", "application/json")
                    .withBody("{\"message\":\"custom message\",\"temp\":36}")));

    assertThatThrownBy(
            () ->
                httpService.executeConnectorRequest(
                    getRequest(wm), documentFactory, DocumentReturnChoice.JSON))
        .isInstanceOf(ConnectorException.class)
        .satisfies(
            ex -> {
              var ce = (ConnectorException) ex;
              assertThat(ce.getErrorCode()).isEqualTo("400");
              var response = (Map<String, Object>) ce.getErrorVariables().get("response");
              var body = (Map<String, Object>) response.get("body");
              assertThat(body).containsEntry("temp", 36).containsEntry("message", "custom message");
            });
  }

  @Test
  void legacyPath_choiceNull_storeResponseFalse_returnsBody(WireMockRuntimeInfo wm) {
    WireMock.stubFor(
        WireMock.get("/endpoint")
            .willReturn(
                WireMock.ok("{\"k\":\"v\"}").withHeader("Content-Type", "application/json")));
    HttpCommonRequest request = getRequest(wm);
    request.setStoreResponse(false);

    Object raw = httpService.executeConnectorRequest(request, documentFactory, null);

    assertThat(raw).isInstanceOf(HttpCommonResult.class);
    HttpCommonResult result = (HttpCommonResult) raw;
    assertThat(result.body()).isEqualTo(Map.of("k", "v"));
    assertThat(result.document()).isNull();
  }

  @Test
  void legacyPath_choiceNull_storeResponseTrue_returnsDocument(WireMockRuntimeInfo wm) {
    WireMock.stubFor(WireMock.get("/endpoint").willReturn(WireMock.ok("file-content")));
    HttpCommonRequest request = getRequest(wm);
    request.setStoreResponse(true);

    Object raw = httpService.executeConnectorRequest(request, documentFactory, null);

    assertThat(raw).isInstanceOf(HttpCommonResult.class);
    HttpCommonResult result = (HttpCommonResult) raw;
    assertThat(result.document()).isNotNull();
    assertThat(result.body()).isNull();
    assertThat(store.getDocuments()).isNotEmpty();
  }
}
