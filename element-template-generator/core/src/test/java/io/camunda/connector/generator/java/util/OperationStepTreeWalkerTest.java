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
package io.camunda.connector.generator.java.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.connector.api.annotation.Operation;
import io.camunda.connector.generator.dsl.LeafStep;
import io.camunda.connector.generator.dsl.Preset;
import io.camunda.connector.util.reflection.ReflectionUtil;
import io.camunda.connector.util.reflection.ReflectionUtil.MethodWithAnnotation;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class OperationStepTreeWalkerTest {

  @Test
  void methodsWithKeywords_emitFlatLeafStepsAndPresets() {
    List<MethodWithAnnotation<Operation>> methods =
        ReflectionUtil.getMethodsAnnotatedWith(BothAnnotated.class, Operation.class);

    StepTreeResult result = OperationStepTreeWalker.walk(methods);

    assertThat(result.steps()).hasSize(2);
    assertThat(result.steps()).allMatch(LeafStep.class::isInstance);

    LeafStep read = leafByPresetId(result, "operation_readCsv");
    assertThat(read.name()).isEqualTo("Read CSV");
    assertThat(read.description()).isEqualTo("Reads CSV data");
    assertThat(read.keywords()).containsExactly("read csv", "parse");

    LeafStep write = leafByPresetId(result, "operation_writeCsv");
    // No `name` set on writeCsv → falls back to the operation id.
    assertThat(write.name()).isEqualTo("writeCsv");
    assertThat(write.description()).isNull();
    assertThat(write.keywords()).containsExactly("write csv");

    assertThat(result.presets())
        .extracting(Preset::id)
        .containsExactlyInAnyOrder("operation_readCsv", "operation_writeCsv");
    Preset readPreset = presetById(result, "operation_readCsv");
    assertThat(readPreset.properties()).containsExactly(Map.entry("operation", "readCsv"));
  }

  @Test
  void noMethodHasKeywords_returnsEmpty() {
    List<MethodWithAnnotation<Operation>> methods =
        ReflectionUtil.getMethodsAnnotatedWith(NoKeywords.class, Operation.class);

    StepTreeResult result = OperationStepTreeWalker.walk(methods);

    assertThat(result.isEmpty()).isTrue();
  }

  @Test
  void emptyMethodList_returnsEmpty() {
    assertThat(OperationStepTreeWalker.walk(List.of()).isEmpty()).isTrue();
    assertThat(OperationStepTreeWalker.walk(null).isEmpty()).isTrue();
  }

  @Test
  void partialKeywords_hardFailsOnMethodMissingKeywords() {
    List<MethodWithAnnotation<Operation>> methods =
        ReflectionUtil.getMethodsAnnotatedWith(PartiallyAnnotated.class, Operation.class);

    assertThatThrownBy(() -> OperationStepTreeWalker.walk(methods))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("operation2")
        .hasMessageContaining("keywords");
  }

  // ---------- Fixtures ----------

  static class BothAnnotated {
    @Operation(
        id = "readCsv",
        name = "Read CSV",
        description = "Reads CSV data",
        keywords = {"read csv", "parse"})
    public Object readCsv() {
      return null;
    }

    @Operation(
        id = "writeCsv",
        keywords = {"write csv"})
    public Object writeCsv() {
      return null;
    }
  }

  static class NoKeywords {
    @Operation(id = "op1", name = "Operation 1")
    public Object op1() {
      return null;
    }

    @Operation(id = "op2", name = "Operation 2")
    public Object op2() {
      return null;
    }
  }

  static class PartiallyAnnotated {
    @Operation(
        id = "operation1",
        keywords = {"kw"})
    public Object operation1() {
      return null;
    }

    @Operation(id = "operation2")
    public Object operation2() {
      return null;
    }
  }

  private static LeafStep leafByPresetId(StepTreeResult result, String presetId) {
    return result.steps().stream()
        .map(LeafStep.class::cast)
        .filter(l -> l.presetId().equals(presetId))
        .findFirst()
        .orElseThrow();
  }

  private static Preset presetById(StepTreeResult result, String id) {
    return result.presets().stream().filter(p -> p.id().equals(id)).findFirst().orElseThrow();
  }
}
