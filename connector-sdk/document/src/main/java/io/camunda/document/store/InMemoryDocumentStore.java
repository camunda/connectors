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

import io.camunda.connector.api.document.DocumentCreationRequest;
import io.camunda.connector.api.document.DocumentLinkParameters;
import io.camunda.connector.api.document.DocumentMetadata;
import io.camunda.connector.api.document.DocumentReference.CamundaDocumentReference;
import io.camunda.document.CamundaDocumentReferenceImpl;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

/** Use this document store to store documents in memory. This is useful for testing purposes. */
public class InMemoryDocumentStore implements CamundaDocumentStore {

  private static final Logger LOGGER = Logger.getLogger(InMemoryDocumentStore.class.getName());
  public static final String STORE_ID = "in-memory";

  public static InMemoryDocumentStore INSTANCE = new InMemoryDocumentStore();

  private final Map<String, byte[]> documents = new HashMap<>();

  private InMemoryDocumentStore() {}

  @Override
  public CamundaDocumentReference createDocument(DocumentCreationRequest request) {
    logWarning();
    final String id =
        request.documentId() != null ? request.documentId() : UUID.randomUUID().toString();

    final DocumentMetadata metadata =
        new DocumentMetadata() {
          @Override
          public String getContentType() {
            return request.contentType();
          }

          @Override
          public OffsetDateTime getExpiresAt() {
            if (request.timeToLive() != null) {
              return OffsetDateTime.now().plus(request.timeToLive());
            }

            return null;
          }

          @Override
          public Long getSize() {
            return (long) documents.get(id).length;
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

    final byte[] content;
    try (InputStream contentStream = request.content()) {
      content = contentStream.readAllBytes();
    } catch (Exception e) {
      throw new RuntimeException("Failed to read document content", e);
    }
    documents.put(id, content);
    return new CamundaDocumentReferenceImpl(STORE_ID, id, String.valueOf(content.length), metadata);
  }

  @Override
  public InputStream getDocumentContent(CamundaDocumentReference reference) {
    logWarning();
    if (reference.getContentHash() == null || reference.getContentHash().isEmpty()) {
      throw new RuntimeException("Content hash is missing: " + reference.getDocumentId());
    }
    var hash = reference.getContentHash();
    var content = documents.get(reference.getDocumentId());
    if (content == null) {
      throw new RuntimeException("Document not found: " + reference.getDocumentId());
    }
    if (!hash.equals(String.valueOf(content.length))) {
      throw new RuntimeException("Content hash mismatch: " + reference.getDocumentId());
    }
    return new ByteArrayInputStream(content);
  }

  @Override
  public void deleteDocument(CamundaDocumentReference reference) {
    logWarning();
    documents.remove(reference.getDocumentId());
  }

  @Override
  public String generateLink(
      CamundaDocumentReference reference, DocumentLinkParameters parameters) {
    logWarning();
    throw new UnsupportedOperationException("Not implemented");
  }

  public void clear() {
    documents.clear();
  }

  public Map<String, byte[]> getDocuments() {
    return documents;
  }

  public void logWarning() {
    LOGGER.warning(
        "In-memory document store is used. This store is not suitable for production use.");
  }
}
