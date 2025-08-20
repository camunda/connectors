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
package io.camunda.connector.api.document;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

public class ExternalDocument implements Document {

  private final String url;
  private final String name;
  private transient DocumentMetadata metadata;
  private transient HttpURLConnection connection;

  public ExternalDocument(
      String url, String name, HttpURLConnection connection) {
    this.url = url;
    this.name = name;
    this.connection = connection;
  }

  public ExternalDocument(String url, String name) {
    this(url, name, null);
  }

  private HttpURLConnection connection() {
    try {
      if (connection != null) {
        return connection;
      }
      URL urlObj = new URL(url);
      connection = (HttpURLConnection) urlObj.openConnection();
      connection.setRequestMethod("GET");
      return connection;
    } catch (IOException e) {
      throw new RuntimeException("Failed to connect to external document URL: " + url, e);
    }
  }

  public DocumentMetadata metadata() {
    if (metadata != null) {
      return metadata;
    }
    var metadata =
        new DocumentMetadata() {
          @Override
          public String getContentType() {
            return connection().getContentType();
          }

          @Override
          public OffsetDateTime getExpiresAt() {
            return null;
          }

          @Override
          public Long getSize() {
            return connection().getContentLengthLong();
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
      return connection().getInputStream();
    } catch (IOException e) {
      throw new RuntimeException(
          "Failed to download external document: " + connection().getURL().toString(), e);
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
    return connection().getURL().toString();
  }
}
