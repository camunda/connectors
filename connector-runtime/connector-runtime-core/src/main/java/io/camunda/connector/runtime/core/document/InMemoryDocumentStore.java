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

import io.camunda.connector.api.document.DocumentMetadata;
import io.camunda.connector.api.document.DocumentReference;
import io.camunda.connector.api.document.DocumentReference.CamundaDocumentReference;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class InMemoryDocumentStore implements DocumentStore {

  public static final String STORE_ID = "in-memory";

  private final Map<UUID, byte[]> documents = new HashMap<>();

  @Override
  public DocumentReference createDocument(DocumentMetadata metadata, byte[] content) {
    var id = UUID.randomUUID();
    documents.put(id, content);
    return new CamundaDocumentReference(STORE_ID, id.toString(), metadata.getKeys(), null);
  }

  @Override
  public DocumentReference createDocument(DocumentMetadata metadata, InputStream content) {
    var id = UUID.randomUUID();
    try {
      documents.put(id, content.readAllBytes());
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
    return new CamundaDocumentReference(STORE_ID, id.toString(), metadata.getKeys(), null);
  }

  @Override
  public byte[] getDocumentContent(DocumentReference reference) {
    if (!(reference instanceof CamundaDocumentReference ref)) {
      throw new IllegalArgumentException(
          "Unsupported document reference type: " + reference.getClass().getName());
    }
    if (!STORE_ID.equals(ref.storeId())) {
      throw new IllegalArgumentException("Unsupported store id: " + ref.storeId());
    }
    return documents.get(UUID.fromString(ref.documentId()));
  }

  @Override
  public InputStream getDocumentContentStream(DocumentReference reference) {
    if (!(reference instanceof CamundaDocumentReference ref)) {
      throw new IllegalArgumentException(
          "Unsupported document reference type: " + reference.getClass().getName());
    }
    if (!STORE_ID.equals(ref.storeId())) {
      throw new IllegalArgumentException("Unsupported store id: " + ref.storeId());
    }
    return new ByteArrayInputStream(documents.get(UUID.fromString(ref.documentId())));
  }

  @Override
  public void deleteDocument(DocumentReference reference) {
    if (!(reference instanceof CamundaDocumentReference ref)) {
      throw new IllegalArgumentException(
          "Unsupported document reference type: " + reference.getClass().getName());
    }
    if (!STORE_ID.equals(ref.storeId())) {
      throw new IllegalArgumentException("Unsupported store id: " + ref.storeId());
    }
    documents.remove(UUID.fromString(ref.documentId()));
  }
}
