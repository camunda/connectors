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

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class SecretReferenceUtilTest {

  @ParameterizedTest
  @CsvSource({
    "camunda.secrets.TOKEN,camunda.secrets.TOKEN",
    "=camunda.secrets.TOKEN,camunda.secrets.TOKEN",
    "Bearer camunda.secrets.TOKEN,camunda.secrets.TOKEN",
    "camunda.secrets.TOKEN_V2,camunda.secrets.TOKEN_V2",
    "camunda.secrets.a1B2,camunda.secrets.a1B2",
    "camunda.secrets.TOKEN.length,camunda.secrets.TOKEN",
    "camunda.secrets.db-password,camunda.secrets.db-password",
    "=camunda.secrets.my-api-key,camunda.secrets.my-api-key",
    "camunda.secrets.a-b-c,camunda.secrets.a-b-c"
  })
  void findsAReference(String input, String expected) {
    assertThat(SecretReferenceUtil.findReferences(input)).containsExactly(expected);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "camunda.secrets.",
        "camunda.secret.TOKEN",
        "secrets.TOKEN",
        "camundaXsecrets.TOKEN"
      })
  void findsNoReference(String input) {
    assertThat(SecretReferenceUtil.findReferences(input)).isEmpty();
  }

  @Test
  void findsNoReferenceInNull() {
    assertThat(SecretReferenceUtil.findReferences(null)).isEmpty();
  }

  @Test
  void reportsEachReferenceOnce() {
    assertThat(SecretReferenceUtil.findReferences("camunda.secrets.A camunda.secrets.A"))
        .containsExactly("camunda.secrets.A");
  }

  @Test
  void replacesEveryOccurrenceOfAKnownReference() {
    String result =
        SecretReferenceUtil.replaceReferences(
            "camunda.secrets.A/camunda.secrets.B/camunda.secrets.A",
            Map.of("camunda.secrets.A", "a-value", "camunda.secrets.B", "b-value"));

    assertThat(result).isEqualTo("a-value/b-value/a-value");
  }

  @Test
  void leavesAnUnknownReferenceExactlyAsItIs() {
    String result =
        SecretReferenceUtil.replaceReferences(
            "camunda.secrets.KNOWN and camunda.secrets.UNKNOWN",
            Map.of("camunda.secrets.KNOWN", "value"));

    assertThat(result).isEqualTo("value and camunda.secrets.UNKNOWN");
  }

  @Test
  void doesNotSubstituteAShorterReferenceInsideALongerOne() {
    // camunda.secrets.TOKEN is a prefix of camunda.secrets.TOKEN_V2. Replacing the short one first
    // would turn the long one into "<value>_V2".
    String result =
        SecretReferenceUtil.replaceReferences(
            "camunda.secrets.TOKEN_V2",
            Map.of(
                "camunda.secrets.TOKEN", "short-value", "camunda.secrets.TOKEN_V2", "long-value"));

    assertThat(result).isEqualTo("long-value");
  }

  @Test
  void doesNotRescanAResolvedValue() {
    // A secret whose value contains reference-shaped text must stay opaque: rescanning it could
    // corrupt the value, or trigger a lookup nobody asked for.
    String result =
        SecretReferenceUtil.replaceReferences(
            "camunda.secrets.A",
            Map.of("camunda.secrets.A", "camunda.secrets.B", "camunda.secrets.B", "b-value"));

    assertThat(result).isEqualTo("camunda.secrets.B");
  }

  @Test
  void doesNotEscapeTheSubstitutedValue() {
    // Substitution happens on a string leaf of the evaluation result, not on serialized JSON, so
    // the value is handed over exactly as the store holds it.
    String result =
        SecretReferenceUtil.replaceReferences(
            "camunda.secrets.A", Map.of("camunda.secrets.A", "quote\"back\\slash"));

    assertThat(result).isEqualTo("quote\"back\\slash");
  }

  @Test
  void returnsTheInputUnchangedWhenItHoldsNoReference() {
    String input = "nothing to see here";

    assertThat(SecretReferenceUtil.replaceReferences(input, Map.of("camunda.secrets.A", "v")))
        .isSameAs(input);
  }

  @Test
  void doesNotSubstituteAShorterDashedReferenceInsideALongerOne() {
    String result =
        SecretReferenceUtil.replaceReferences(
            "camunda.secrets.db-password-v2",
            Map.of(
                "camunda.secrets.db-password", "short",
                "camunda.secrets.db-password-v2", "long"));

    assertThat(result).isEqualTo("long");
  }

  @Test
  void buildsTheReferenceTheResolveEndpointExpects() {
    assertThat(SecretReferenceUtil.reference("TOKEN")).isEqualTo("camunda.secrets.TOKEN");
  }
}
