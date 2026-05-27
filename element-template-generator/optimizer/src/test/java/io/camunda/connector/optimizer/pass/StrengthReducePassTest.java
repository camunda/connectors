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

class StrengthReducePassTest {

  private final StrengthReducePass pass = new StrengthReducePass();

  @Test
  void shouldConvertSingletonOneOfToEquals() {
    ElementTemplate input =
        template(hiddenProperty("field", "val", zeebeInput("field"), oneOf("op", "search")));

    ElementTemplate result = pass.apply(input);

    var condition = result.properties().get(0).getCondition();
    assertThat(condition).isInstanceOf(PropertyCondition.Equals.class);
    var equals = (PropertyCondition.Equals) condition;
    assertThat(equals.property()).isEqualTo("op");
    assertThat(equals.equals()).isEqualTo("search");
  }

  @Test
  void shouldKeepMultiValueOneOf() {
    ElementTemplate input =
        template(
            hiddenProperty(
                "field", "val", zeebeInput("field"), oneOf("op", "search", "autocomplete")));

    ElementTemplate result = pass.apply(input);

    var condition = result.properties().get(0).getCondition();
    assertThat(condition).isInstanceOf(PropertyCondition.OneOf.class);
    var oneOf = (PropertyCondition.OneOf) condition;
    assertThat(oneOf.oneOf()).hasSize(2);
  }

  @Test
  void shouldHandlePropertiesWithoutConditions() {
    ElementTemplate input = template(hiddenProperty("field", "val", zeebeInput("field")));

    ElementTemplate result = pass.apply(input);

    assertThat(result.properties().get(0).getCondition()).isNull();
  }
}
