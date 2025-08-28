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
package io.camunda.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import io.camunda.connector.api.document.DocumentCreationRequest;
import io.camunda.connector.api.document.DocumentFactory;
import io.camunda.connector.api.error.ConnectorException;
import io.camunda.connector.http.client.HttpClientObjectMapperSupplier;
import io.camunda.connector.http.client.cloudfunction.CloudFunctionCredentials;
import io.camunda.connector.http.client.cloudfunction.CloudFunctionService;
import io.camunda.connector.http.client.exception.ConnectorExceptionMapper;
import io.camunda.connector.http.client.model.HttpClientRequest;
import io.camunda.connector.http.client.model.HttpClientResult;
import io.camunda.connector.http.client.model.HttpMethod;
import io.camunda.connector.http.client.model.auth.BearerAuthentication;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import org.apache.hc.core5.http.ContentType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class CloudFunctionServiceTest {
  private static final CloudFunctionCredentials cloudFunctionCredentials =
      mock(CloudFunctionCredentials.class);
  private static final CloudFunctionService cloudFunctionService =
      spy(new CloudFunctionService(cloudFunctionCredentials));
  private static final DocumentFactory documentFactory = new TestDocumentFactory();

  @BeforeAll
  public static void setUp() {
    when(cloudFunctionService.getProxyFunctionUrl()).thenReturn("proxyUrl");
    when(cloudFunctionCredentials.getOAuthToken(anyString())).thenReturn("token");
  }

  @Test
  public void
      shouldConvertToCloudFunctionRequestWithDocumentContent_whenBodyContainsDocumentsAndJsonContentType()
          throws IOException {
    // given
    var document =
        documentFactory.create(
            DocumentCreationRequest.from("the content".getBytes(StandardCharsets.UTF_8))
                .fileName("the filename")
                .contentType("text/plain")
                .build());
    HttpClientRequest request = new HttpClientRequest();
    request.setUrl("theUrl");
    request.setMethod(HttpMethod.POST);
    request.setHeaders(
        Map.of("header", "value", "Content-Type", ContentType.APPLICATION_JSON.getMimeType()));
    request.setBody(Map.of("bodyKey", "bodyValue", "myDocument", document));
    request.setConnectionTimeoutInSeconds(50);
    request.setReadTimeoutInSeconds(60);
    request.setAuthentication(new BearerAuthentication("token"));

    // when
    HttpClientRequest cloudFunctionRequest = cloudFunctionService.toCloudFunctionRequest(request);

    // then
    assertThat(cloudFunctionRequest.getUrl()).isEqualTo("proxyUrl");
    assertThat(cloudFunctionRequest.getMethod()).isEqualTo(HttpMethod.POST);
    assertThat(cloudFunctionRequest.getHeaders().orElse(Map.of())).hasSize(1);
    assertThat(cloudFunctionRequest.getHeaders().orElse(Map.of()))
        .containsEntry("Content-Type", "application/json");
    Map<String, Object> body =
        HttpClientObjectMapperSupplier.getCopy()
            .readValue((String) cloudFunctionRequest.getBody(), Map.class);
    assertThat(body).containsEntry("url", "theUrl");
    assertThat(body).containsEntry("method", "POST");
    assertThat(body)
        .containsEntry("headers", Map.of("header", "value", "Content-Type", "application/json"));
    assertThat(body)
        .containsEntry(
            "body",
            Map.of(
                "bodyKey",
                "bodyValue",
                "myDocument",
                Base64.getEncoder()
                    .encodeToString("the content".getBytes(StandardCharsets.UTF_8))));
    assertThat(body).containsEntry("connectionTimeoutInSeconds", 50);
    assertThat(body).containsEntry("readTimeoutInSeconds", 60);
    assertThat(body).containsEntry("authentication", Map.of("token", "token", "type", "bearer"));
  }

  @Test
  public void
      shouldConvertToCloudFunctionRequestWithDocumentContent_whenBodyContainsDocumentsAndMultipartContentType()
          throws IOException {
    // given
    var document =
        documentFactory.create(
            DocumentCreationRequest.from("the content".getBytes(StandardCharsets.UTF_8))
                .fileName("the filename")
                .contentType("text/plain")
                .build());
    HttpClientRequest request = new HttpClientRequest();
    request.setUrl("theUrl");
    request.setMethod(HttpMethod.POST);
    request.setHeaders(
        Map.of("header", "value", "Content-Type", ContentType.MULTIPART_FORM_DATA.getMimeType()));
    request.setBody(Map.of("bodyKey", "bodyValue", "myDocument", document));
    request.setConnectionTimeoutInSeconds(50);
    request.setReadTimeoutInSeconds(60);
    request.setAuthentication(new BearerAuthentication("token"));

    // when
    HttpClientRequest cloudFunctionRequest = cloudFunctionService.toCloudFunctionRequest(request);

    // then
    assertThat(cloudFunctionRequest.getUrl()).isEqualTo("proxyUrl");
    assertThat(cloudFunctionRequest.getMethod()).isEqualTo(HttpMethod.POST);
    assertThat(cloudFunctionRequest.getHeaders().orElse(Map.of())).hasSize(1);
    assertThat(cloudFunctionRequest.getHeaders().orElse(Map.of()))
        .containsEntry("Content-Type", "application/json");
    Map<String, Object> body =
        HttpClientObjectMapperSupplier.getCopy()
            .readValue((String) cloudFunctionRequest.getBody(), Map.class);
    assertThat(body).containsEntry("url", "theUrl");
    assertThat(body).containsEntry("method", "POST");
    assertThat(body)
        .containsEntry("headers", Map.of("header", "value", "Content-Type", "multipart/form-data"));
    assertThat(body)
        .containsEntry(
            "body",
            Map.of(
                "bodyKey",
                "bodyValue",
                "myDocument",
                Map.of(
                    "name",
                    "myDocument",
                    "fileName",
                    "the filename",
                    "contentType",
                    "text/plain",
                    "content",
                    Base64.getEncoder()
                        .encodeToString("the content".getBytes(StandardCharsets.UTF_8)))));
    assertThat(body).containsEntry("connectionTimeoutInSeconds", 50);
    assertThat(body).containsEntry("readTimeoutInSeconds", 60);
    assertThat(body).containsEntry("authentication", Map.of("token", "token", "type", "bearer"));
  }

  @Test
  public void
      shouldConvertToCloudFunctionRequestWithDocumentContent_whenBodyContainsDocumentsAndFormUrlEncodedContentType()
          throws IOException {
    // given
    var document =
        documentFactory.create(
            DocumentCreationRequest.from("the content".getBytes(StandardCharsets.UTF_8)).build());
    HttpClientRequest request = new HttpClientRequest();
    request.setUrl("theUrl");
    request.setMethod(HttpMethod.POST);
    request.setHeaders(
        Map.of(
            "header",
            "value",
            "Content-Type",
            ContentType.APPLICATION_FORM_URLENCODED.getMimeType()));
    request.setBody(Map.of("bodyKey", "bodyValue", "myDocument", document));
    request.setConnectionTimeoutInSeconds(50);
    request.setReadTimeoutInSeconds(60);
    request.setAuthentication(new BearerAuthentication("token"));

    // when
    HttpClientRequest cloudFunctionRequest = cloudFunctionService.toCloudFunctionRequest(request);

    // then
    assertThat(cloudFunctionRequest.getUrl()).isEqualTo("proxyUrl");
    assertThat(cloudFunctionRequest.getMethod()).isEqualTo(HttpMethod.POST);
    assertThat(cloudFunctionRequest.getHeaders().orElse(Map.of())).hasSize(1);
    assertThat(cloudFunctionRequest.getHeaders().orElse(Map.of()))
        .containsEntry("Content-Type", "application/json");
    Map<String, Object> body =
        HttpClientObjectMapperSupplier.getCopy()
            .readValue((String) cloudFunctionRequest.getBody(), Map.class);
    assertThat(body).containsEntry("url", "theUrl");
    assertThat(body).containsEntry("method", "POST");
    assertThat(body)
        .containsEntry(
            "headers",
            Map.of("header", "value", "Content-Type", "application/x-www-form-urlencoded"));
    assertThat(body)
        .containsEntry(
            "body",
            Map.of(
                "bodyKey",
                "bodyValue",
                "myDocument",
                Base64.getEncoder()
                    .encodeToString("the content".getBytes(StandardCharsets.UTF_8))));
    assertThat(body).containsEntry("connectionTimeoutInSeconds", 50);
    assertThat(body).containsEntry("readTimeoutInSeconds", 60);
    assertThat(body).containsEntry("authentication", Map.of("token", "token", "type", "bearer"));
  }

  @Test
  public void shouldConvertToCloudFunctionRequest() throws IOException {
    // given
    HttpClientRequest request = new HttpClientRequest();
    request.setUrl("theUrl");
    request.setMethod(HttpMethod.POST);
    request.setHeaders(
        Map.of(
            "header",
            "value",
            "Content-Type",
            ContentType.APPLICATION_FORM_URLENCODED.getMimeType()));
    request.setBody(Map.of("bodyKey", "bodyValue"));
    request.setConnectionTimeoutInSeconds(50);
    request.setReadTimeoutInSeconds(60);
    request.setAuthentication(new BearerAuthentication("token"));

    // when
    HttpClientRequest cloudFunctionRequest = cloudFunctionService.toCloudFunctionRequest(request);

    // then
    assertThat(cloudFunctionRequest.getUrl()).isEqualTo("proxyUrl");
    assertThat(cloudFunctionRequest.getMethod()).isEqualTo(HttpMethod.POST);
    assertThat(cloudFunctionRequest.getHeaders().orElse(Map.of())).hasSize(1);
    assertThat(cloudFunctionRequest.getHeaders().orElse(Map.of()))
        .containsEntry("Content-Type", "application/json");
    Map<String, Object> body =
        HttpClientObjectMapperSupplier.getCopy()
            .readValue((String) cloudFunctionRequest.getBody(), Map.class);
    assertThat(body).containsEntry("url", "theUrl");
    assertThat(body).containsEntry("method", "POST");
    assertThat(body)
        .containsEntry(
            "headers",
            Map.of("header", "value", "Content-Type", "application/x-www-form-urlencoded"));
    assertThat(body).containsEntry("body", Map.of("bodyKey", "bodyValue"));
    assertThat(body).containsEntry("connectionTimeoutInSeconds", 50);
    assertThat(body).containsEntry("readTimeoutInSeconds", 60);
    assertThat(body).containsEntry("authentication", Map.of("token", "token", "type", "bearer"));
  }

  @Test
  public void shouldUpdateError_whenExceptionMessageIsJson() {
    // given
    HttpClientResult result =
        new HttpClientResult(404, Map.of("Content-Type", "text/plain"), "text_body", "the Reason");

    // when
    var exception =
        cloudFunctionService.parseCloudFunctionError(ConnectorExceptionMapper.from(result));

    // then
    assertThat(exception.getMessage()).isEqualTo("the Reason");
    assertThat(exception.getErrorCode()).isEqualTo("404");
    assertThat(exception.getErrorVariables())
        .isEqualTo(
            Map.of(
                "response",
                Map.of("body", "text_body", "headers", Map.of("Content-Type", "text/plain"))));
  }

  @Test
  public void shouldNotUpdateError_whenExceptionMessageIsNotJson() {
    // given
    ConnectorException exception = new ConnectorException("500", "Unknown error");

    // when
    exception = cloudFunctionService.parseCloudFunctionError(exception);

    // then
    assertThat(exception.getMessage()).isEqualTo("Unknown error");
    assertThat(exception.getErrorCode()).isEqualTo("500");
  }
}
