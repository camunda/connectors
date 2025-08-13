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

import io.camunda.client.CamundaClient;
import io.camunda.connector.api.document.DocumentCreationRequest;
import io.camunda.connector.api.document.DocumentLinkParameters;
import io.camunda.connector.api.document.DocumentReference.CamundaDocumentReference;
import io.camunda.document.CamundaDocumentReferenceImpl;
import java.io.InputStream;

public class CamundaDocumentStoreImpl implements CamundaDocumentStore {

  private final CamundaClient camundaClient;

  public CamundaDocumentStoreImpl(CamundaClient camundaClient) {
    this.camundaClient = camundaClient;
  }

  @Override
  public CamundaDocumentReference createDocument(DocumentCreationRequest request) {
    final var command = camundaClient.newCreateDocumentCommand().content(request.content());

    if (request.contentType() != null) {
      command.contentType(request.contentType());
    }
    if (request.fileName() != null) {
      command.fileName(request.fileName());
    }
    if (request.timeToLive() != null) {
      command.timeToLive(request.timeToLive());
    }
    if (request.customProperties() != null) {
      command.customMetadata(request.customProperties());
    }
    final var response = command.send().join();
    return new CamundaDocumentReferenceImpl(response);
  }

  @Override
  public InputStream getDocumentContent(CamundaDocumentReference reference) {
    return camundaClient
        .newDocumentContentGetRequest(reference.getDocumentId())
        .contentHash(reference.getContentHash())
        .storeId(reference.getStoreId())
        .send()
        .join();
  }

  @Override
  public void deleteDocument(CamundaDocumentReference reference) {
    camundaClient
        .newDeleteDocumentCommand(reference.getDocumentId())
        .storeId(reference.getStoreId())
        .send()
        .join();
  }

  @Override
  public String generateLink(
      CamundaDocumentReference reference, DocumentLinkParameters parameters) {
    final var command =
        camundaClient
            .newCreateDocumentLinkCommand(reference.getDocumentId())
            .contentHash(reference.getContentHash())
            .storeId(reference.getStoreId());

    if (parameters.timeToLive() != null) {
      command.timeToLive(parameters.timeToLive());
    }
    return command.send().join().getUrl();
  }
}
