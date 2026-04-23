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
package io.camunda.connector.feel.jackson;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.camunda.connector.api.annotation.FEEL;
import java.util.Map;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

/**
 * Test that verifies BLANK_OBJECT_MAPPER in AbstractFeelDeserializer can handle objects without
 * serialization support (like Document objects before the fix). This test ensures that the Document
 * serializer module is loaded optionally via reflection, preventing serialization failures when
 * process variables contain Document objects.
 */
public class AbstractFeelDeserializerDocumentTest {

  private final ObjectMapper mapper =
      new ObjectMapper().registerModule(new JacksonModuleFeelFunction());

  @Test
  void feelDeserializer_contextWithUnserializableObject_shouldNotFail() {
    // given: a JSON with a FEEL expression that references context variables
    String json =
        """
        { "props": "= { result: myVar }" }
        """;

    // Simulate an object without serialization support (like CamundaDocument before the fix)
    // This would have caused: "No serializer found for class..." error
    UnserializableTestObject unserializableObj = new UnserializableTestObject("test-data");

    // Create a context supplier that includes the unserializable object
    Supplier<Map<String, Object>> supplier =
        () -> Map.of("myVar", "value", "document", unserializableObj);

    var objectReader = FeelContextAwareObjectReader.of(mapper).withContextSupplier(supplier);

    // when: deserializing with context containing unserializable object
    // then: should not throw exception (the fix registers document serializer via reflection)
    var targetType = assertDoesNotThrow(() -> objectReader.readValue(json, TargetTypeMap.class));
    assertThat(targetType.props).containsEntry("result", "value");
  }

  @Test
  void blankObjectMapper_shouldBeConfiguredToHandleEmptyBeans() {
    // given: the BLANK_OBJECT_MAPPER from AbstractFeelDeserializer
    ObjectMapper blankMapper = AbstractFeelDeserializer.BLANK_OBJECT_MAPPER;

    // when & then: it should be configured to handle objects without properties
    // (either via SerializationFeature.FAIL_ON_EMPTY_BEANS = false OR via registered serializers)
    // This test verifies that the BLANK_OBJECT_MAPPER won't fail on objects like CamundaDocument
    assertThat(blankMapper.isEnabled(SerializationFeature.FAIL_ON_EMPTY_BEANS))
        .as("BLANK_OBJECT_MAPPER should handle empty beans gracefully")
        .isFalse();
  }

  /**
   * Test object without Jackson annotations or properties, simulating Document objects that need
   * special serializers
   */
  private static class UnserializableTestObject {
    private final String data;

    UnserializableTestObject(String data) {
      this.data = data;
    }

    // No getter - Jackson won't find properties to serialize
    // This simulates CamundaDocument which also has no exposed properties
  }

  public record TargetTypeMap(@FEEL Map<String, Object> props) {}
}
