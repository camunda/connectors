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

import io.camunda.client.api.response.DocumentMetadata;
import io.camunda.document.reference.DocumentReference;
import io.camunda.document.reference.DocumentReference.CamundaDocumentReference;
import io.camunda.document.store.CamundaDocumentStore;
import io.camunda.document.store.DocumentLinkCreationRequest;
import java.io.InputStream;
import java.time.Duration;
import java.util.Base64;
import java.util.Optional;

public class CamundaDocument implements Document {

  private final DocumentMetadata metadata;
  private final CamundaDocumentReference reference;
  private final CamundaDocumentStore documentStore;

  public CamundaDocument(
      DocumentMetadata metadata,
      CamundaDocumentReference reference,
      CamundaDocumentStore documentStore) {
    this.metadata = metadata;
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
    return documentStore.getDocumentContent(reference);
  }

  @Override
  public byte[] asByteArray() {
    try {
      return documentStore.getDocumentContent(reference).readAllBytes();
    } catch (Exception e) {
      throw new RuntimeException("Failed to read document content: " + e.getMessage(), e);
    }
  }

  @Override
  public DocumentReference reference() {
    return reference;
  }

  @Override
  public String generateLink() {
    return documentStore.generateLink(new DocumentLinkCreationRequest(reference, Optional.empty()));
  }

  @Override
  public String generateLink(Duration timeToLive) {
    return documentStore.generateLink(
        new DocumentLinkCreationRequest(reference, Optional.of(timeToLive)));
  }
}
