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
import io.camunda.connector.api.document.DocumentMetadata;
import io.camunda.connector.api.document.DocumentReference;
import java.io.InputStream;
import java.util.Base64;
import java.util.Map;

public class DocumentImpl implements Document {

  private final DocumentMetadata metadata;
  private final DocumentReference reference;
  private final DocumentStore documentStore;

  public DocumentImpl(
      Map<String, Object> metadata, DocumentReference reference, DocumentStore documentStore) {
    this.metadata = new DocumentMetadata(metadata);
    this.reference = reference;
    this.documentStore = documentStore;
  }

  @Override
  public DocumentMetadata metadata() {
    return metadata;
  }

  @Override
  public String asBase64() {
    return Base64.getEncoder().encodeToString(asByteArray());
  }

  @Override
  public InputStream asInputStream() {
    return documentStore.getDocumentContentStream(reference);
  }

  @Override
  public byte[] asByteArray() {
    return documentStore.getDocumentContent(reference);
  }

  @Override
  public DocumentReference reference() {
    return reference;
  }
}
