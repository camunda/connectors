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
package io.camunda.document.factory;

import io.camunda.document.CamundaDocument;
import io.camunda.document.Document;
import io.camunda.document.reference.DocumentReference;
import io.camunda.document.reference.DocumentReference.CamundaDocumentReference;
import io.camunda.document.reference.DocumentReference.ExternalDocumentReference;
import io.camunda.document.store.CamundaDocumentStore;
import io.camunda.document.store.DocumentCreationRequest;

public class DocumentFactoryImpl implements DocumentFactory {

  private final CamundaDocumentStore documentStore;

  public DocumentFactoryImpl(CamundaDocumentStore documentStore) {
    this.documentStore = documentStore;
  }

  @Override
  public Document resolve(DocumentReference reference) {
    if (reference == null) {
      return null;
    }
    if (reference instanceof CamundaDocumentReference camundaDocumentReference) {
      return new CamundaDocument(
          camundaDocumentReference.metadata(), camundaDocumentReference, documentStore);
    }
    if (reference instanceof ExternalDocumentReference ignored) {
      throw new IllegalArgumentException(
          "External document references are not yet supported: " + reference.getClass());
    }
    throw new IllegalArgumentException("Unknown document reference type: " + reference.getClass());
  }

  @Override
  public Document create(DocumentCreationRequest request) {
    var reference = documentStore.createDocument(request);
    return resolve(reference);
  }
}
