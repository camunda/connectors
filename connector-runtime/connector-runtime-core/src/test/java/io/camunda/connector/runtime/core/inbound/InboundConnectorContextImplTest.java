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
package io.camunda.connector.runtime.core.inbound;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.camunda.connector.api.annotation.FEEL;
import io.camunda.connector.api.secret.SecretProvider;
import io.camunda.connector.impl.feel.jackson.JacksonModuleFeelFunction;
import io.camunda.connector.impl.inbound.MessageCorrelationPoint;
import io.camunda.connector.runtime.core.FooBarSecretProvider;
import io.camunda.connector.runtime.core.inbound.InboundConnectorContextImplTest.TestPropertiesClass.InnerObject;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.junit.jupiter.api.Test;

class InboundConnectorContextImplTest {
  private final SecretProvider secretProvider = new FooBarSecretProvider();
  private final ObjectMapper mapper =
      new ObjectMapper()
          .registerModule(new JavaTimeModule())
          .registerModule(new JacksonModuleFeelFunction())
          // deserialize unknown types as empty objects
          .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
          .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

  @Test
  void bindProperties_shouldThrowExceptionWhenWrongFormat() {
    // given
    InboundConnectorDefinitionImpl definition =
        new InboundConnectorDefinitionImpl(
            Map.of("stringMap", "={{\"key\":\"value\"}"),
            new MessageCorrelationPoint("", ""),
            "bool",
            0,
            0L,
            "id");
    InboundConnectorContextImpl inboundConnectorContext =
        new InboundConnectorContextImpl(
            secretProvider, (e) -> {}, definition, null, (e) -> {}, mapper);
    // when and then
    RuntimeException exception =
        assertThrows(
            RuntimeException.class,
            () -> inboundConnectorContext.bindProperties(TestPropertiesClass.class));
    assertThat(exception.getMessage()).contains("Failed to evaluate expression");
  }

  @Test
  void bindProperties_shouldParseNullValue() {
    // given
    InboundConnectorDefinitionImpl definition =
        new InboundConnectorDefinitionImpl(
            Map.of("stringMap", "={\"keyString\":null}"),
            new MessageCorrelationPoint("", ""),
            "bool",
            0,
            0L,
            "id");
    InboundConnectorContextImpl inboundConnectorContext =
        new InboundConnectorContextImpl(
            secretProvider, (e) -> {}, definition, null, (e) -> {}, mapper);
    // when
    TestPropertiesClass propertiesAsType =
        inboundConnectorContext.bindProperties(TestPropertiesClass.class);
    // then
    assertThat(propertiesAsType.getStringMap().containsKey("keyString")).isTrue();
    assertThat(propertiesAsType.getStringMap().get("keyString")).isNull();
    System.out.println(propertiesAsType.getMapWithStringListWithNumbers());
  }

  @Test
  void bindProperties_shouldParseStringAsString() {
    // given
    InboundConnectorDefinitionImpl definition =
        new InboundConnectorDefinitionImpl(
            Map.of(
                "mapWithStringListWithNumbers", "={key:[\"34\", \"45\", \"890\",\"0\",\"16785\"]}"),
            new MessageCorrelationPoint("", ""),
            "bool",
            0,
            0L,
            "id");
    InboundConnectorContextImpl inboundConnectorContext =
        new InboundConnectorContextImpl(
            secretProvider, (e) -> {}, definition, null, (e) -> {}, mapper);
    // when
    TestPropertiesClass propertiesAsType =
        inboundConnectorContext.bindProperties(TestPropertiesClass.class);
    // then
    assertThat(propertiesAsType.getMapWithStringListWithNumbers().get("key").get(0))
        .isInstanceOf(String.class);
  }

  @Test
  void bindProperties_shouldParseAllObject() {
    // Given
    InboundConnectorDefinitionImpl definition =
        new InboundConnectorDefinitionImpl(
            Map.of(
                "stringMap",
                "={\"keyString\":\"valueString\"}",
                "stringMapMap",
                "={\"keyString\":{\"innerKeyString\":\"innerValueString\"}}",
                "stringList",
                "=[\"value1\", \"value2\", \"value3\"]",
                "numberList",
                "=[34, -45, 890, 0, -16785]",
                "str",
                "foo",
                "bool",
                "=true",
                "mapWithNumberList",
                "={\"key\":[43, 0, -123]}",
                "mapWithStringListWithNumbers",
                "={\"key\":[\"34\", \"45\", \"890\",\"0\",\"16785\"]}",
                "stringNumberList",
                "=[\"34\", \"-45\", \"890\", \"0\", \"-16785\"]",
                "stringObjectMap",
                "={\"innerObject\":{\"stringList\":[\"innerList\"], \"bool\":true}}"),
            new MessageCorrelationPoint("", ""),
            "bool",
            0,
            0L,
            "id");
    InboundConnectorContextImpl inboundConnectorContext =
        new InboundConnectorContextImpl(
            secretProvider, (e) -> {}, definition, null, (e) -> {}, mapper);
    // when
    TestPropertiesClass propertiesAsType =
        inboundConnectorContext.bindProperties(TestPropertiesClass.class);
    // then
    assertThat(propertiesAsType).isEqualTo(createTestClass());
  }

