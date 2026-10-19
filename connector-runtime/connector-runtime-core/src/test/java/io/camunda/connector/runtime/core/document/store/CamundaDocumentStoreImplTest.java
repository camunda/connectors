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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.ClientException;
import io.camunda.connector.runtime.core.document.CamundaDocumentReferenceImpl;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import org.apache.hc.core5.http.ConnectionClosedException;
import org.junit.jupiter.api.Test;

/**
 * Covers {@link CamundaDocumentStoreImpl#getDocumentContent}'s single retry when the server closes
 * a pooled connection.
 */
class CamundaDocumentStoreImplTest {

  private static final CamundaDocumentReferenceImpl REFERENCE =
      new CamundaDocumentReferenceImpl("store", "doc-1", "hash", null);

  private static ClientException connectionClosed() {
    return new ClientException(new ConnectionClosedException("Connection closed by peer"));
  }

  private static InputStream documentContentJoin(CamundaClient camundaClient) {
    return camundaClient
        .newDocumentContentGetRequest(anyString())
        .contentHash(anyString())
        .storeId(anyString())
        .send()
        .join();
  }

  @Test
  void retriesDocumentContentOnceWhenConnectionIsClosed() throws Exception {
    var camundaClient = mock(CamundaClient.class, RETURNS_DEEP_STUBS);
    var content = new ByteArrayInputStream("hello".getBytes());
    when(documentContentJoin(camundaClient)).thenThrow(connectionClosed()).thenReturn(content);
    var store = new CamundaDocumentStoreImpl(camundaClient);

    var result = store.getDocumentContent(REFERENCE);

    assertThat(result.readAllBytes()).isEqualTo("hello".getBytes());
    verify(camundaClient, times(2)).newDocumentContentGetRequest("doc-1");
  }

  @Test
  void doesNotRetryDocumentContentOnOtherErrors() {
    var camundaClient = mock(CamundaClient.class, RETURNS_DEEP_STUBS);
    var error = new ClientException("Document not found");
    when(documentContentJoin(camundaClient)).thenThrow(error);
    var store = new CamundaDocumentStoreImpl(camundaClient);

    assertThatThrownBy(() -> store.getDocumentContent(REFERENCE)).isSameAs(error);
    verify(camundaClient, times(1)).newDocumentContentGetRequest("doc-1");
  }

  @Test
  void propagatesWhenConnectionIsClosedAgainOnRetry() {
    var camundaClient = mock(CamundaClient.class, RETURNS_DEEP_STUBS);
    var secondError = connectionClosed();
    when(documentContentJoin(camundaClient)).thenThrow(connectionClosed()).thenThrow(secondError);
    var store = new CamundaDocumentStoreImpl(camundaClient);

    assertThatThrownBy(() -> store.getDocumentContent(REFERENCE)).isSameAs(secondError);
    verify(camundaClient, times(2)).newDocumentContentGetRequest("doc-1");
  }
}
