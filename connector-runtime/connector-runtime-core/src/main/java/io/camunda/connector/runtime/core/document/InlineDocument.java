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
import io.camunda.connector.api.document.DocumentReference.InlineDocumentReference;
import io.camunda.connector.api.error.ConnectorInputException;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

/**
 * In-memory {@link Document} implementation backed by content carried directly in process
 * variables.
 */
public class InlineDocument implements Document {

  private final String content;
  private final String name;
  private final String contentType;
  private byte[] bytes;
  private transient DocumentMetadata metadata;

  public InlineDocument(String content, String name, String contentType) {
    if (content == null) {
      throw new ConnectorInputException("Inline document content must not be null");
    }
    this.content = content;
    this.name = (name != null && !name.isBlank()) ? name : UUID.randomUUID().toString();
    this.contentType = contentType;
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
            return MimeTypeResolver.resolveContentType(contentType, name);
          }

          @Override
          public OffsetDateTime getExpiresAt() {
            return null;
          }

          @Override
          public Long getSize() {
            return (long) asByteArray().length;
          }

          @Override
          public String getFileName() {
            return name;
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
    return new ByteArrayInputStream(asByteArray());
  }

  @Override
  public byte[] asByteArray() {
    if (bytes == null) {
      bytes = content.getBytes(StandardCharsets.UTF_8);
    }
    return bytes;
  }

  @Override
  public DocumentReference reference() {
    return new InlineDocumentReference() {
      @Override
      public String content() {
        return content;
      }

      @Override
      public String name() {
        return name;
      }

      @Override
      public String contentType() {
        return contentType;
      }
    };
  }

  @Override
  public String generateLink(DocumentLinkParameters parameters) {
    return null;
  }
}
