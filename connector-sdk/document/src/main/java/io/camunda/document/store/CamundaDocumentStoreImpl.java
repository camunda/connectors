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

import io.camunda.document.DocumentLinkParameters;
import io.camunda.document.reference.CamundaDocumentReferenceImpl;
import io.camunda.document.reference.DocumentReference.CamundaDocumentReference;
import io.camunda.zeebe.client.ZeebeClient;
import java.io.InputStream;

public class CamundaDocumentStoreImpl implements CamundaDocumentStore {

  private final ZeebeClient zeebeClient;

  public CamundaDocumentStoreImpl(ZeebeClient zeebeClient) {
    this.zeebeClient = zeebeClient;
  }

  @Override
  public CamundaDocumentReference createDocument(DocumentCreationRequest request) {
    final var command = zeebeClient.newCreateDocumentCommand().content(request.content());

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
    return zeebeClient.newDocumentContentGetRequest(reference).send().join();
  }

  @Override
  public void deleteDocument(CamundaDocumentReference reference) {
    zeebeClient.newDeleteDocumentCommand(reference).send().join();
  }

  @Override
  public String generateLink(
      CamundaDocumentReference reference, DocumentLinkParameters parameters) {
    final var command = zeebeClient.newCreateDocumentLinkCommand(reference);

    if (parameters.timeToLive() != null) {
      command.timeToLive(parameters.timeToLive());
    }
    return command.send().join().getUrl();
  }
}
