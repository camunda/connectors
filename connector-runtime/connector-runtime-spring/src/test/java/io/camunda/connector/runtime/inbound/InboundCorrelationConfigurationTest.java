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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.camunda.client.CamundaClient;
import io.camunda.client.spring.bean.CamundaClientRegistry;
import io.camunda.connector.api.document.Document;
import io.camunda.connector.api.document.DocumentFactory;
import io.camunda.connector.api.document.DocumentReference;
import io.camunda.connector.runtime.TestObjectMapperSupplier;
import io.camunda.connector.runtime.core.inbound.InboundConnectorElement;
import io.camunda.connector.runtime.core.inbound.ProcessElementWithRuntimeData;
import io.camunda.connector.runtime.core.inbound.correlation.InboundCorrelationHandler;
import io.camunda.connector.runtime.core.inbound.correlation.StartEventCorrelationPoint;
import io.camunda.connector.runtime.metrics.ConnectorsInboundMetrics;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

/**
 * Exercises {@link InboundCorrelationConfiguration#inboundCorrelationHandler}, the
 * backward-compatible scalar bean built on top of the per-physical-tenant correlation-handler map.
 */
class InboundCorrelationConfigurationTest {

  private final InboundCorrelationConfiguration configuration =
      new InboundCorrelationConfiguration();

  private static CamundaClient clientWithPhysicalTenantId(String physicalTenantId) {
    var client = mock(CamundaClient.class, RETURNS_DEEP_STUBS);
    when(client.getConfiguration().getPhysicalTenantId()).thenReturn(physicalTenantId);
    return client;
  }

  @Test
  void inboundCorrelationHandler_returnsTheSingleEntryForASinglePhysicalTenant() {
    var registry = mock(CamundaClientRegistry.class);
    var client = clientWithPhysicalTenantId("tenant");
    when(registry.clientNames()).thenReturn(Set.of("engine-a"));
    when(registry.get("engine-a")).thenReturn(client);

    var result =
        configuration.inboundCorrelationHandler(
            registry,
            null,
            mock(ObjectMapper.class),
            mock(DocumentFactory.class),
            mock(ConnectorsInboundMetrics.class));

    assertThat(result).isInstanceOf(InboundCorrelationHandler.class);
  }

  @Test
  void inboundCorrelationHandler_throwsClearErrorForMultiplePhysicalTenants() {
    var registry = mock(CamundaClientRegistry.class);
    var clientA = clientWithPhysicalTenantId("tenant-a");
    var clientB = clientWithPhysicalTenantId("tenant-b");
    when(registry.clientNames()).thenReturn(Set.of("engine-a", "engine-b"));
    when(registry.get("engine-a")).thenReturn(clientA);
    when(registry.get("engine-b")).thenReturn(clientB);

    assertThatThrownBy(
            () ->
                configuration.inboundCorrelationHandler(
                    registry,
                    null,
                    mock(ObjectMapper.class),
                    mock(DocumentFactory.class),
                    mock(ConnectorsInboundMetrics.class)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("InboundCorrelationHandler");
  }

  @Test
  void buildCorrelationHandlersByPhysicalTenantId_selectsDocumentFactoryPerPhysicalTenant() {
    var registry = mock(CamundaClientRegistry.class);
    var clientA = mock(CamundaClient.class, RETURNS_DEEP_STUBS);
    var clientB = mock(CamundaClient.class, RETURNS_DEEP_STUBS);
    when(clientA.getConfiguration().getPhysicalTenantId()).thenReturn("tenant-a");
    when(clientB.getConfiguration().getPhysicalTenantId()).thenReturn("tenant-b");
    when(registry.clientNames()).thenReturn(Set.of("engine-a", "engine-b"));
    when(registry.get("engine-a")).thenReturn(clientA);
    when(registry.get("engine-b")).thenReturn(clientB);
    var documentFactoryA = mock(DocumentFactory.class);
    var documentFactoryB = mock(DocumentFactory.class);
    var createdDocument = mock(Document.class);
    var createdDocumentReference = mock(DocumentReference.ExternalDocumentReference.class);
    when(createdDocumentReference.url()).thenReturn("https://example.invalid/doc");
    when(createdDocumentReference.name()).thenReturn("hello");
    when(createdDocument.reference()).thenReturn(createdDocumentReference);
    when(documentFactoryA.create(any())).thenReturn(createdDocument);

    var handlers =
        InboundCorrelationConfiguration.buildCorrelationHandlersByPhysicalTenantId(
            registry,
            null,
            TestObjectMapperSupplier.INSTANCE,
            Duration.ofHours(1),
            Map.of("tenant-a", documentFactoryA, "tenant-b", documentFactoryB),
            mock(ConnectorsInboundMetrics.class));

    var element = mock(InboundConnectorElement.class);
    when(element.correlationPoint()).thenReturn(new StartEventCorrelationPoint("process1", 0, 0));
    when(element.resultExpression()).thenReturn("={myDoc: createDocument(\"aGVsbG8=\")}");
    when(element.element())
        .thenReturn(new ProcessElementWithRuntimeData("process1", 0, 0, "element", "default"));

    handlers.get("tenant-a").correlate(List.of(element), Map.of());

    // createDocument() on tenant-a's handler must use tenant-a's own DocumentFactory, never
    // tenant-b's — otherwise a non-primary tenant's documents would be uploaded to the wrong
    // cluster's document store
    verify(documentFactoryA).create(any());
    verifyNoInteractions(documentFactoryB);
  }

  @Test
  void buildCorrelationHandlersByPhysicalTenantId_legacyFiveArgOverloadStillWorks() {
    // Preserves source/binary compatibility for callers compiled against the pre-createDocument()
    // five-argument overload (no DocumentFactory map parameter).
    var registry = mock(CamundaClientRegistry.class);
    var client = clientWithPhysicalTenantId("tenant");
    when(registry.clientNames()).thenReturn(Set.of("engine-a"));
    when(registry.get("engine-a")).thenReturn(client);

    var handlers =
        InboundCorrelationConfiguration.buildCorrelationHandlersByPhysicalTenantId(
            registry,
            null,
            TestObjectMapperSupplier.INSTANCE,
            Duration.ofHours(1),
            mock(ConnectorsInboundMetrics.class));

    assertThat(handlers.get("tenant")).isInstanceOf(InboundCorrelationHandler.class);
  }
}
