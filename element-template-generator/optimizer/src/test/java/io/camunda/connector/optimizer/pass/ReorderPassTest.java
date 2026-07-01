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
import org.junit.jupiter.api.Test;

class ReorderPassTest {

  private final ReorderPass pass = new ReorderPass();

  @Test
  void shouldOrderByGroupThenVisibilityThenId() {
    ElementTemplate input =
        template(
            stringProperty("z_field", "groupB"),
            stringProperty("a_field", "groupB"),
            hiddenPropertyWithGroup("hidden", "groupA"),
            stringProperty("visible", "groupA"),
            stringProperty("no_group"));

    ElementTemplate result = pass.apply(input);

    var properties = result.properties();
    assertThat(properties).hasSize(5);

    // Properties without group come first
    assertThat(properties.get(0).getId()).isEqualTo("no_group");

    // Then groupA (alphabetically before groupB)
    assertThat(properties.get(1).getId()).isEqualTo("hidden"); // Hidden comes before visible
    assertThat(properties.get(2).getId()).isEqualTo("visible");

    // Then groupB
    assertThat(properties.get(3).getId()).isEqualTo("a_field"); // Alphabetically first
    assertThat(properties.get(4).getId()).isEqualTo("z_field");
  }

  @Test
  void shouldHandlePropertiesWithoutGroups() {
    ElementTemplate input =
        template(
            stringProperty("c_field"),
            stringProperty("a_field"),
            hiddenPropertyWithGroup("b_field", null));

    ElementTemplate result = pass.apply(input);

    var properties = result.properties();
    assertThat(properties.get(0).getId()).isEqualTo("b_field"); // Hidden first
    assertThat(properties.get(1).getId()).isEqualTo("a_field"); // Then alphabetically
    assertThat(properties.get(2).getId()).isEqualTo("c_field");
  }
}
