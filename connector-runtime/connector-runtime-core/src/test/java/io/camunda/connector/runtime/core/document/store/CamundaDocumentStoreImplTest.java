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
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import io.camunda.client.CamundaClient;
import io.camunda.connector.api.document.DocumentCreationRequest;
import java.io.ByteArrayInputStream;
import org.junit.jupiter.api.Test;

/**
 * Exercises {@link CamundaDocumentStoreImpl#createDocument}'s physical-tenant sanity check: when
 * both the store and the request carry a physical tenant ID, they must match — catching a stale/
 * cross-wired {@code DocumentFactory} rather than silently creating the document against the wrong
 * cluster.
 */
class CamundaDocumentStoreImplTest {

  private static DocumentCreationRequest requestWithPhysicalTenantId(String physicalTenantId) {
    return DocumentCreationRequest.from(new ByteArrayInputStream("hello".getBytes()))
        .physicalTenantId(physicalTenantId)
        .build();
  }

  @Test
  void throwsWhenRequestsPhysicalTenantIdDoesNotMatchTheStores() {
    var camundaClient = mock(CamundaClient.class);
    var store = new CamundaDocumentStoreImpl(camundaClient, "tenant-a");

    assertThatThrownBy(() -> store.createDocument(requestWithPhysicalTenantId("tenant-b")))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("tenant-b")
        .hasMessageContaining("tenant-a");

    // the mismatch is caught before ever touching the (wrong) client
    verifyNoInteractions(camundaClient);
  }

  @Test
  void succeedsWhenRequestsPhysicalTenantIdMatchesTheStores() {
    var camundaClient = mock(CamundaClient.class, RETURNS_DEEP_STUBS);
    var store = new CamundaDocumentStoreImpl(camundaClient, "tenant-a");

    var reference = store.createDocument(requestWithPhysicalTenantId("tenant-a"));

    assertThat(reference).isNotNull();
  }

  @Test
  void succeedsWhenNeitherSideHasAPhysicalTenantId() {
    var camundaClient = mock(CamundaClient.class, RETURNS_DEEP_STUBS);
    var store = new CamundaDocumentStoreImpl(camundaClient);

    var reference = store.createDocument(requestWithPhysicalTenantId(null));

    assertThat(reference).isNotNull();
  }

  @Test
  void succeedsWhenTheStoreHasNoConfiguredPhysicalTenantId() {
    // e.g. a legacy single-client deployment where physical-tenant-id was never configured
    var camundaClient = mock(CamundaClient.class, RETURNS_DEEP_STUBS);
    var store = new CamundaDocumentStoreImpl(camundaClient);

    var reference = store.createDocument(requestWithPhysicalTenantId("tenant-a"));

    assertThat(reference).isNotNull();
  }
}
