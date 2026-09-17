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
package io.camunda.connector.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.api.document.Document;
import io.camunda.connector.api.document.DocumentFactory;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Regression coverage for security-testing-findings#275 on the actual production construction path:
 * {@code ConnectorsAutoConfiguration.buildOutboundConnectorObjectMapper} is the mapper that binds
 * job variables into a connector's properties, and both confirmed exploit routes went through it.
 * Reached here via reflection (private static, no Spring context needed) rather than only through
 * the generic {@code DisabledIntrinsicFunctionExecutor} unit tests.
 */
class ConnectorsAutoConfigurationOutboundMapperTest {

  private record ObjectTypedProperty(Object value) {}

  private static ObjectMapper buildOutboundConnectorObjectMapper(DocumentFactory documentFactory)
      throws Exception {
    Method method =
        ConnectorsAutoConfiguration.class.getDeclaredMethod(
            "buildOutboundConnectorObjectMapper", DocumentFactory.class);
    method.setAccessible(true);
    return (ObjectMapper) method.invoke(null, documentFactory);
  }

  private static Map<String, Object> exploitNode() {
    // The issue's PoC payload: createLink against an arbitrary document reference, arriving as
    // ordinary Object-typed connector-property data (e.g. an ioMapping expression's result).
    return Map.of(
        "camunda.function.type",
        "createLink",
        "params",
        List.of(Map.of("camunda.document.type", "camunda"), "PT1H"));
  }

  @Test
  void refusesIntrinsicFunctionDispatchThroughAnObjectTypedProperty() throws Exception {
    ObjectMapper mapper = buildOutboundConnectorObjectMapper(mock(DocumentFactory.class));
    var payload = Map.of("value", exploitNode());

    assertThatThrownBy(() -> mapper.convertValue(payload, ObjectTypedProperty.class))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Intrinsic function dispatch is disabled");
  }

  @Test
  void aDocumentReferenceStillMaterializesThroughAnObjectTypedProperty() throws Exception {
    var documentFactory = mock(DocumentFactory.class);
    var expectedDocument = mock(Document.class);
    when(documentFactory.resolve(any())).thenReturn(expectedDocument);
    ObjectMapper mapper = buildOutboundConnectorObjectMapper(documentFactory);

    var payload =
        Map.of(
            "value",
            Map.of(
                "camunda.document.type", "camunda",
                "storeId", "store-1",
                "documentId", "doc-1",
                "contentHash", "hash-1"));

    var result = mapper.convertValue(payload, ObjectTypedProperty.class);

    assertThat(result.value()).isEqualTo(expectedDocument);
  }

  @Test
  void ordinaryDataStillBindsThroughAnObjectTypedProperty() throws Exception {
    ObjectMapper mapper = buildOutboundConnectorObjectMapper(mock(DocumentFactory.class));

    var result = mapper.convertValue(Map.of("value", "hello"), ObjectTypedProperty.class);

    assertThat(result.value()).isEqualTo("hello");
  }
}
