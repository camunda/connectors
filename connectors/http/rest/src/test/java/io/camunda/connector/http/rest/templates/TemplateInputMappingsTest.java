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
package io.camunda.connector.http.rest.templates;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.connector.http.rest.templates.TemplateInputMappings.Mode;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.AbstractMap.SimpleEntry;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * Pins the helper to the job variables Zeebe 8.10.9 produced for the same input mappings (Camunda 8
 * Run, variables read when the job was activated): a nested context is evaluated where its first
 * target appears, so its entries see only the mappings before that position and otherwise read
 * process variables.
 */
class TemplateInputMappingsTest {

  private static Map.Entry<String, String> input(String target, String source) {
    return new SimpleEntry<>(target, source);
  }

  private static Map<String, Object> nested(
      String key1, Object value1, String key2, Object value2) {
    Map<String, Object> map = new HashMap<>();
    map.put(key1, value1);
    map.put(key2, value2);
    return map;
  }

  @Test
  void nestedContextDoesNotSeeALaterPlainInput() {
    var locals =
        TemplateInputMappings.evaluate(
            List.of(input("a.x", "1"), input("b", "B-local"), input("a.y", "=b")), Map.of());

    assertThat(locals)
        .containsEntry("a", nested("x", "1", "y", null))
        .containsEntry("b", "B-local");
  }

  @Test
  void nestedContextSeesAnEarlierPlainInput() {
    var locals =
        TemplateInputMappings.evaluate(
            List.of(input("b", "B-local"), input("a.x", "1"), input("a.y", "=b")), Map.of());

    assertThat(locals).containsEntry("a", nested("x", "1", "y", "B-local"));
  }

  @Test
  void nestedContextReadsTheProcessVariableForALaterPlainInput() {
    var locals =
        TemplateInputMappings.evaluate(
            List.of(input("a.x", "1"), input("b", "B-local"), input("a.y", "=b")),
            Map.of("b", "B-process"));

    assertThat(locals)
        .containsEntry("a", nested("x", "1", "y", "B-process"))
        .containsEntry("b", "B-local");
  }

  @Test
  void plainInputReadsTheProcessVariableForALaterInput() {
    var locals =
        TemplateInputMappings.evaluate(
            List.of(input("c", "=d"), input("d", "D-local")), Map.of("d", "D-process"));

    assertThat(locals).containsEntry("c", "D-process").containsEntry("d", "D-local");
  }

  // The two cases below follow from FEEL context semantics (an entry sees the entries before it);
  // they were not part of the Zeebe 8.10 runs.

  @Test
  void nestedEntrySeesAnEarlierSibling() {
    var locals =
        TemplateInputMappings.evaluate(List.of(input("a.x", "1"), input("a.y", "=x")), Map.of());

    assertThat(locals).containsEntry("a", nested("x", "1", "y", "1"));
  }

  @Test
  void nestedEntryPrefersAnEarlierSiblingToAProcessVariable() {
    var locals =
        TemplateInputMappings.evaluate(
            List.of(input("a.x", "1"), input("a.y", "=x")), Map.of("x", "X-process"));

    assertThat(locals).containsEntry("a", nested("x", "1", "y", "1"));
  }

  /**
   * The opt-in {@code ordered} mode, read from {@code OrderedInputMappingResolver}, {@code
   * InputMappingResultBuilder} and {@code MappingLevels} in zeebe-workflow-engine 8.10.9 (javap).
   */
  @Nested
  class OrderedMode {

    private static Map<String, Object> evaluate(
        List<Map.Entry<String, String>> mappings, Map<String, Object> processVariables) {
      return TemplateInputMappings.evaluate(Mode.ORDERED, mappings, processVariables);
    }

    @Test
    void nestedTargetSeesAPlainInputMappedBeforeIt() {
      var locals =
          evaluate(List.of(input("a.x", "1"), input("b", "B-local"), input("a.y", "=b")), Map.of());

      assertThat(locals)
          .containsEntry("a", nested("x", "1", "y", "B-local"))
          .containsEntry("b", "B-local");
    }

    @Test
    void nestedTargetSeesAnEarlierPlainInput() {
      var locals =
          evaluate(List.of(input("b", "B-local"), input("a.x", "1"), input("a.y", "=b")), Map.of());

      assertThat(locals).containsEntry("a", nested("x", "1", "y", "B-local"));
    }

    @Test
    void earlierInputWinsOverTheProcessVariable() {
      var locals =
          evaluate(
              List.of(input("a.x", "1"), input("b", "B-local"), input("a.y", "=b")),
              Map.of("b", "B-process"));

      assertThat(locals)
          .containsEntry("a", nested("x", "1", "y", "B-local"))
          .containsEntry("b", "B-local");
    }

    @Test
    void plainInputReadsTheProcessVariableForALaterInput() {
      var locals =
          evaluate(List.of(input("c", "=d"), input("d", "D-local")), Map.of("d", "D-process"));

      assertThat(locals).containsEntry("c", "D-process").containsEntry("d", "D-local");
    }

    @Test
    void nestedEntryDoesNotSeeASiblingByItsBareName() {
      var locals = evaluate(List.of(input("a.x", "1"), input("a.y", "=x")), Map.of());

      assertThat(locals).containsEntry("a", nested("x", "1", "y", null));
    }

    @Test
    void nestedEntryReadsTheProcessVariableNamedLikeASibling() {
      var locals =
          evaluate(List.of(input("a.x", "1"), input("a.y", "=x")), Map.of("x", "X-process"));

      assertThat(locals).containsEntry("a", nested("x", "1", "y", "X-process"));
    }

    @Test
    void laterInputReadsAnEarlierNestedTargetByItsPath() {
      var locals = evaluate(List.of(input("a.x", "1"), input("c", "=a.x")), Map.of());

      assertThat(locals).containsEntry("c", "1");
    }

    @Test
    void nestedTargetReadsAsItsFieldsOverTheProcessVariable() {
      var locals =
          evaluate(
              List.of(input("a.x", "1"), input("c", "=a")), Map.of("a", Map.of("z", "Z-process")));

      assertThat(locals)
          .containsEntry("c", nested("z", "Z-process", "x", "1"))
          .containsEntry("a", Map.of("x", "1"));
    }
  }

  /**
   * Desktop Modeler 5.53 maps a property when {@code value || !optional} holds; with no value the
   * mapping has no source, and Zeebe evaluates that to null ({@code NullExpression}).
   */
  @ParameterizedTest
  @EnumSource(Mode.class)
  void requiredInputWithoutValueIsMappedToNull(Mode mode, @TempDir Path dir) throws IOException {
    Path template = dir.resolve("template.json");
    Files.writeString(
        template,
        """
        {"properties": [
          {"type": "String", "binding": {"type": "zeebe:input", "name": "required"}},
          {"type": "String", "optional": true,
           "binding": {"type": "zeebe:input", "name": "optional"}},
          {"type": "Hidden", "value": "=[required, optional]",
           "binding": {"type": "zeebe:input", "name": "both"}}
        ]}
        """);
    var mappings = TemplateInputMappings.load(template.toString(), mode);

    assertThat(mappings.mappings(Map.of()))
        .containsExactly(input("required", null), input("both", "=[required, optional]"));
    var locals = mappings.apply(Map.of(), Map.of("required", "R-process", "optional", "O-process"));
    assertThat(locals)
        .containsEntry("required", null)
        .containsEntry("both", Arrays.asList(null, "O-process"));
  }
}
