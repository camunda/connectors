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

import static io.camunda.connector.runtime.core.document.DocumentDeserializationTest.createDocumentMock;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import io.camunda.connector.api.document.Document;
import io.camunda.connector.api.document.DocumentFactory;
import io.camunda.connector.document.jackson.IntrinsicFunctionExecutor;
import io.camunda.connector.document.jackson.JacksonModuleDocumentDeserializer;
import io.camunda.connector.runtime.core.document.store.CamundaDocumentStore;
import java.io.IOException;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Exercises the {@code Map<String, DocumentFactory>}-based {@link
 * JacksonModuleDocumentDeserializer} constructor, which resolves the {@link DocumentFactory} to use
 * per deserialization call from the {@link DocumentFactory#PHYSICAL_TENANT_ID_ATTRIBUTE} reader
 * attribute rather than a single constructor-supplied instance (see {@link
 * DocumentDeserializationTest} for the single-factory variant, which this mirrors).
 */
@ExtendWith(MockitoExtension.class)
class DocumentDeserializationPhysicalTenantIdTest {

  private final CamundaDocumentStore storeA = mock(CamundaDocumentStore.class);
  private final CamundaDocumentStore storeB = mock(CamundaDocumentStore.class);
  private final DocumentFactory factoryA = new DocumentFactoryImpl(storeA);
  private final DocumentFactory factoryB = new DocumentFactoryImpl(storeB);
  private final IntrinsicFunctionExecutor operationExecutor = mock(IntrinsicFunctionExecutor.class);

  private ObjectMapper mapperFor(Map<String, DocumentFactory> factoriesByPhysicalTenantId) {
    return new ObjectMapper()
        .registerModule(
            new JacksonModuleDocumentDeserializer(
                factoriesByPhysicalTenantId,
                operationExecutor,
                JacksonModuleDocumentDeserializer.DocumentModuleSettings.create()))
        .registerModule(new Jdk8Module());
  }

  @Test
  void resolvesTheFactoryMatchingTheReaderAttribute() throws IOException {
    var objectMapper = mapperFor(Map.of("tenant-a", factoryA, "tenant-b", factoryB));
    var ref = createDocumentMock("Hello from tenant B", null, storeB);
    var payload = Map.of("document", ref);

    var result =
        objectMapper
            .reader()
            .withAttribute(DocumentFactory.PHYSICAL_TENANT_ID_ATTRIBUTE, "tenant-b")
            .readValue(objectMapper.valueToTree(payload).toString(), TargetTypeDocument.class);

    assertThat(result.document().reference()).isEqualTo(ref);
  }

  @Test
  void fallsBackToTheSoleFactoryWhenNoAttributeIsSetAndOnlyOneTenantIsConfigured() {
    var objectMapper = mapperFor(Map.of("tenant-a", factoryA));
    var ref = createDocumentMock("Hello from the only tenant", null, storeA);
    var payload = Map.of("document", ref);

    var result = objectMapper.convertValue(payload, TargetTypeDocument.class); // no attribute set

    assertThat(result.document().reference()).isEqualTo(ref);
  }

  @Test
  void throwsWhenNoAttributeIsSetAndMultipleTenantsAreConfigured() {
    var objectMapper = mapperFor(Map.of("tenant-a", factoryA, "tenant-b", factoryB));
    var ref = createDocumentMock("Hello from nowhere in particular", null, storeA);
    var payload = Map.of("document", ref);

    assertThatThrownBy(() -> objectMapper.convertValue(payload, TargetTypeDocument.class))
        .rootCause()
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("no physical tenant ID attribute was set");
  }

  @Test
  void throwsWhenTheAttributeDoesNotMatchAnyConfiguredTenant() {
    var objectMapper = mapperFor(Map.of("tenant-a", factoryA));
    var ref = createDocumentMock("Hello from an unknown tenant", null, storeA);
    var payload = Map.of("document", ref);

    assertThatThrownBy(
            () ->
                objectMapper
                    .reader()
                    .withAttribute(DocumentFactory.PHYSICAL_TENANT_ID_ATTRIBUTE, "tenant-unknown")
                    .readValue(
                        objectMapper.valueToTree(payload).toString(), TargetTypeDocument.class))
        .hasCauseInstanceOf(IllegalStateException.class)
        .cause()
        .hasMessageContaining("No DocumentFactory configured for physical tenant 'tenant-unknown'");
  }

  public record TargetTypeDocument(Document document) {}
}
