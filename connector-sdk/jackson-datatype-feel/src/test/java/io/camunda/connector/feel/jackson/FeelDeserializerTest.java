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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.camunda.connector.feel.annotation.FEEL;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

public class FeelDeserializerTest {

  private final ObjectMapper mapper =
      new ObjectMapper()
          .registerModule(new JacksonModuleFeelFunction())
          .registerModule(new JavaTimeModule());

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
    assertThrows(JsonMappingException.class, () -> mapper.readValue(json, TargetTypeArray.class));
  }

  @Test
  void feelDeserializer_plainString_preserved() throws JsonProcessingException {
    // given
    String json = """
        { "props": "foobar" }
        """;

    // when && then
    assertDoesNotThrow(() -> mapper.readValue(json, TargetTypeString.class));
    assertEquals("foobar", mapper.readValue(json, TargetTypeString.class).props);
  }

  @Test
  void feelDeserializer_notFeel_jsonArray_parsed() {
    // given
    String json = """
        { "props": "[1, 2, 3]" }
        """;

    // when && then
    var targetType = assertDoesNotThrow(() -> mapper.readValue(json, TargetTypeArray.class));
    assertThat(targetType.props).containsExactly(1L, 2L, 3L);
  }

  @Test
  void feelDeserializer_notFeel_jsonList_parsed() {
    // given
    String json = """
        { "props": "[1, 2, 3]" }
        """;

    // when && then
    var targetType = assertDoesNotThrow(() -> mapper.readValue(json, TargetTypeList.class));
    assertThat(targetType.props).containsExactly("1", "2", "3");
  }

  @Test
  void feelDeserializer_notFeel_jsonObject_parsed() {
    // given
    String json = """
        { "props": "{\\"foo\\": \\"bar\\"}" }
        """;

    // when && then
    var targetType = assertDoesNotThrow(() -> mapper.readValue(json, TargetTypeMap.class));
    assertThat(targetType.props).containsEntry("foo", "bar");
  }

  @Test
  void feelDeserializer_notFeel_stringListLong_parsed() {
    // given
    String json = """
        { "props": "1, 2, 3" }
        """;

    // when && then
    var targetType = assertDoesNotThrow(() -> mapper.readValue(json, TargetTypeListLong.class));
    assertThat(targetType.props).containsExactly(1L, 2L, 3L);
  }

  @Test
  void feelDeserializer_notFeel_stringListInteger_parsed() {
    // given
    String json = """
        { "props": "1, 2, 3" }
        """;

    // when && then
    var targetType = assertDoesNotThrow(() -> mapper.readValue(json, TargetTypeListInteger.class));
    assertThat(targetType.props).containsExactly(1, 2, 3);
  }

  @Test
  void feelDeserializer_notFeel_stringList_parsed() {
    // given
    String json = """
        { "props": "a, b, c" }
        """;

    // when && then
    var targetType = assertDoesNotThrow(() -> mapper.readValue(json, TargetTypeList.class));
    assertThat(targetType.props).contains("a", "b", "c");
  }

  @Test
  void feelDeserializer_notFeel_string_parsed() {
    // given
    String json = """
        { "props": "a, b, c" }
        """;

    // when && then
    var targetType = assertDoesNotThrow(() -> mapper.readValue(json, TargetTypeString.class));
    assertThat(targetType.props).isEqualTo("a, b, c");
  }

  @Test
  void feelDeserializer_contextSupplied_valid() {
    // given
    String json = """
        { "props": "= { first: a, second: b }" }
        """;
    Supplier<Map<String, String>> supplier = () -> Map.of("a", "value1", "b", "value2");
    var objectReader = FeelContextAwareObjectReader.of(mapper).withContextSupplier(supplier);

    // when && then
    var targetType = assertDoesNotThrow(() -> objectReader.readValue(json, TargetTypeMap.class));
    assertThat(targetType.props).containsEntry("first", "value1");
    assertThat(targetType.props).containsEntry("second", "value2");
  }

  @Test
  void feelDeserializer_contextSupplied_invalidObjectProvided() {
    // given
    String json = """
        { "props": "= { first: a, second: b }" }
        """;
    var objectReader =
        mapper
            .readerFor(TargetTypeMap.class)
            .withAttribute(
                FeelContextAwareObjectReader.FEEL_CONTEXT_ATTRIBUTE,
                Map.of("a", "value1", "b", "value2")); // map is not a supplier

    // when && then
    var e = assertThrows(JsonMappingException.class, () -> objectReader.readValue(json));
    assertThat(e.getMessage()).contains("Attribute FEEL_CONTEXT must be a Supplier");
  }

  @Test
  void feelDeserializer_notFeel_java8Time_parsed() {
    // this test is to ensure that deserialization takes active jackson modules into account

    // given
    String json = """
        { "props": "2019-01-01" }
        """;

    // when && then
    var targetType = assertDoesNotThrow(() -> mapper.readValue(json, TargetTypeJava8Time.class));
    assertThat(targetType.props).isEqualTo(LocalDate.of(2019, 1, 1));
  }

  @Test
  void feelDeserializer_notFeel_null_parsed() {
    // given
    String json = """
        { "props": null }
        """;

    // when && then
    var targetType = assertDoesNotThrow(() -> mapper.readValue(json, TargetTypeString.class));
    assertThat(targetType.props).isNull();
  }

  private record TargetTypeMap(@FEEL Map<String, String> props) {}

  private record TargetTypeObject(@FEEL StubObject stubObject) {}

  private record StubObject(Map<String, Object> props) {}

  private record TargetTypeString(@FEEL String props) {}

  private record TargetTypeArray(@FEEL Long[] props) {}

  private record TargetTypeList(@FEEL List<String> props) {}

  private record TargetTypeListLong(@FEEL List<Long> props) {}

  private record TargetTypeListInteger(@FEEL List<Integer> props) {}

  private record TargetTypeJava8Time(@FEEL LocalDate props) {}
}
