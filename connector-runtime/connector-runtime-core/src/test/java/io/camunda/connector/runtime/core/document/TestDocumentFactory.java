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
import io.camunda.connector.api.document.DocumentCreationRequest;
import io.camunda.connector.api.document.DocumentFactory;
import io.camunda.connector.api.document.DocumentReference;
import io.camunda.document.store.InMemoryDocumentStore;

public class TestDocumentFactory implements DocumentFactory {

  public static final InMemoryDocumentStore store = InMemoryDocumentStore.INSTANCE;
  private final DocumentFactory factory = new DocumentFactoryImpl(store);

  @Override
  public Document resolve(DocumentReference reference) {
    return factory.resolve(reference);
  }

  @Override
  public Document create(DocumentCreationRequest request) {
    var reference = store.createDocument(request);
    var metadata =
        new DocumentMetadataImpl(
            reference.getMetadata().getContentType(),
            reference.getMetadata().getExpiresAt(),
            reference.getMetadata().getSize(),
            reference.getMetadata().getFileName(),
            reference.getMetadata().getProcessDefinitionId(),
            reference.getMetadata().getProcessInstanceKey(),
            reference.getMetadata().getCustomProperties());
    return new CamundaDocument(metadata, reference, store);
  }
}