  @Test
  void getProperties_shouldNotParseFeel() {
    // given
    InboundConnectorDefinitionImpl definition =
        new InboundConnectorDefinitionImpl(
            Map.of("stringMap", "={\"keyString\":null}"),
            new MessageCorrelationPoint("", ""),
            "bool",
            0,
            0L,
            "id");

    InboundConnectorContextImpl inboundConnectorContext =
        new InboundConnectorContextImpl(
            secretProvider, (e) -> {}, definition, null, (e) -> {}, mapper);

    // when
    Map<String, Object> properties = inboundConnectorContext.getProperties();

    // then
    assertThat(properties.get("stringMap")).isEqualTo("={\"keyString\":null}");
  }

  private TestPropertiesClass createTestClass() {
    TestPropertiesClass testClass = new TestPropertiesClass();
    testClass.setStringMap(Map.of("keyString", "valueString"));
    testClass.setStringMapMap(Map.of("keyString", Map.of("innerKeyString", "innerValueString")));
    testClass.setStringList(List.of("value1", "value2", "value3"));
    testClass.setNumberList(List.of(34, -45, 890, 0, -16785));
    testClass.setStringNumberList(List.of("34", "-45", "890", "0", "-16785"));
    testClass.setStr("foo");
    testClass.setBool(true);
    testClass.setMapWithNumberList(Map.of("key", List.of(43L, 0L, -123L)));
    var innerObject = new InnerObject(List.of("innerList"), true);
    testClass.setStringObjectMap(Map.of("innerObject", innerObject));
    testClass.setMapWithStringListWithNumbers(
        Map.of("key", List.of("34", "45", "890", "0", "16785")));
    return testClass;
  }

  public static class TestPropertiesClass {
    @FEEL private Map<String, String> stringMap;
    @FEEL private Map<String, Map<String, String>> stringMapMap;
    @FEEL private Map<String, InnerObject> stringObjectMap;
    @FEEL private List<String> stringList;
    @FEEL private List<Integer> numberList;
    @FEEL private List<String> stringNumberList;
    @FEEL private Map<String, List<Long>> mapWithNumberList;
    @FEEL private Map<String, List<String>> mapWithStringListWithNumbers;
    @FEEL private String str;
    @FEEL private boolean bool;

    public Map<String, String> getStringMap() {
      return stringMap;
    }

    public void setStringMap(final Map<String, String> stringMap) {
      this.stringMap = stringMap;
    }

    public void setStringMapMap(final Map<String, Map<String, String>> stringMapMap) {
      this.stringMapMap = stringMapMap;
    }

    public void setStringObjectMap(final Map<String, InnerObject> stringObjectMap) {
      this.stringObjectMap = stringObjectMap;
    }

    public void setStringList(final List<String> stringList) {
      this.stringList = stringList;
    }

    public void setNumberList(final List<Integer> numberList) {
      this.numberList = numberList;
    }

    public void setStringNumberList(final List<String> stringNumberList) {
      this.stringNumberList = stringNumberList;
    }

    public void setMapWithNumberList(final Map<String, List<Long>> mapWithNumberList) {
      this.mapWithNumberList = mapWithNumberList;
    }

    public Map<String, List<String>> getMapWithStringListWithNumbers() {
      return mapWithStringListWithNumbers;
    }

    public void setMapWithStringListWithNumbers(
        final Map<String, List<String>> mapWithStringListWithNumbers) {
      this.mapWithStringListWithNumbers = mapWithStringListWithNumbers;
    }

    public void setStr(final String str) {
      this.str = str;
    }

    public void setBool(final boolean bool) {
      this.bool = bool;
    }

    record InnerObject(List<String> stringList, boolean bool) {}

    @Override
    public boolean equals(final Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      final TestPropertiesClass that = (TestPropertiesClass) o;
      return bool == that.bool
          && Objects.equals(stringMap, that.stringMap)
          && Objects.equals(stringMapMap, that.stringMapMap)
          && Objects.equals(stringObjectMap, that.stringObjectMap)
          && Objects.equals(stringList, that.stringList)
          && Objects.equals(numberList, that.numberList)
          && Objects.equals(stringNumberList, that.stringNumberList)
          && Objects.equals(mapWithNumberList, that.mapWithNumberList)
          && Objects.equals(mapWithStringListWithNumbers, that.mapWithStringListWithNumbers)
          && Objects.equals(str, that.str);
    }

    @Override
    public int hashCode() {
      return Objects.hash(
          stringMap,
          stringMapMap,
          stringObjectMap,
          stringList,
          numberList,
          stringNumberList,
          mapWithNumberList,
          mapWithStringListWithNumbers,
          str,
          bool);
    }

    @Override
    public String toString() {
      return "TestPropertiesClass{"
          + "stringMap="
          + stringMap
          + ", stringMapMap="
          + stringMapMap
          + ", stringObjectMap="
          + stringObjectMap
          + ", stringList="
          + stringList
          + ", numberList="
          + numberList
          + ", stringNumberList="
          + stringNumberList
          + ", mapWithNumberList="
          + mapWithNumberList
          + ", mapWithStringListWithNumbers="
          + mapWithStringListWithNumbers
          + ", str='"
          + str
          + "'"
          + ", bool="
          + bool
          + "}";
    }
  }
}
