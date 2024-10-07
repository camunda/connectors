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
package io.camunda.document.store;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.document.DocumentLink;
import io.camunda.document.reference.CamundaDocumentReferenceImpl;
import io.camunda.document.reference.DocumentReference.CamundaDocumentReference;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;
import org.apache.hc.client5.http.classic.methods.HttpDelete;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.entity.mime.MultipartEntityBuilder;
import org.apache.hc.client5.http.entity.mime.StringBody;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.io.HttpClientResponseHandler;
import org.apache.hc.core5.net.URIBuilder;

public class CamundaDocumentStoreImpl implements CamundaDocumentStore {

  private final String documentApiBaseUrl;
  private final ObjectMapper objectMapper;

  public CamundaDocumentStoreImpl(ObjectMapper objectMapper) {
    this.documentApiBaseUrl = "http://zeebe-service:8080/v2/documents";
    // this.documentApiBaseUrl = "http://localhost:8088/v2/documents";
    this.objectMapper = objectMapper;
  }

  @Override
  public CamundaDocumentReference createDocument(DocumentCreationRequest request) {

    try (CloseableHttpClient httpClient = HttpClientBuilder.create().build()) {
      URIBuilder uriBuilder = new URIBuilder(documentApiBaseUrl);
      if (request.storeId() != null) {
        uriBuilder.addParameter("storeId", request.storeId());
      }
      if (request.documentId() != null) {
        uriBuilder.addParameter("documentId", request.documentId());
      }
      HttpPost post = new HttpPost(uriBuilder.build());

      MultipartEntityBuilder entityBuilder = MultipartEntityBuilder.create();
      final var contentType =
          Optional.ofNullable(request.metadata().getContentType())
              .orElse("application/octet-stream");
      entityBuilder.addBinaryBody(
          "file", request.content(), ContentType.parse("application/octet-stream"), "file");

      final var metadataString = objectMapper.writeValueAsString(request.metadata());
      entityBuilder.addPart(
          "metadata", new StringBody(metadataString, ContentType.APPLICATION_JSON));

      post.setEntity(entityBuilder.build());

      final HttpClientResponseHandler<CamundaDocumentReference> handler =
          (ClassicHttpResponse response) ->
              handleResponse(
                  response,
                  successfulResponse ->
                      objectMapper.readValue(
                          successfulResponse.getEntity().getContent(),
                          CamundaDocumentReferenceImpl.class));

      return httpClient.execute(post, handler);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public InputStream getDocumentContent(CamundaDocumentReference reference) {

    try (CloseableHttpClient httpClient = HttpClientBuilder.create().build()) {
      URIBuilder uriBuilder = new URIBuilder(documentApiBaseUrl);
      uriBuilder.appendPath(reference.documentId());
      if (reference.storeId() != null) {
        uriBuilder.addParameter("storeId", reference.storeId());
      }
      HttpGet get = new HttpGet(uriBuilder.build());
      final byte[] result =
          httpClient.execute(get, response -> response.getEntity().getContent().readAllBytes());
      return new ByteArrayInputStream(result);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public void deleteDocument(CamundaDocumentReference reference) {
    try (CloseableHttpClient httpClient = HttpClientBuilder.create().build()) {
      URIBuilder uriBuilder = new URIBuilder(documentApiBaseUrl);
      uriBuilder.appendPath(reference.documentId());
      if (reference.storeId() != null) {
        uriBuilder.addParameter("storeId", reference.storeId());
      }
      HttpDelete delete = new HttpDelete(uriBuilder.build());
      httpClient.execute(delete, response -> handleResponse(response, success -> null));
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public DocumentLink createLink(CamundaDocumentReference reference) {
    try (CloseableHttpClient httpClient = HttpClientBuilder.create().build()) {
      URIBuilder uriBuilder = new URIBuilder(documentApiBaseUrl);
      uriBuilder.appendPath(reference.documentId());
      uriBuilder.appendPath("links");

      if (reference.storeId() != null) {
        uriBuilder.addParameter("storeId", reference.storeId());
      }
      HttpPost post = new HttpPost(uriBuilder.build());

      final HttpClientResponseHandler<DocumentLink> handler =
          (ClassicHttpResponse response) ->
              handleResponse(
                  response,
                  successfulResponse ->
                      objectMapper.readValue(
                          successfulResponse.getEntity().getContent(), DocumentLink.class));

      return httpClient.execute(post, handler);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private <T> T handleResponse(
      ClassicHttpResponse response, HttpClientResponseHandler<T> successHandler)
      throws IOException, HttpException {

    return switch (response.getCode()) {
      case 200, 201, 204 -> successHandler.handleResponse(response);
      case 401, 403 -> {
        throw new RuntimeException("Authentication failed: " + response.getCode());
      }
      default ->
          throw new RuntimeException(
              "Unable to handle response from the Document API: " + response.getCode());
    };
  }
}
