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
import com.github.tomakehurst.wiremock.matching.MultipartValuePatternBuilder;
import io.camunda.connector.api.document.Document;
import io.camunda.connector.api.document.DocumentCreationRequest;
import io.camunda.connector.api.document.DocumentFactory;
import io.camunda.connector.api.document.DocumentReference;
import io.camunda.connector.http.base.model.HttpCommonRequest;
import io.camunda.connector.http.base.model.HttpCommonResult;
import io.camunda.connector.http.base.model.HttpMethod;
import io.camunda.connector.runtime.core.document.CamundaDocument;
import io.camunda.connector.runtime.core.document.DocumentFactoryImpl;
import io.camunda.connector.runtime.core.document.store.InMemoryDocumentStore;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpHeaders;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@WireMockTest
public class HttpServiceDocumentTest {

  private final HttpService customApacheHttpClient = new HttpService();
  private final InMemoryDocumentStore store = InMemoryDocumentStore.INSTANCE;
  private final DocumentFactory documentFactory = new DocumentFactoryImpl(store);

  @BeforeEach
  public void setUp() {
    store.clear();
  }

  @Nested
  class DocumentDownloadTests {

    @Test
    public void shouldNotStoreDocument_whenErrorOccurs(WireMockRuntimeInfo wmRuntimeInfo) {
      WireMock.stubFor(
          WireMock.post("/path")
              .withMultipartRequestBody(WireMock.aMultipart())
              .willReturn(WireMock.created()));
      HttpCommonRequest request = new HttpCommonRequest();
      request.setMethod(HttpMethod.POST);
      request.setHeaders(Map.of("Content-Type", ContentType.MULTIPART_FORM_DATA.getMimeType()));
      request.setUrl(wmRuntimeInfo.getHttpBaseUrl() + "/path");
      request.setStoreResponse(true);
      assertThatThrownBy(
              () ->
                  customApacheHttpClient.executeConnectorRequest(
                      request,
                      new DocumentFactory() {
                        @Override
                        public Document resolve(DocumentReference reference) {
                          return null;
                        }

                        @Override
                        public Document create(DocumentCreationRequest request) {
                          throw new RuntimeException("Cannot create document");
                        }
                      }))
          .hasMessage("Error while executing an HTTP request: Failed to create document: Cannot create document");
      var documents = store.getDocuments();
      assertThat(documents).isEmpty();
    }

    @Test
    public void shouldStoreDocument_whenStoreResponseEnabled(WireMockRuntimeInfo wmRuntimeInfo)
        throws IOException {
      WireMock.stubFor(
          WireMock.get("/download")
              .willReturn(
                  WireMock.ok()
                      .withHeader(HttpHeaders.CONTENT_TYPE, ContentType.IMAGE_JPEG.getMimeType())
                      .withBodyFile("fileName.jpg")));
      HttpCommonRequest request = new HttpCommonRequest();
      request.setMethod(HttpMethod.GET);
      request.setStoreResponse(true);
      request.setUrl(wmRuntimeInfo.getHttpBaseUrl() + "/download");
      HttpCommonResult result =
          customApacheHttpClient.executeConnectorRequest(request, documentFactory);
      assertThat(result).isNotNull();
      assertThat(result.status()).isEqualTo(200);
      assertThat(result.headers().get(HttpHeaders.CONTENT_TYPE))
          .isEqualTo(ContentType.IMAGE_JPEG.getMimeType());
      assertThat(result.body()).isNull();
      var documents = store.getDocuments();
      assertThat(documents).hasSize(1);
      var responseDocument = result.document();
      DocumentReference.CamundaDocumentReference responseDocumentReference =
          (DocumentReference.CamundaDocumentReference) responseDocument.reference();
      assertThat(responseDocumentReference).isNotNull();
      var documentContent = documents.get(responseDocumentReference.getDocumentId());
      assertThat(documentContent)
          .isEqualTo(getClass().getResourceAsStream("/__files/fileName.jpg").readAllBytes());
    }
  }

  @Nested
  class DocumentUploadTests {

    @Test
    public void shouldReturn201_whenUploadDocument(WireMockRuntimeInfo wmRuntimeInfo) {
      WireMock.stubFor(
          WireMock.post("/path")
              .withMultipartRequestBody(WireMock.aMultipart())
              .willReturn(WireMock.created()));
      var ref =
          store.createDocument(
              DocumentCreationRequest.from("The content of this file".getBytes())
                  .fileName("file.txt")
                  .contentType("text/plain")
                  .build());
      HttpCommonRequest request = new HttpCommonRequest();
      request.setMethod(HttpMethod.POST);
      var bodyMap = new HashMap<>();
      bodyMap.put("document", new CamundaDocument(ref.getMetadata(), ref, store));
      bodyMap.put("otherField", "otherValue");
      bodyMap.put("nullField", null);
      request.setHeaders(Map.of("Content-Type", ContentType.MULTIPART_FORM_DATA.getMimeType()));
      request.setUrl(wmRuntimeInfo.getHttpBaseUrl() + "/path");
      request.setBody(bodyMap);
      HttpCommonResult result =
          customApacheHttpClient.executeConnectorRequest(request, documentFactory);
      assertThat(result).isNotNull();
      assertThat(result.status()).isEqualTo(201);

      WireMock.verify(
          WireMock.postRequestedFor(WireMock.urlEqualTo("/path"))
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
                      .withName("document")
                      .withBody(WireMock.equalTo("The content of this file"))
                      .withHeader("Content-Type", WireMock.equalTo("text/plain"))
                      .build()));
    }

    @Test
    public void shouldReturn201_whenUploadDocuments(WireMockRuntimeInfo wmRuntimeInfo) {
      WireMock.stubFor(
          WireMock.post("/path")
              .withMultipartRequestBody(WireMock.aMultipart())
              .willReturn(WireMock.created()));
      var ref =
          store.createDocument(
              DocumentCreationRequest.from("The content of this file".getBytes())
                  .fileName("file.txt")
                  .contentType("text/plain")
                  .build());
      var ref2 =
          store.createDocument(
              DocumentCreationRequest.from("The content of this file 2".getBytes())
                  .fileName("file2.txt")
                  .contentType("text/plain")
                  .build());
      HttpCommonRequest request = new HttpCommonRequest();
      request.setMethod(HttpMethod.POST);
      var bodyMap = new HashMap<>();
      bodyMap.put(
          "documents",
          List.of(
              new CamundaDocument(ref.getMetadata(), ref, store),
              new CamundaDocument(ref2.getMetadata(), ref2, store)));
      bodyMap.put("otherField", "otherValue");
      bodyMap.put("nullField", null);
      request.setHeaders(Map.of("Content-Type", ContentType.MULTIPART_FORM_DATA.getMimeType()));
      request.setUrl(wmRuntimeInfo.getHttpBaseUrl() + "/path");
      request.setBody(bodyMap);
      HttpCommonResult result =
          customApacheHttpClient.executeConnectorRequest(request, documentFactory);
      assertThat(result).isNotNull();
      assertThat(result.status()).isEqualTo(201);

      WireMock.verify(
          WireMock.postRequestedFor(WireMock.urlEqualTo("/path"))
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
                      .withName("documents")
                      .withBody(WireMock.equalTo("The content of this file"))
                      .withHeader("Content-Type", WireMock.equalTo("text/plain"))
                      .build())
              .withRequestBodyPart(
                  new MultipartValuePatternBuilder()
                      .withName("documents")
                      .withBody(WireMock.equalTo("The content of this file 2"))
                      .withHeader("Content-Type", WireMock.equalTo("text/plain"))
                      .build()));
    }
  }
}
