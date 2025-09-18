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

import io.camunda.connector.api.document.Document;
import io.camunda.connector.api.document.DocumentLinkParameters;
import io.camunda.connector.api.document.DocumentMetadata;
import io.camunda.connector.api.document.DocumentReference;
import io.camunda.connector.http.client.client.apache.CustomApacheHttpClient;
import io.camunda.connector.http.client.document.HttpHeaderFilenameResolver;
import io.camunda.connector.http.client.model.HttpClientResult;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.Map;
import java.util.function.Function;
import org.apache.hc.core5.http.HttpHeaders;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ExternalDocument implements Document {

  private final String url;
  private final String name;
  private transient DocumentMetadata metadata;
  Function<String, HttpClientResult> downloadDocument;
  private HttpClientResult result = null;

  private static final Logger LOGGER = LoggerFactory.getLogger(ExternalDocument.class);

  public ExternalDocument(
      String url, String name, Function<String, HttpClientResult> downloadDocument) {
    this.url = url;
    this.name = name;
    this.downloadDocument = downloadDocument;
  }

  private HttpClientResult getResult() {
    if (result == null) {
      this.result = downloadDocument.apply(url);
      LOGGER.debug(
          "Downloading external document completed with status code: {}", this.result.status());
    }
    return result;
  }

  @Override
  public DocumentMetadata metadata() {
    if (metadata != null) {
      return metadata;
    }
    var metadata =
        new DocumentMetadata() {
          @Override
          public String getContentType() {
            Object contentType =
                CustomApacheHttpClient.getHeaderIgnoreCase(
                    getResult().headers(), HttpHeaders.CONTENT_TYPE);
            return contentType != null ? contentType.toString() : null;
          }

          @Override
          public OffsetDateTime getExpiresAt() {
            return null;
          }

          @Override
          public Long getSize() {
            try {
              return CustomApacheHttpClient.getHeaderIgnoreCase(
                          getResult().headers(), HttpHeaders.CONTENT_LENGTH)
                      instanceof String sizeStr
                  ? Long.parseLong(sizeStr)
                  : -1L;
            } catch (NumberFormatException e) {
              LOGGER.debug("Could not parse content-length header, falling back to -1L");
              return -1L;
            }
          }

          @Override
          public String getFileName() {
            return name != null
                ? name
                : HttpHeaderFilenameResolver.getFilename(getResult().headers());
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
    Object resultBody = getResult().body();
    byte[] bytes = resultBody instanceof byte[] b ? b : ((String) resultBody).getBytes();
    return new ByteArrayInputStream(bytes);
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
    return new DocumentReference.ExternalDocumentReference() {
      @Override
      public String url() {
        return url;
      }

      @Override
      public String name() {
        return name;
      }
    };
  }

  @Override
  public String generateLink(DocumentLinkParameters parameters) {
    return url;
  }
}
