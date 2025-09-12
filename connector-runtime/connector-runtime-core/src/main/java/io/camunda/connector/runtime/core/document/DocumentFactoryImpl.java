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
import io.camunda.connector.api.document.DocumentReference.CamundaDocumentReference;
import io.camunda.connector.api.document.DocumentReference.ExternalDocumentReference;
import io.camunda.connector.http.client.HttpClientService;
import io.camunda.connector.http.client.model.HttpClientRequest;
import io.camunda.connector.http.client.model.HttpClientResult;
import io.camunda.connector.http.client.model.HttpMethod;
import io.camunda.connector.runtime.core.document.store.CamundaDocumentStore;
import java.util.function.Function;

public class DocumentFactoryImpl implements DocumentFactory {

  private final CamundaDocumentStore documentStore;
  private final HttpClientService httpClientService;
  private final Function<String, HttpClientResult> downloadDocument;

  public DocumentFactoryImpl(CamundaDocumentStore documentStore) {
    this.documentStore = documentStore;
    this.httpClientService = new HttpClientService();
    this.downloadDocument =
        url -> {
          HttpClientRequest req = new HttpClientRequest();
          req.setMethod(HttpMethod.GET);
          req.setUrl(url);
          req.setStoreResponse(false);
          return this.httpClientService.executeConnectorRequest(req);
        };
  }

  @Override
  public Document resolve(DocumentReference reference) {
    if (reference == null) {
      return null;
    }
    if (reference instanceof CamundaDocumentReference camundaDocumentReference) {
      return new CamundaDocument(
          camundaDocumentReference.getMetadata(), camundaDocumentReference, documentStore);
    }
    if (reference instanceof ExternalDocumentReference externalDocumentReference) {
      return new ExternalDocument(
          externalDocumentReference.url(), externalDocumentReference.name(), downloadDocument);
    }
    throw new IllegalArgumentException("Unknown document reference type: " + reference.getClass());
  }

  @Override
  public Document create(DocumentCreationRequest request) {
    var reference = documentStore.createDocument(request);
    return resolve(reference);
  }
}
