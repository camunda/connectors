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

import io.camunda.document.DocumentMetadata;
import io.camunda.document.reference.CamundaDocumentReferenceImpl;
import io.camunda.document.reference.DocumentReference.CamundaDocumentReference;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Use this document store to store documents in memory. This is useful for testing purposes. */
public class InMemoryDocumentStore implements CamundaDocumentStore {

  public static final String STORE_ID = "in-memory";

  public static InMemoryDocumentStore INSTANCE = new InMemoryDocumentStore();

  private final Map<String, byte[]> documents = new HashMap<>();

  private InMemoryDocumentStore() {}

  @Override
  public CamundaDocumentReference createDocument(DocumentCreationRequest request) {
    final String id =
        request.documentId() != null ? request.documentId() : UUID.randomUUID().toString();
    final DocumentMetadata metadata = request.metadata();
    final byte[] content;
    try (InputStream contentStream = request.content()) {
      content = contentStream.readAllBytes();
    } catch (Exception e) {
      throw new RuntimeException("Failed to read document content", e);
    }
    documents.put(id, content);
    return new CamundaDocumentReferenceImpl(STORE_ID, id, metadata);
  }

  @Override
  public InputStream getDocumentContent(CamundaDocumentReference reference) {
    var content = documents.get(reference.documentId());
    if (content == null) {
      throw new RuntimeException("Document not found: " + reference.documentId());
    }
    return new ByteArrayInputStream(content);
  }

  @Override
  public void deleteDocument(CamundaDocumentReference reference) {
    documents.remove(reference.documentId());
  }
}
