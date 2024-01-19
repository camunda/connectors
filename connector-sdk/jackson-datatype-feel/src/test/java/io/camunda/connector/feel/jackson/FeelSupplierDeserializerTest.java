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
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

public class FeelSupplierDeserializerTest {

  private final ObjectMapper mapper =
      new ObjectMapper()
          .registerModule(new JacksonModuleFeelFunction())
          .registerModule(new JavaTimeModule());

  @Test
  void feelSupplierDeserialization_objectResult() throws JsonProcessingException {
    // given
    String json = """
        { "supplier": "= { result: \\"foobar\\" }" }
        """;

    // when
    TargetTypeObject targetType = mapper.readValue(json, TargetTypeObject.class);

    // then
    OutputContext result = targetType.supplier().get();
    assertThat(result).isInstanceOf(OutputContext.class);
    assertThat(result.result).isEqualTo("foobar");
  }

  @Test
  void feelSupplierDeserialization_stringResult() throws JsonProcessingException {
    // given
    String json = """
        { "supplier": "= \\"foobar\\"" }
        """;

    // when
    TargetTypeString targetType = mapper.readValue(json, TargetTypeString.class);

    // then
    String result = targetType.supplier().get();
    assertThat(result).isEqualTo("foobar");
  }

  @Test
  void feelSupplierDeserialization_booleanResult() throws JsonProcessingException {
    // given
    String json = """
        { "supplier": "= true" }
        """;

    // when
    TargetTypeBoolean targetType = mapper.readValue(json, TargetTypeBoolean.class);

    // then
    Boolean result = targetType.supplier().get();
    assertThat(result).isTrue();
  }

  @Test
  void feelSupplierDeserialization_integerResult() throws JsonProcessingException {
    // given
    String json = """
        { "supplier": "= 42" }
        """;

    // when
    TargetTypeInteger targetType = mapper.readValue(json, TargetTypeInteger.class);

    // then
    Integer result = targetType.supplier.get();
    assertThat(result).isEqualTo(42);
  }

  @Test
  void feelSupplierDeserialization_nullResult() throws JsonProcessingException {
    // given
    String json = """
        { "supplier": "= null" }
        """;

    // when
    TargetTypeObject targetType = mapper.readValue(json, TargetTypeObject.class);

    // then
    OutputContext result = targetType.supplier().get();
    assertThat(result).isNull();
  }

  @Test
  void feelSupplierDeserialization_listResult() throws JsonProcessingException {
    // given
    String json = """
        { "supplier": "= [1, 2, 3]" }
        """;

    // when
    TargetTypeList targetType = mapper.readValue(json, TargetTypeList.class);

    // then
    List<Long> result = targetType.supplier().get();
    assertThat(result).containsExactlyElementsOf(List.of(1L, 2L, 3L));
  }

  @Test
  void feelSupplierDeserialization_mapResult() throws JsonProcessingException {
    // given
    String json = """
        { "supplier": "= { foo: \\"bar\\" }" }
        """;

    // when
    TargetTypeMap targetType = mapper.readValue(json, TargetTypeMap.class);

    // then
    Map<String, String> result = targetType.supplier().get();
    assertThat(result).containsEntry("foo", "bar");
  }

  @Test
  void feelSupplierDeserialization_convertFromMap() {
    // given
    var jsonAsMap = Map.of("supplier", "= { result: \"foobar\" }");

    // when
    TargetTypeObject targetType = mapper.convertValue(jsonAsMap, TargetTypeObject.class);

    // then
    OutputContext result = targetType.supplier().get();
    assertThat(result).isInstanceOf(OutputContext.class);
    assertThat(result.result).isEqualTo("foobar");
  }

  @Test
  void feelSupplierDeserialization_contextProvided() throws IOException {
    // given
    var json = """
        { "supplier": "= ctx.foo + ctx.bar" }
                """;
    var context = Map.of("ctx", Map.of("foo", "foo", "bar", "bar"));

    // when
    TargetTypeString targetType =
        FeelContextAwareObjectReader.of(mapper)
            .withStaticContext(context)
            .readValue(json, TargetTypeString.class);

    // then
    String result = targetType.supplier().get();
    assertThat(result).isEqualTo("foobar");
  }

  @Test
  void feelSupplierDeserialization_java8Time() throws IOException {
    // given
    var json = """
        { "supplier": "= string(date(2021,1,1))" }
        """;

    // when
    TargetTypeJava8Time targetType = mapper.readValue(json, TargetTypeJava8Time.class);

    // then
    LocalDate result = targetType.supplier().get();
    assertThat(result).isEqualTo(LocalDate.of(2021, 1, 1));
  }

  private record OutputContext(String result) {}

  private record TargetTypeObject(Supplier<OutputContext> supplier) {}

  private record TargetTypeString(Supplier<String> supplier) {}

  private record TargetTypeBoolean(Supplier<Boolean> supplier) {}

  private record TargetTypeInteger(Supplier<Integer> supplier) {}

  private record TargetTypeList(Supplier<List<Long>> supplier) {}

  private record TargetTypeMap(Supplier<Map<String, String>> supplier) {}

  private record TargetTypeJava8Time(Supplier<LocalDate> supplier) {}
}
