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
import io.camunda.connector.generator.dsl.PropertyCondition;
import org.junit.jupiter.api.Test;

class TotalizePassTest {

  private final TotalizePass pass = new TotalizePass();

  @Test
  void shouldRemoveConditionThatCoversAllChoices() {
    ElementTemplate input =
        template(
            dropdownProperty(
                "operationId", "search", choice("search"), choice("autocomplete"), choice("feed")),
            hiddenProperty(
                "field",
                "val",
                zeebeInput("field"),
                oneOf("operationId", "search", "autocomplete", "feed")));

    ElementTemplate result = pass.apply(input);

    var field = result.properties().get(1);
    assertThat(field.getCondition()).isNull();
  }

  @Test
  void shouldKeepPartialCondition() {
    ElementTemplate input =
        template(
            dropdownProperty(
                "operationId", "search", choice("search"), choice("autocomplete"), choice("feed")),
            hiddenProperty(
                "field",
                "val",
                zeebeInput("field"),
                oneOf("operationId", "search", "autocomplete")));

    ElementTemplate result = pass.apply(input);

    var field = result.properties().get(1);
    assertThat(field.getCondition()).isNotNull();
    assertThat(field.getCondition()).isInstanceOf(PropertyCondition.OneOf.class);
    var condition = (PropertyCondition.OneOf) field.getCondition();
    assertThat(condition.oneOf()).hasSize(2);
  }

  @Test
  void shouldHandleEqualsCondition() {
    ElementTemplate input =
        template(
            dropdownProperty("operationId", "search", choice("search")),
            hiddenProperty(
                "field", "val", zeebeInput("field"), equalsCondition("operationId", "search")));

    ElementTemplate result = pass.apply(input);

    var field = result.properties().get(1);
    assertThat(field.getCondition()).isNull();
  }

  @Test
  void shouldNotRemoveConditionForUnknownDiscriminator() {
    ElementTemplate input =
        template(
            hiddenProperty("field", "val", zeebeInput("field"), oneOf("unknownProp", "a", "b")));

    ElementTemplate result = pass.apply(input);

    var field = result.properties().get(0);
    assertThat(field.getCondition()).isNotNull();
  }
}
