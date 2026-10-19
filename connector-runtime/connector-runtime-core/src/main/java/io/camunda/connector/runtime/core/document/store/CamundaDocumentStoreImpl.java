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
package io.camunda.connector.runtime.core.document.store;

import io.camunda.client.CamundaClient;
import io.camunda.connector.api.document.DocumentCreationRequest;
import io.camunda.connector.api.document.DocumentLinkParameters;
import io.camunda.connector.api.document.DocumentReference.CamundaDocumentReference;
import io.camunda.connector.runtime.core.document.CamundaDocumentReferenceImpl;
import java.io.InputStream;
import org.apache.hc.core5.http.ConnectionClosedException;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CamundaDocumentStoreImpl implements CamundaDocumentStore {

  private static final Logger LOG = LoggerFactory.getLogger(CamundaDocumentStoreImpl.class);

  private final CamundaClient camundaClient;
  private final @Nullable String physicalTenantId;

  public CamundaDocumentStoreImpl(CamundaClient camundaClient) {
    this(camundaClient, null);
  }

  public CamundaDocumentStoreImpl(CamundaClient camundaClient, @Nullable String physicalTenantId) {
    this.camundaClient = camundaClient;
    this.physicalTenantId = physicalTenantId;
  }

  @Override
  public CamundaDocumentReference createDocument(DocumentCreationRequest request) {
    if (physicalTenantId != null
        && request.physicalTenantId() != null
        && !physicalTenantId.equals(request.physicalTenantId())) {
      throw new IllegalStateException(
          "Attempted to create a document for physical tenant '"
              + request.physicalTenantId()
              + "' using a document store configured for physical tenant '"
              + physicalTenantId
              + "' — this likely indicates a DocumentFactory reused across physical tenants.");
    }
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
    try {
      return fetchDocumentContent(reference);
    } catch (RuntimeException e) {
      // The Camunda client's HTTP pool can hand out a keep-alive connection the server has just
      // closed, and Apache's default retry strategy never retries ConnectionClosedException. The
      // request is an idempotent GET and the dead connection is discarded, so retry once.
      if (!isConnectionClosed(e)) {
        throw e;
      }
      LOG.debug(
          "Connection closed while fetching content of document {}, retrying once",
          reference.getDocumentId(),
          e);
      return fetchDocumentContent(reference);
    }
  }

  private InputStream fetchDocumentContent(CamundaDocumentReference reference) {
    return camundaClient
        .newDocumentContentGetRequest(reference.getDocumentId())
        .contentHash(reference.getContentHash())
        .storeId(reference.getStoreId())
        .send()
        .join();
  }

  private static boolean isConnectionClosed(Throwable e) {
    for (Throwable cause = e; cause != null; cause = cause.getCause()) {
      if (cause instanceof ConnectionClosedException) {
        return true;
      }
    }
    return false;
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
