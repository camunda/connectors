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
package io.camunda.connector.runtime.inbound;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.client.spring.bean.CamundaClientRegistry;
import io.camunda.connector.api.document.DocumentCreationRequest;
import io.camunda.connector.runtime.app.TestConnectorRuntimeApplication;
import java.io.ByteArrayInputStream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * End-to-end proof that, when multiple {@code camunda.clients.*} are configured, each physical
 * tenant's {@link io.camunda.connector.api.document.DocumentFactory} genuinely routes document I/O
 * through its OWN configured {@code CamundaClient} — never silently through another physical
 * tenant's — using the real Spring-bound {@link CamundaClientRegistry} (built from actual {@code
 * camunda.clients.*} properties, not a hand-built test double, unlike {@link
 * PhysicalTenantIdResolutionTest}).
 *
 * <p>No live broker is needed to prove isolation: each client is configured with a distinct,
 * unreachable {@code grpc-address}, so attempting a document operation through the wrong tenant's
 * factory would either fail against the wrong host or (if genuinely cross-wired) succeed/fail
 * identically for both tenants. Asserting the failure for each tenant surfaces ITS OWN configured
 * host proves the two factories are backed by two distinct {@code CamundaClient} instances, not one
 * shared/mixed-up client.
 */
@SpringBootTest(
    classes = TestConnectorRuntimeApplication.class,
    properties = {
      "camunda.clients.engine-a.mode=self-managed",
      "camunda.clients.engine-a.grpc-address=http://engine-a.internal:26500",
      "camunda.clients.engine-a.rest-address=http://engine-a.internal:8080",
      "camunda.clients.engine-a.physical-tenant-id=tenanta",
      "camunda.clients.engine-a.primary=true",
      "camunda.clients.engine-b.mode=self-managed",
      "camunda.clients.engine-b.grpc-address=http://engine-b.internal:26500",
      "camunda.clients.engine-b.rest-address=http://engine-b.internal:8080",
      "camunda.clients.engine-b.physical-tenant-id=tenantb",
      "camunda.connector.polling.enabled=false",
      "camunda.connector.webhook.enabled=false"
    })
class DocumentPhysicalTenantIsolationWiringTest {

  @Autowired private CamundaClientRegistry camundaClientRegistry;

  private static DocumentCreationRequest documentCreationRequest() {
    return DocumentCreationRequest.from(new ByteArrayInputStream("hello".getBytes())).build();
  }

  @Test
  void eachPhysicalTenantsDocumentFactoryTargetsItsOwnConfiguredClient() {
    var documentFactoriesByPhysicalTenantId =
        PhysicalTenantIds.buildDocumentFactoriesByPhysicalTenantId(
            camundaClientRegistry, null, null);

    assertThat(documentFactoriesByPhysicalTenantId).containsOnlyKeys("tenanta", "tenantb");
    assertThat(documentFactoriesByPhysicalTenantId.get("tenanta"))
        .isNotSameAs(documentFactoriesByPhysicalTenantId.get("tenantb"));

    assertThatThrownBy(
            () ->
                documentFactoriesByPhysicalTenantId
                    .get("tenanta")
                    .create(documentCreationRequest()))
        .rootCause()
        .hasMessageContaining("engine-a.internal");

    assertThatThrownBy(
            () ->
                documentFactoriesByPhysicalTenantId
                    .get("tenantb")
                    .create(documentCreationRequest()))
        .rootCause()
        .hasMessageContaining("engine-b.internal");
  }
}
