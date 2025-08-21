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

import io.camunda.connector.api.document.Document;
import io.camunda.connector.api.document.DocumentLinkParameters;
import io.camunda.connector.api.document.DocumentMetadata;
import io.camunda.connector.api.document.DocumentReference;
import io.camunda.connector.http.client.HttpClientService;
import io.camunda.connector.http.client.model.HttpClientRequest;
import io.camunda.connector.http.client.model.HttpClientResult;
import io.camunda.connector.http.client.model.HttpMethod;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

public class ExternalDocument implements Document {

  private final String url;
  private final String name;
  private transient DocumentMetadata metadata;
  // private HttpClientService httpClientService;
  private final HttpClientRequest request;
  private final HttpClientService httpClientService;

  public ExternalDocument(String url, String name, HttpURLConnection connection) {
    this.url = url;
    this.name = name;
    HttpClientService httpClientService = new HttpClientService();
    HttpClientRequest request = new HttpClientRequest();
    request.setMethod(HttpMethod.GET);
    request.setUrl(url);
    request.setStoreResponse(true);
    this.request = request;
    this.httpClientService = new HttpClientService();
  }

  public ExternalDocument(String url, String name) {
    this(url, name, null);
  }

  public DocumentMetadata metadata() {
    if (metadata != null) {
      return metadata;
    }
    var metadata =
        new DocumentMetadata() {
          @Override
          public String getContentType() {
            return "connection().getContentType()";
          }

          @Override
          public OffsetDateTime getExpiresAt() {
            return null;
          }

          @Override
          public Long getSize() {
            return 0L;
          }

          @Override
          public String getFileName() {
            return name == null ? UUID.randomUUID().toString() : name;
          }

          @Override
          public String getProcessDefinitionId() {
            return "";
          }

          @Override
          public Long getProcessInstanceKey() {
            return 0L;
          }

          @Override
          public Map<String, Object> getCustomProperties() {
            return Map.of();
          }
        };
    this.metadata = metadata;
    return metadata;
  }

  @Override
  public String asBase64() {
    return Base64.getEncoder().encodeToString(asByteArray());
  }

  @Override
  public InputStream asInputStream() {
    try {
      HttpClientResult result = httpClientService.executeConnectorRequest(request);
      return result.document().asInputStream();
    } catch (Exception e) {
      throw new RuntimeException("Failed to download external document from " + url, e);
    }
  }

  @Override
  public byte[] asByteArray() {
    try (InputStream inputStream = asInputStream()) {
      return inputStream.readAllBytes();
    } catch (IOException e) {
      return null;
    }
  }

  @Override
  public DocumentReference reference() {
    return null;
  }

  @Override
  public String generateLink(DocumentLinkParameters parameters) {
    return url;
  }
}
