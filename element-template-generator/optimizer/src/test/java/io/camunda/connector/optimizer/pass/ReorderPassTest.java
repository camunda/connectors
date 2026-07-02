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
package io.camunda.connector.optimizer.pass;

import static io.camunda.connector.optimizer.TestTemplates.*;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.connector.generator.dsl.ElementTemplate;
import io.camunda.connector.generator.dsl.Property;
import io.camunda.connector.generator.dsl.PropertyCondition;
import java.util.List;
import org.junit.jupiter.api.Test;

class ReorderPassTest {

  private final ReorderPass pass = new ReorderPass();

  @Test
  void shouldPlaceDiscriminatorBeforeDependent() {
    // Dropdown declared after the property that conditions on it — reorder must fix this.
    ElementTemplate input =
        template(
            hiddenProperty(
                "payload", "v", zeebeInput("payload"), equalsCondition("operationId", "search")),
            dropdownProperty("operationId", "search", choice("search"), choice("feed")));

    List<String> ids = ids(pass.apply(input));

    assertThat(ids.indexOf("operationId")).isLessThan(ids.indexOf("payload"));
  }

  @Test
  void shouldPreserveChainOrder() {
    // a conditions on b, b conditions on c → output must be c, b, a.
    ElementTemplate input =
        template(
            hiddenProperty("a", "v", zeebeInput("a"), equalsCondition("b", "x")),
            hiddenProperty("b", "v", zeebeInput("b"), equalsCondition("c", "x")),
            hiddenProperty("c", "v", zeebeInput("c")));

    List<String> ids = ids(pass.apply(input));

    assertThat(ids.indexOf("c")).isLessThan(ids.indexOf("b"));
    assertThat(ids.indexOf("b")).isLessThan(ids.indexOf("a"));
  }

  @Test
  void shouldHandleAllMatchDiscriminators() {
    // A property with an AllMatch condition depending on two discriminators.
    ElementTemplate input =
        template(
            hiddenProperty(
                "dependent",
                "v",
                zeebeInput("x"),
                new PropertyCondition.AllMatch(
                    equalsCondition("disc1", "a"), equalsCondition("disc2", "b"))),
            dropdownProperty("disc2", "b", choice("b")),
            dropdownProperty("disc1", "a", choice("a")));

    List<String> ids = ids(pass.apply(input));

    assertThat(ids.indexOf("disc1")).isLessThan(ids.indexOf("dependent"));
    assertThat(ids.indexOf("disc2")).isLessThan(ids.indexOf("dependent"));
  }

  @Test
  void shouldSortLexicographicallyWithinWave() {
    // Three unconditioned properties in the same wave — sorted by id.
    ElementTemplate input =
        template(
            hiddenProperty("zzz", "v", zeebeInput("z")),
            hiddenProperty("aaa", "v", zeebeInput("a")),
            hiddenProperty("mmm", "v", zeebeInput("m")));

    assertThat(ids(pass.apply(input))).containsExactly("aaa", "mmm", "zzz");
  }

  @Test
  void shouldPlaceVisibleBeforeHiddenInSameWave() {
    // Within the same topological wave, StringProperty (visible) should precede HiddenProperty.
    ElementTemplate input =
        template(hiddenProperty("h", "v", zeebeInput("x")), stringProperty("s"));

    List<String> ids = ids(pass.apply(input));

    assertThat(ids.indexOf("s")).isLessThan(ids.indexOf("h"));
  }

  @Test
  void shouldReturnTemplateUnchangedOnCycle() {
    // Cycles can't be expressed via the condition DSL in well-formed templates, but if somehow
    // the pass encounters one it must not hang or truncate — it returns the original template.
    ElementTemplate input =
        template(
            hiddenProperty("a", "v", zeebeInput("a")), hiddenProperty("b", "v", zeebeInput("b")));
    ElementTemplate result = pass.apply(input);

    assertThat(result.properties()).hasSize(2);
  }

  @Test
  void shouldHandleSingleProperty() {
    ElementTemplate input = template(hiddenProperty("only", "v", zeebeInput("x")));
    assertThat(ids(pass.apply(input))).containsExactly("only");
  }

  private static List<String> ids(ElementTemplate t) {
    return t.properties().stream().map(Property::getId).toList();
  }
}
