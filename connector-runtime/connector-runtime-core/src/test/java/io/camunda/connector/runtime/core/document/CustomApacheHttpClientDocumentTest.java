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
package io.camunda.connector.runtime.core.document;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.client.WireMock.aMultipart;
import static com.github.tomakehurst.wiremock.client.WireMock.and;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.created;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import com.github.tomakehurst.wiremock.matching.MultipartValuePatternBuilder;
import io.camunda.connector.api.document.Document;
import io.camunda.connector.api.document.DocumentCreationRequest;
import io.camunda.connector.api.document.DocumentFactory;
import io.camunda.connector.api.document.DocumentReference;
import io.camunda.connector.http.client.ExecutionEnvironment;
import io.camunda.connector.http.client.client.apache.CustomApacheHttpClient;
import io.camunda.connector.http.client.model.HttpClientRequest;
import io.camunda.connector.http.client.model.HttpClientResult;
import io.camunda.connector.http.client.model.HttpMethod;
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
public class CustomApacheHttpClientDocumentTest {

  private final CustomApacheHttpClient customApacheHttpClient = new CustomApacheHttpClient();
  private final InMemoryDocumentStore store = InMemoryDocumentStore.INSTANCE;

  @BeforeEach
  public void setUp() {
    store.clear();
  }

  @Nested
  class DocumentDownloadTests {

    @Test
    public void shouldNotStoreDocument_whenErrorOccurs(WireMockRuntimeInfo wmRuntimeInfo) {
      stubFor(post("/path").withMultipartRequestBody(aMultipart()).willReturn(created()));
      HttpClientRequest request = new HttpClientRequest();
      request.setMethod(HttpMethod.POST);
      request.setHeaders(Map.of("Content-Type", ContentType.MULTIPART_FORM_DATA.getMimeType()));
      request.setUrl(wmRuntimeInfo.getHttpBaseUrl() + "/path");
      request.setStoreResponse(true);
      assertThatThrownBy(
              () ->
                  customApacheHttpClient.execute(
                      request,
                      new ExecutionEnvironment.SelfManaged(
                          new DocumentFactory() {
                            @Override
                            public Document resolve(DocumentReference reference) {
                              return null;
                            }

                            @Override
                            public Document create(DocumentCreationRequest request) {
                              throw new RuntimeException("Cannot create document");
                            }
                          })))
          .hasMessage("Failed to create document: Cannot create document");
      var documents = store.getDocuments();
      assertThat(documents).isEmpty();
    }

    @Test
    public void shouldStoreDocument_whenStoreResponseEnabled(WireMockRuntimeInfo wmRuntimeInfo)
        throws IOException {
      stubFor(
          get("/download")
              .willReturn(
                  ok().withHeader(HttpHeaders.CONTENT_TYPE, ContentType.IMAGE_JPEG.getMimeType())
                      .withBodyFile("fileName.jpg")));
      HttpClientRequest request = new HttpClientRequest();
      request.setMethod(HttpMethod.GET);
      request.setStoreResponse(true);
      request.setUrl(wmRuntimeInfo.getHttpBaseUrl() + "/download");
      HttpClientResult result =
          customApacheHttpClient.execute(
              request, new ExecutionEnvironment.SelfManaged(new TestDocumentFactory()));
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
      stubFor(post("/path").withMultipartRequestBody(aMultipart()).willReturn(created()));
      var ref =
          store.createDocument(
              DocumentCreationRequest.from("The content of this file".getBytes())
                  .fileName("file.txt")
                  .contentType("text/plain")
                  .build());
      HttpClientRequest request = new HttpClientRequest();
      request.setMethod(HttpMethod.POST);
      var bodyMap = new HashMap<>();
      bodyMap.put("document", new CamundaDocument(ref.getMetadata(), ref, store));
      bodyMap.put("otherField", "otherValue");
      bodyMap.put("nullField", null);
      request.setHeaders(Map.of("Content-Type", ContentType.MULTIPART_FORM_DATA.getMimeType()));
      request.setUrl(wmRuntimeInfo.getHttpBaseUrl() + "/path");
      request.setBody(bodyMap);
      HttpClientResult result = customApacheHttpClient.execute(request);
      assertThat(result).isNotNull();
      assertThat(result.status()).isEqualTo(201);

      verify(
          postRequestedFor(urlEqualTo("/path"))
              .withHeader(
                  "Content-Type", and(containing("multipart/form-data"), containing("boundary=")))
              .withRequestBodyPart(
                  new MultipartValuePatternBuilder()
                      .withName("otherField")
                      .withBody(equalTo("otherValue"))
                      .build())
              .withRequestBodyPart(
                  new MultipartValuePatternBuilder()
                      .withName("document")
                      .withBody(equalTo("The content of this file"))
                      .withHeader("Content-Type", equalTo("text/plain"))
                      .build()));
    }

    @Test
    public void shouldReturn201_whenUploadDocuments(WireMockRuntimeInfo wmRuntimeInfo) {
      stubFor(post("/path").withMultipartRequestBody(aMultipart()).willReturn(created()));
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
      HttpClientRequest request = new HttpClientRequest();
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
      HttpClientResult result = customApacheHttpClient.execute(request);
      assertThat(result).isNotNull();
      assertThat(result.status()).isEqualTo(201);

      verify(
          postRequestedFor(urlEqualTo("/path"))
              .withHeader(
                  "Content-Type", and(containing("multipart/form-data"), containing("boundary=")))
              .withRequestBodyPart(
                  new MultipartValuePatternBuilder()
                      .withName("otherField")
                      .withBody(equalTo("otherValue"))
                      .build())
              .withRequestBodyPart(
                  new MultipartValuePatternBuilder()
                      .withName("documents")
                      .withBody(equalTo("The content of this file"))
                      .withHeader("Content-Type", equalTo("text/plain"))
                      .build())
              .withRequestBodyPart(
                  new MultipartValuePatternBuilder()
                      .withName("documents")
                      .withBody(equalTo("The content of this file 2"))
                      .withHeader("Content-Type", equalTo("text/plain"))
                      .build()));
    }
  }
}
