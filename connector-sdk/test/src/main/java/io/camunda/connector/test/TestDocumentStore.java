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
package io.camunda.connector.test;

import io.camunda.connector.api.document.*;
import io.camunda.connector.api.document.DocumentReference.CamundaDocumentReference;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class TestDocumentStore implements CamundaDocumentStore {

  private final Map<String, byte[]> store = new HashMap<>();

  @Override
  public CamundaDocumentReference createDocument(DocumentCreationRequest request) {
    String documentId = UUID.randomUUID().toString();

    final byte[] content;
    try (InputStream in = request.content()) {
      content = in.readAllBytes();
    } catch (Exception e) {
      throw new RuntimeException("Failed to read InputStream", e);
    }

    // Minimal Metadata
    DocumentMetadata metadata =
        new DocumentMetadata() {
          @Override
          public String getContentType() {
            return request.contentType();
          }

          @Override
          public OffsetDateTime getExpiresAt() {
            Duration ttl = request.timeToLive();
            return ttl != null ? OffsetDateTime.now().plus(ttl) : null;
          }

          @Override
          public Long getSize() {
            return (long) content.length;
          }

          @Override
          public String getFileName() {
            return request.fileName();
          }

          @Override
          public String getProcessDefinitionId() {
            return request.processDefinitionId();
          }

          @Override
          public Long getProcessInstanceKey() {
            return request.processInstanceKey();
          }

          @Override
          public Map<String, Object> getCustomProperties() {
            return request.customProperties();
          }
        };

    store.put(documentId, content);

    // Reference
    return new CamundaDocumentReference() {
      @Override
      public String getDocumentId() {
        return documentId;
      }

      @Override
      public String getStoreId() {
        return "test-in-memory-store";
      }

      @Override
      public String getContentHash() {
        return Integer.toHexString(documentId.hashCode());
      }

      @Override
      public DocumentMetadata getMetadata() {
        return metadata;
      }
    };
  }

  @Override
  public InputStream getDocumentContent(CamundaDocumentReference reference) {
    byte[] content = store.get(reference.getDocumentId());
    if (content == null) return null;
    return new ByteArrayInputStream(content);
  }

  @Override
  public void deleteDocument(CamundaDocumentReference reference) {
    store.remove(reference.getDocumentId());
  }

  @Override
  public String generateLink(
      CamundaDocumentReference reference, DocumentLinkParameters parameters) {
    return "https://test-store/" + reference.getDocumentId();
  }
}
