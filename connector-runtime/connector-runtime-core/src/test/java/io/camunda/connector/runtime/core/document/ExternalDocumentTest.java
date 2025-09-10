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

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import io.camunda.connector.api.document.Document;
import io.camunda.connector.api.document.DocumentMetadata;
import io.camunda.connector.api.document.DocumentReference;
import io.camunda.connector.http.client.HttpClientService;
import io.camunda.connector.http.client.model.HttpClientRequest;
import io.camunda.connector.http.client.model.HttpClientResult;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ExternalDocumentTest {

  private HttpClientService httpClientService;
  private HttpClientResult httpClientResult;

  private ExternalDocument document;

  private static final String URL = "http://test.local/file.json";
  private static final String NAME = "myfile.json";

  @BeforeEach
  void setup() {
    httpClientService = mock(HttpClientService.class);
    httpClientResult = mock(HttpClientResult.class);
    document = new ExternalDocument(URL, NAME);
    // replace httpClientService with mock
    try {
      var serviceField = ExternalDocument.class.getDeclaredField("httpClientService");
      serviceField.setAccessible(true);
      serviceField.set(document, httpClientService);
      HttpClientRequest request = new HttpClientRequest();
      request.setShouldReturnRawBody(true);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  @Test
  void metadata_shouldReturnContentTypeAndSize() {
    when(httpClientService.executeConnectorRequest(any())).thenReturn(httpClientResult);
    when(httpClientResult.body()).thenReturn("abc");
    when(httpClientResult.headers())
        .thenReturn(
            Map.of(
                "content-type", "application/json",
                "content-length", "123"));

    DocumentMetadata meta = document.metadata();

    assertThat(meta.getContentType()).isEqualTo("application/json");
    assertThat(meta.getSize()).isEqualTo(123L);
  }

  @Test
  void metadata_shouldFallbackOnInvalidSize() {
    when(httpClientService.executeConnectorRequest(any())).thenReturn(httpClientResult);
    when(httpClientResult.body()).thenReturn("abc");

    DocumentMetadata meta = document.metadata();

    assertThat(meta.getSize()).isEqualTo(-1L);
    assertThat(meta.getContentType()).isEqualTo(null);
  }

  @Test
  void asInputStream_shouldReturnStreamOfStringBody() throws Exception {
    String svg =
        "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"1\" height=\"1\"><rect width=\"1\" height=\"1\"/></svg>\n";
    when(httpClientService.executeConnectorRequest(any())).thenReturn(httpClientResult);
    when(httpClientResult.body()).thenReturn(svg);
    Document document = new ExternalDocument(URL, NAME);

    var serviceField = ExternalDocument.class.getDeclaredField("httpClientService");
    serviceField.setAccessible(true);
    serviceField.set(document, httpClientService);

    try (InputStream is = document.asInputStream()) {
      String content = new String(is.readAllBytes(), StandardCharsets.UTF_8);
      assertThat(content).isEqualTo(svg);
    }
  }

  @Test
  void asInputStream_shouldReturnStreamOfPDF() throws Exception {
    var pdf =
        Objects.requireNonNull(getClass().getClassLoader().getResourceAsStream("testpdf.pdf"))
            .readAllBytes();
    when(httpClientService.executeConnectorRequest(any())).thenReturn(httpClientResult);
    when(httpClientResult.body()).thenReturn(pdf);
    Document document = new ExternalDocument(URL, NAME);

    var serviceField = ExternalDocument.class.getDeclaredField("httpClientService");
    serviceField.setAccessible(true);
    serviceField.set(document, httpClientService);

    try (InputStream is = document.asInputStream()) {
      assertThat(is.readAllBytes()).isEqualTo(pdf);
    }
  }

  @Test
  void reference_shouldReturnExternalDocumentReference() {
    DocumentReference ref = document.reference();

    assertThat(ref).isInstanceOf(DocumentReference.ExternalDocumentReference.class);
    assertThat(((DocumentReference.ExternalDocumentReference) ref).url()).isEqualTo(URL);
    assertThat(((DocumentReference.ExternalDocumentReference) ref).name()).isEqualTo(NAME);
  }

  @Test
  void generateLink_shouldReturnUrl() {
    assertThat(document.generateLink(null)).isEqualTo(URL);
  }
}
