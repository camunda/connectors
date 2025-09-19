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
package io.camunda.connector.runtime.test.document;

import io.camunda.connector.api.document.Document;
import io.camunda.connector.api.document.DocumentCreationRequest;
import io.camunda.connector.api.document.DocumentFactory;
import io.camunda.connector.api.document.DocumentMetadata;
import io.camunda.connector.api.document.DocumentReference;
import io.camunda.connector.api.document.DocumentReference.CamundaDocumentReference;
import io.camunda.connector.api.document.DocumentReference.ExternalDocumentReference;
import java.io.IOException;
import java.io.InputStream;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class TestDocumentFactory implements DocumentFactory {

  private final Map<String, Document> documents = new HashMap<>();

  @Override
  public Document resolve(DocumentReference reference) {
    if (reference == null) {
      return null;
    }

    if (reference instanceof CamundaDocumentReference camundaRef) {
      return documents.get(camundaRef.getDocumentId());
    }

    if (reference instanceof ExternalDocumentReference extRef) {
      return documents.get(extRef.url());
    }

    throw new IllegalArgumentException("Unknown document reference type: " + reference.getClass());
  }

  @Override
  public Document create(DocumentCreationRequest request) {
    String documentId = UUID.randomUUID().toString();

    final byte[] content;
    try (InputStream in = request.content()) {
      content = in.readAllBytes();
    } catch (IOException e) {
      throw new RuntimeException("Failed to read InputStream", e);
    }

    DocumentMetadata metadata =
        new TestDocumentMetadata(
            request.contentType(),
            OffsetDateTime.now().plus(request.timeToLive()),
            (long) content.length,
            request.fileName(),
            request.processDefinitionId(),
            request.processInstanceKey(),
            request.customProperties());

    DocumentReference.CamundaDocumentReference reference =
        new CamundaDocumentReference() {
          @Override
          public String getDocumentId() {
            return documentId;
          }

          @Override
          public String getStoreId() {
            return "test-hashmap-store";
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

    // Create TestDocument
    Document document = new TestDocument(content, metadata, reference, documentId);
    documents.put(documentId, document);
    return document;
  }
}
