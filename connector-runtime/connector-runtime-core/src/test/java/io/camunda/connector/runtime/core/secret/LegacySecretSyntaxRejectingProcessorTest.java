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
package io.camunda.connector.runtime.core.secret;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.connector.feel.EvaluationResultProcessor;
import io.camunda.connector.runtime.core.secret.LegacySecretSyntaxRejectingProcessor.LegacySecretSyntaxException;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class LegacySecretSyntaxRejectingProcessorTest {

  private final EvaluationResultProcessor substituting =
      (result, referencedSecrets) -> "SUBSTITUTED";
  private final LegacySecretSyntaxRejectingProcessor processor =
      new LegacySecretSyntaxRejectingProcessor(substituting);

  @ParameterizedTest
  @ValueSource(strings = {"{{secrets.TOKEN}}", "secrets.TOKEN", "Bearer {{secrets.TOKEN}}"})
  void rejectsAResultCarryingTheLegacySyntax(String value) {
    assertThatThrownBy(() -> processor.process(value, List.of()))
        .isInstanceOf(LegacySecretSyntaxException.class);
  }

  @Test
  void rejectsTheLegacySyntaxNestedInTheResult() {
    Object nested = Map.of("auth", List.of(Map.of("password", "{{secrets.TOKEN}}")));

    assertThatThrownBy(() -> processor.process(nested, List.of()))
        .isInstanceOf(LegacySecretSyntaxException.class);
  }

  @Test
  void checksTheResultBeforeAnySecretValueIsSubstituted() {
    // The whole point of the ordering: a resolved secret whose value happens to contain
    // "secrets." text must not be read as a configuration using unsupported syntax.
    EvaluationResultProcessor substitutesLegacyLookingValue =
        (result, referencedSecrets) -> "secrets.this-came-from-a-secret-value";
    var ordered = new LegacySecretSyntaxRejectingProcessor(substitutesLegacyLookingValue);

    assertThat(ordered.process("camunda.secrets.TOKEN", List.of()))
        .isEqualTo("secrets.this-came-from-a-secret-value");
  }

  @Test
  void passesACleanResultToTheDelegate() {
    assertThat(processor.process("camunda.secrets.TOKEN", List.of())).isEqualTo("SUBSTITUTED");
  }

  @Test
  void toleratesNullsInTheResult() {
    var withNulls = new LinkedHashMap<String, Object>();
    withNulls.put("empty", null);
    withNulls.put("list", Arrays.asList(null, "clean"));

    assertThat(processor.process(withNulls, List.of())).isEqualTo("SUBSTITUTED");
    assertThat(processor.process(null, List.of())).isEqualTo("SUBSTITUTED");
  }
}
