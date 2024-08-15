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
import io.camunda.connector.api.document.DocumentFactory;
import io.camunda.connector.api.document.DocumentReference;
import io.camunda.connector.api.document.DocumentReference.CamundaDocumentReference;
import io.camunda.connector.api.document.DocumentReference.ExternalDocumentReference;
import io.camunda.connector.api.document.store.DocumentCreationRequest;
import io.camunda.connector.api.document.store.DocumentStore;

public class DocumentFactoryImpl implements DocumentFactory {

  private final DocumentStore documentStore;

  public DocumentFactoryImpl(DocumentStore documentStore) {
    this.documentStore = documentStore;
  }

  @Override
  public Document parse(DocumentReference reference) {
    return switch (reference) {
      case CamundaDocumentReference camundaDocumentReference -> new CamundaDocument(
          camundaDocumentReference.metadata(), camundaDocumentReference, documentStore);
      case ExternalDocumentReference ignored -> throw new IllegalArgumentException(
          "External document references are not yet supported: " + reference.getClass());
      default -> throw new IllegalArgumentException("Unsupported document reference: " + reference);
    };
  }

  @Override
  public Document create(DocumentCreationRequest request) {
    var reference = documentStore.createDocument(request);
    return parse(reference);
  }
}
