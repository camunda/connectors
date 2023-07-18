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
package io.camunda.connector.impl.feel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class FeelDeserializerTest {

  private final ObjectMapper mapper = new ObjectMapper();

  @Test
  void feelDeserializer_deserializeMap() throws JsonProcessingException {
    // given
    String json = """
        { "props": "= { result: \\"foobar\\" }" }
        """;

    // when
    TargetTypeMap targetType = mapper.readValue(json, TargetTypeMap.class);

    // then
    assertThat(targetType.props.size()).isEqualTo(1);
    assertThat(targetType.props.get("result")).isEqualTo("foobar");
  }

  @Test
  void feelDeserializer_deserializeNestedMap() throws JsonProcessingException {
    // given
    String json = """
        { "stubObject": "= { props: { nested: \\"foobar\\" } }" }
        """;

    // when
    TargetTypeObject targetType = mapper.readValue(json, TargetTypeObject.class);

    // then
    assertThat(targetType.stubObject.props.size()).isEqualTo(1);
    assertThat(targetType.stubObject.props.get("nested")).isEqualTo("foobar");
  }

  @Test
  void feelDeserializer_deserializePrimitive() throws JsonProcessingException {
    // given
    String json = """
        { "props": "= \\"foobar\\"" }
        """;

    // when
    TargetTypeString targetType = mapper.readValue(json, TargetTypeString.class);

    // then
    assertThat(targetType.props).isEqualTo("foobar");
  }

  @Test
  void feelDeserializer_wrongType_throwsException() {
    // given
    String json = """
        { "props": "= { result: \\"foobar\\" }" }
        """;

    // when & then
    assertThrows(JsonMappingException.class, () -> mapper.readValue(json, TargetTypeString.class));
  }

  @Test
  void feelDeserializer_notFeel_preserved() throws JsonProcessingException {
    // given
    String json = """
        { "props": "foobar" }
        """;

    // when && then
    assertDoesNotThrow(() -> mapper.readValue(json, TargetTypeString.class));
    assertEquals("foobar", mapper.readValue(json, TargetTypeString.class).props);
  }

  private record TargetTypeMap(@FEEL Map<String, String> props) {}

  private record TargetTypeObject(@FEEL StubObject stubObject) {}

  private record StubObject(Map<String, Object> props) {}

  private record TargetTypeString(@FEEL String props) {}
}
