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
package io.camunda.connector.validator.core;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ConditionEvaluatorTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private static boolean evaluate(String condition, Map<String, String> assignment) {
    try {
      JsonNode node = MAPPER.readTree(condition);
      return ConditionEvaluator.evaluate(node, assignment);
    } catch (Exception e) {
      throw new IllegalArgumentException(e);
    }
  }

  @Test
  void equalsMatchesTheAssignedValue() {
    String condition =
        """
        { "property": "authType", "equals": "basic" }
        """;

    assertThat(evaluate(condition, Map.of("authType", "basic"))).isTrue();
    assertThat(evaluate(condition, Map.of("authType", "bearer"))).isFalse();
    assertThat(evaluate(condition, Map.of())).isFalse();
  }

  @Test
  void oneOfMatchesAnyListedValue() {
    String condition =
        """
        { "property": "authType", "oneOf": ["basic", "bearer"] }
        """;

    assertThat(evaluate(condition, Map.of("authType", "bearer"))).isTrue();
    assertThat(evaluate(condition, Map.of("authType", "apiKey"))).isFalse();
  }

  /**
   * The case that motivated adding {@code isEmpty} support: a property the assignment never
   * mentions is empty, so {@code isEmpty: true} must hold for it. Evaluating it as "unknown
   * property, therefore false" is what made {@code PresetConditionsSatisfiedRule} reject presets
   * pinning a property gated on an empty credential chooser.
   */
  @Test
  void isEmptyTrueHoldsForAnUnassignedProperty() {
    String condition =
        """
        { "property": "authenticationConfiguration", "isEmpty": true }
        """;

    assertThat(evaluate(condition, Map.of())).isTrue();
    assertThat(evaluate(condition, Map.of("authenticationConfiguration", ""))).isTrue();
    assertThat(evaluate(condition, Map.of("authenticationConfiguration", "   "))).isTrue();
    assertThat(evaluate(condition, Map.of("authenticationConfiguration", "cred-1"))).isFalse();
  }

  @Test
  void isEmptyFalseHoldsOnlyForAnAssignedNonBlankValue() {
    String condition =
        """
        { "property": "authenticationConfiguration", "isEmpty": false }
        """;

    assertThat(evaluate(condition, Map.of("authenticationConfiguration", "cred-1"))).isTrue();
    assertThat(evaluate(condition, Map.of("authenticationConfiguration", ""))).isFalse();
    assertThat(evaluate(condition, Map.of())).isFalse();
  }

  /** The composite shape the HTTP connectors emit: an auth-type choice gated on "no credential". */
  @Test
  void allMatchCombinesIsEmptyWithEquals() {
    String condition =
        """
        { "allMatch": [
            { "property": "authentication.type", "equals": "basic" },
            { "property": "authenticationConfiguration", "isEmpty": true }
        ] }
        """;

    assertThat(evaluate(condition, Map.of("authentication.type", "basic"))).isTrue();
    assertThat(
            evaluate(
                condition,
                Map.of("authentication.type", "basic", "authenticationConfiguration", "cred-1")))
        .isFalse();
    assertThat(evaluate(condition, Map.of("authentication.type", "bearer"))).isFalse();
  }

  @Test
  void anAbsentConditionAlwaysHolds() {
    assertThat(ConditionEvaluator.evaluate(null, Map.of())).isTrue();
    assertThat(ConditionEvaluator.evaluate(MAPPER.nullNode(), Map.of())).isTrue();
  }
}
