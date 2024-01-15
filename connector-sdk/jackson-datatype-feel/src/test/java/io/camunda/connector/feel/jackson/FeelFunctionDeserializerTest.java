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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

public class FeelFunctionDeserializerTest {

  private final ObjectMapper mapper =
      new ObjectMapper()
          .registerModule(new JacksonModuleFeelFunction())
          .registerModule(new JavaTimeModule());

  @Test
  void feelFunctionDeserialization_objectResult() throws JsonProcessingException {
    // given
    String json = """
        { "function": "= { result: a + b }" }
        """;

    // when
    TargetTypeObject targetType = mapper.readValue(json, TargetTypeObject.class);

    // then
    InputContextString inputContext = new InputContextString("foo", "bar");
    OutputContext result = targetType.function().apply(inputContext);
    assertThat(result).isInstanceOf(OutputContext.class);
    assertThat(result.result).isEqualTo("foobar");
  }

  @Test
  void feelFunctionDeserialization_stringResult() throws JsonProcessingException {
    // given
    String json = """
        { "function": "= a + b" }
        """;

    // when
    TargetTypeString targetType = mapper.readValue(json, TargetTypeString.class);

    // then
    InputContextString inputContext = new InputContextString("foo", "bar");
    String result = targetType.function().apply(inputContext);
    assertThat(result).isEqualTo("foobar");
  }

  @Test
  void feelFunctionDeserialization_booleanResult() throws JsonProcessingException {
    // given
    String json = """
        { "function": "= a = b" }
        """;

    // when
    TargetTypeBoolean targetType = mapper.readValue(json, TargetTypeBoolean.class);

    // then
    InputContextString inputContext = new InputContextString("foo", "bar");
    Boolean result = targetType.function().apply(inputContext);
    assertThat(result).isFalse();
  }

  @Test
  void feelFunctionDeserialization_integerResult() throws JsonProcessingException {
    // given
    String json = """
        { "function": "= a + b" }
        """;

    // when
    TargetTypeInteger targetType = mapper.readValue(json, TargetTypeInteger.class);

    // then
    InputContextInteger inputContext = new InputContextInteger(3, 5);
    Integer result = targetType.function().apply(inputContext);
    assertThat(result).isEqualTo(8);
  }

  @Test
  void feelFunctionDeserialization_nullResult() throws JsonProcessingException {
    // given
    String json = """
        { "function": "= null" }
        """;

    // when
    TargetTypeObject targetType = mapper.readValue(json, TargetTypeObject.class);

    // then
    InputContextString inputContext = new InputContextString("foo", "bar");
    Object result = targetType.function().apply(inputContext);
    assertThat(result).isNull();
  }

  @Test
  void feelSupplierDeserialization_listResult() throws JsonProcessingException {
    // given
    String json = """
        { "function": "= [a, b]" }
        """;

    // when
    TargetTypeList targetType = mapper.readValue(json, TargetTypeList.class);

    // then
    InputContextInteger inputContext = new InputContextInteger(3, 5);
    List<Long> result = targetType.function().apply(inputContext);
    assertThat(result).containsExactlyElementsOf(List.of(3L, 5L));
  }

  @Test
  void feelSupplierDeserialization_mapResult() throws JsonProcessingException {
    // given
    String json = """
        { "function": "= { foo: a + b }" }
        """;

    // when
    TargetTypeMap targetType = mapper.readValue(json, TargetTypeMap.class);

    // then
    InputContextInteger inputContext = new InputContextInteger(3, 5);
    Map<String, Long> result = targetType.function().apply(inputContext);
    assertThat(result).containsEntry("foo", 8L);
  }

  @Test
  void feelSupplierDeserialization_foldedMapResult() throws JsonProcessingException {
    // given
    String json = """
        { "function": "= { foo: {bar: a + b, baz: b - a} }" }
        """;

    // when
    TargetTypeFoldedMap targetType = mapper.readValue(json, TargetTypeFoldedMap.class);

    // then
    InputContextInteger inputContext = new InputContextInteger(3, 5);
    var result = targetType.function().apply(inputContext);
    assertThat(result).containsKey("foo");
    assertThat((Map) result.get("foo")).containsEntry("bar", 8L);
    assertThat((Map) result.get("foo")).containsEntry("baz", 2L);
  }

  @Test
  void feelFunctionDeserialization_convertFromMap() {
    // given
    var jsonAsMap = Map.of("function", "= { result: a + b }");

    // when
    TargetTypeObject targetType = mapper.convertValue(jsonAsMap, TargetTypeObject.class);

    // then
    InputContextString inputContext = new InputContextString("foo", "bar");
    OutputContext result = targetType.function().apply(inputContext);
    assertThat(result).isInstanceOf(OutputContext.class);
    assertThat(result.result).isEqualTo("foobar");
  }

  @Test
  void feelFunctionDeserialization_contextAware_mergedWithInput() throws IOException {
    // given
    var json = """
        { "function": "= { result: a + c }" }
        """;
    var contextualReader =
        FeelContextAwareObjectReader.of(mapper).withStaticContext(Map.of("c", "bar"));

    // when
    TargetTypeObject targetType = contextualReader.readValue(json, TargetTypeObject.class);

    // then
    InputContextString inputContext = new InputContextString("foo", null);
    OutputContext result = targetType.function().apply(inputContext);
    assertThat(result).isInstanceOf(OutputContext.class);
    assertThat(result.result).isEqualTo("foobar");
  }

  @Test
  void feelFunctionDeserlization_contextAware_knowsJava8Time() throws IOException {
    // given
    var json = """
        { "function": "= string(date(2021, 1, 1))" }
        """;
    var contextualReader =
        FeelContextAwareObjectReader.of(mapper).withStaticContext(Map.of("c", "bar"));

    // when
    TargetTypeJava8Time targetType = contextualReader.readValue(json, TargetTypeJava8Time.class);

    // then
    InputContextInteger inputContext = new InputContextInteger(3, 5);
    LocalDate result = targetType.function().apply(inputContext);
    assertThat(result).isEqualTo(LocalDate.of(2021, 1, 1));
  }

  private record InputContextString(String a, String b) {}

  private record InputContextInteger(Integer a, Integer b) {}

  private record OutputContext(String result) {}

  private record TargetTypeObject(Function<InputContextString, OutputContext> function) {}

  private record TargetTypeString(Function<InputContextString, String> function) {}

  private record TargetTypeBoolean(Function<InputContextString, Boolean> function) {}

  private record TargetTypeInteger(Function<InputContextInteger, Integer> function) {}

  private record TargetTypeList(Function<InputContextInteger, List<Long>> function) {}

  private record TargetTypeMap(Function<InputContextInteger, Map<String, Long>> function) {}

  private record TargetTypeFoldedMap(Function<InputContextInteger, Map<String, Object>> function) {}

  private record TargetTypeJava8Time(Function<InputContextInteger, LocalDate> function) {}
}
