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
package io.camunda.connector.api.document;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import org.junit.jupiter.api.Test;

class DocumentCreationRequestTest {

  @Test
  void legacyNineArgConstructorDefaultsPhysicalTenantIdToNull() {
    // proves binary/source compatibility for callers compiled against the pre-physicalTenantId
    // record shape: this constructor must keep existing, unchanged, non-deprecated
    var request =
        new DocumentCreationRequest(
            new ByteArrayInputStream("hello".getBytes()),
            "documentId",
            "storeId",
            "text/plain",
            "file.txt",
            null,
            "processDefinitionId",
            1L,
            null);

    assertThat(request.physicalTenantId()).isNull();
  }

  @Test
  void withPhysicalTenantIdIfAbsent_setsItWhenNotAlreadyPresent() {
    var request =
        DocumentCreationRequest.from(new ByteArrayInputStream("hello".getBytes())).build();

    var result = request.withPhysicalTenantIdIfAbsent("tenant-a");

    assertThat(result.physicalTenantId()).isEqualTo("tenant-a");
  }

  @Test
  void withPhysicalTenantIdIfAbsent_neverOverridesAnExplicitValue() {
    var request =
        DocumentCreationRequest.from(new ByteArrayInputStream("hello".getBytes()))
            .physicalTenantId("explicit-tenant")
            .build();

    var result = request.withPhysicalTenantIdIfAbsent("tenant-a");

    assertThat(result.physicalTenantId()).isEqualTo("explicit-tenant");
    assertThat(result).isSameAs(request);
  }
}
