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
package io.camunda.connector.http.client;

import io.camunda.client.api.response.DocumentMetadata;
import io.camunda.connector.api.document.Document;
import io.camunda.connector.api.document.DocumentCreationRequest;
import io.camunda.connector.api.document.DocumentFactory;
import io.camunda.connector.api.document.DocumentReference;
import io.camunda.document.CamundaDocument;
import io.camunda.document.DocumentFactoryImpl;
import io.camunda.document.DocumentMetadataImpl;
import io.camunda.document.store.InMemoryDocumentStore;
import java.time.OffsetDateTime;
import java.util.Map;

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
        new DocumentMetadata() {
          @Override
          public String getContentType() {
            return reference.getMetadata().getContentType();
          }

          @Override
          public OffsetDateTime getExpiresAt() {
            return reference.getMetadata().getExpiresAt();
          }

          @Override
          public Long getSize() {
            return reference.getMetadata().getSize();
          }

          @Override
          public String getFileName() {
            return reference.getMetadata().getFileName();
          }

          @Override
          public String getProcessDefinitionId() {
            return reference.getMetadata().getProcessDefinitionId();
          }

          @Override
          public Long getProcessInstanceKey() {
            return reference.getMetadata().getProcessInstanceKey();
          }

          @Override
          public Map<String, Object> getCustomProperties() {
            return reference.getMetadata().getCustomProperties();
          }
        };
    return new CamundaDocument(new DocumentMetadataImpl(metadata), reference, store);
  }
}
