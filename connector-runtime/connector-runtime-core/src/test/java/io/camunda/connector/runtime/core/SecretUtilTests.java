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
package io.camunda.connector.runtime.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.camunda.connector.api.json.ConnectorsObjectMapperSupplier;
import io.camunda.connector.runtime.core.secret.SecretFilter.Secret;
import io.camunda.connector.runtime.core.secret.SecretUtil;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

public class SecretUtilTests {

  private static final ObjectMapper OBJECT_MAPPER = ConnectorsObjectMapperSupplier.getCopy();

  private static ObjectNode wrap(String value) {
    return OBJECT_MAPPER.createObjectNode().put("value", value);
  }

  @ParameterizedTest
  @CsvSource({
    "secrets.test,test, true",
    "secrets.TEST,TEST, true",
    "secrets.A/B,A/B, true",
    "secrets.A.B,A.B, true",
    "{secrets.TEST},TEST, true",
    "secrets.TEST0,TEST0, true",
    "secrets.TEST-0,TEST-0, true",
    "secrets.TEST_0,TEST_0, true",
    "secrets.TEST_TEST,TEST_TEST, true",
    "secrets.a_b_c_d_e_f,a_b_c_d_e_f, true",
    "secrets.a.b.c.d.e.f,a.b.c.d.e.f, true",
    "secrets.TEST TEST,TEST,true",
    "secrets._TEST,,false",
    "secrets./TEST,,false",
    "secrets.-TEST,,false",
    "secrets..TEST,,false",
    "secrets.,,false",
    "secrets..,,false",
    "secrets.?,,false"
  })
  void testSecretPattern(String input, String secret, boolean shouldDetect) {
    var asked = new ArrayList<Secret>();

    SecretUtil.replaceSecrets(
        wrap(input),
        candidate -> {
          asked.add(candidate);
          return null;
        });

    if (shouldDetect) {
      assertThat(asked).containsExactly(new Secret(secret, List.of("value")));
    } else {
      assertThat(asked).isEmpty();
    }
  }

  @ParameterizedTest
  @CsvSource(
      value = {
        "{\"field1\": \"secrets.KEY1\"}|{\"field1\": \"VALUE1\"}",
        "{\"field1\": \"secrets.KEY1\", \"field2\": \"secrets.KEY2\"}|{\"field1\": \"VALUE1\", \"field2\": \"VALUE2\"}",
        "{\"field1\": \"{{secrets.KEY1}}\"}|{\"field1\": \"VALUE1\"}",
        "{\"field1\": \"{{secrets.KEY1}}\", \"field2\": \"{{secrets.KEY2}}\"}|{\"field1\": \"VALUE1\", \"field2\": \"VALUE2\"}",
      },
      delimiter = '|')
  void testSecretReplacementWithJsonInput(String input, String output) throws Exception {
    Map<String, String> secrets =
        Map.of(
            "KEY1", "VALUE1",
            "KEY2", "VALUE2");
    var tree = (ObjectNode) OBJECT_MAPPER.readTree(input);

    var result = SecretUtil.replaceSecrets(tree, secret -> secrets.get(secret.secretName()));

    assertThat(result).isEqualTo(OBJECT_MAPPER.readTree(output));
  }

  @Test
  void shouldOnlyReplaceAllowListedSecrets() {
    Map<String, String> secrets =
        Map.of(
            "KEY1", "VALUE1",
            "KEY2", "VALUE2",
            "KEY3", "VALUE3");
    List<String> allowList = List.of("KEY1", "KEY2");
    String content = "Hello {{secrets.KEY1}} and {{secrets.KEY2}} and {{secrets.KEY3}}";

    var replaced =
        SecretUtil.replaceSecrets(
            wrap(content),
            secret ->
                allowList.contains(secret.secretName()) ? secrets.get(secret.secretName()) : null);

    assertThat(replaced.get("value").asText())
        .isEqualTo("Hello VALUE1 and VALUE2 and {{secrets.KEY3}}");
  }

  @ParameterizedTest
  @CsvSource({
    "no secrets here,",
    "secrets.FOO,FOO",
    "{{secrets.FOO}},FOO",
  })
  void shouldRetrieveSecretKeysInInput(String input, String expectedKey) {
    var keys = SecretUtil.retrieveSecretKeysInInput(input);
    if (expectedKey == null) {
      assertThat(keys).isEmpty();
    } else {
      assertThat(keys).containsExactly(expectedKey);
    }
  }

  @Test
  void shouldRetrieveMultipleDistinctSecretKeysInInput() {
    var keys =
        SecretUtil.retrieveSecretKeysInInput("{{secrets.FOO}} and secrets.BAR and {{secrets.FOO}}");

    assertThat(keys).containsExactlyInAnyOrder("FOO", "BAR");
  }

  @Test
  void shouldRetrieveSecretKeysWithTheirFieldPaths() throws Exception {
    var input =
        (ObjectNode)
            OBJECT_MAPPER.readTree(
                """
                {
                  "outer": {"inner": "secrets.NESTED"},
                  "values": [["{{secrets.ARRAY}}"]]
                }
                """);

    assertThat(SecretUtil.retrieveSecretKeysInInput(input))
        .containsExactlyInAnyOrder(
            new Secret("NESTED", List.of("outer", "inner")),
            new Secret("ARRAY", List.of("values")));
  }

  @Test
  void shouldTrimSecretKeyExtractedFromBracketedReference() {
    assertThat(SecretUtil.retrieveSecretKeysInInput("{{ secrets.FOO:BAR }}"))
        .containsExactly("FOO:BAR");
  }

  @Test
  void shouldNotResolveABarePrefixOfADeniedBracketedName() {
    var asked = new ArrayList<String>();

    assertThat(replacedValue("{{secrets.PROD:API}}", asked, Map.of("PROD", "p4ssw0rd")))
        .isEqualTo("{{secrets.PROD:API}}");
    assertThat(asked).containsExactly("PROD:API");
  }

  @Test
  void shouldNotResolveABracketedReferenceAResolvedValueIntroduces() {
    var asked = new ArrayList<String>();
    var secrets = Map.of("A", "{{secrets.PROD:API}}", "PROD", "p4ssw0rd");

    assertThat(replacedValue("{{secrets.A}}", asked, secrets)).isEqualTo("{{secrets.PROD:API}}");
    assertThat(asked).containsExactly("A");
  }

  @Test
  void shouldNotResolveABareReferenceAResolvedValueIntroduces() {
    var asked = new ArrayList<String>();
    var secrets = Map.of("NOTE", "see secrets.OTHER", "OTHER", "TOP_SECRET");

    assertThat(replacedValue("{{secrets.NOTE}}", asked, secrets)).isEqualTo("see secrets.OTHER");
    assertThat(asked).containsExactly("NOTE");
  }

  @Test
  void shouldReportOnlyTheNameABracketedReferenceDeclares() {
    assertThat(SecretUtil.retrieveSecretKeysInInput("{{secrets.PROD:API}}"))
        .containsExactly("PROD:API");
    assertThat(SecretUtil.retrieveSecretKeysInInput("{{secrets.camunda.secrets.FOO}}"))
        .containsExactly("camunda.secrets.FOO");
  }

  @Test
  void shouldAskForEveryNameAtMostOnceAtOneFieldPath() {
    var asked = new ArrayList<String>();

    assertThat(replacedValue("secrets.K secrets.K {{secrets.K}}", asked, Map.of("K", "V")))
        .isEqualTo("V V V");
    assertThat(asked).containsExactly("K");
  }

  @Test
  void shouldResolveTheSameNameSeparatelyAtDifferentFieldPaths() throws Exception {
    var input = (ObjectNode) OBJECT_MAPPER.readTree("{\"a\":\"secrets.K\",\"b\":\"secrets.K\"}");
    var asked = new ArrayList<Secret>();

    SecretUtil.replaceSecrets(
        input,
        secret -> {
          asked.add(secret);
          return "V";
        });

    assertThat(asked).containsExactly(new Secret("K", List.of("a")), new Secret("K", List.of("b")));
  }

  @Test
  void shouldAskForEveryDeniedNameAtMostOnce() {
    var asked = new ArrayList<String>();
    var input = "{{secrets.DENIED}} secrets.DENIED {{secrets.DENIED}}";

    assertThat(replacedValue(input, asked, Map.of())).isEqualTo(input);
    assertThat(asked).containsExactly("DENIED");
  }

  @Test
  void shouldScanAPayloadOfDeniedReferencesOnce() {
    var asked = new ArrayList<String>();
    var payload =
        IntStream.range(0, 5000)
            .mapToObj(i -> "{{secrets.DENIED" + i + ":X}}")
            .collect(Collectors.joining(" "));

    assertThat(replacedValue(payload, asked, Map.of())).isEqualTo(payload);
    assertThat(asked).hasSize(5000);
  }

  @Test
  void shouldNotWriteTheTextNullWhereANameIsAllWhitespace() {
    var asked = new ArrayList<String>();
    var input = "{{secrets.\0}}";

    assertThat(replacedValue(input, asked, Map.of())).isEqualTo(input);
    assertThat(asked).containsExactly("\0");
  }

  @Test
  void shouldWriteResolvedTextIntoTheTreeWithoutDoubleEscaping() throws Exception {
    var value = "pa\\nss\"quoted\"";
    var input = OBJECT_MAPPER.createObjectNode().put("token", "{{secrets.FOO}}");

    var substituted = SecretUtil.replaceSecrets(input, secret -> value);

    assertThat(substituted.get("token").textValue()).isEqualTo(value);
    assertThat(OBJECT_MAPPER.writeValueAsString(substituted))
        .isEqualTo("{\"token\":\"pa\\\\nss\\\"quoted\\\"\"}");
  }

  @Test
  void shouldReplaceAReferenceNestedInsideArraysOfArbitraryDepth() throws Exception {
    var input = (ObjectNode) OBJECT_MAPPER.readTree("{\"values\":[[\"{{secrets.TOKEN}}\"]]}");

    var result = SecretUtil.replaceSecrets(input, secret -> "tok123");

    assertThat(result).isEqualTo(OBJECT_MAPPER.readTree("{\"values\":[[\"tok123\"]]}"));
  }

  @Test
  void shouldReplaceAReferenceWrittenAsAPropertyName() throws Exception {
    var input = (ObjectNode) OBJECT_MAPPER.readTree("{\"{{secrets.KEY}}\":\"value\"}");

    var result = SecretUtil.replaceSecrets(input, secret -> "resolved-key");

    assertThat(result).isEqualTo(OBJECT_MAPPER.readTree("{\"resolved-key\":\"value\"}"));
  }

  @Test
  void shouldKeepTheLaterPropertyWhenARenamedKeyCollidesWithIt() throws Exception {
    var input =
        (ObjectNode)
            OBJECT_MAPPER.readTree(
                "{\"{{secrets.KEY}}\":\"placeholder\",\"resolved-key\":{\"nested\":\"value\"}}");

    var result = SecretUtil.replaceSecrets(input, secret -> "resolved-key");

    assertThat(result)
        .isEqualTo(OBJECT_MAPPER.readTree("{\"resolved-key\":{\"nested\":\"value\"}}"));
  }

  @Test
  void shouldRejectANonObjectRoot() throws Exception {
    assertThatThrownBy(
            () ->
                SecretUtil.replaceSecrets(
                    OBJECT_MAPPER.readTree("[\"secrets.KEY\"]"), ignored -> "V"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("input must be an ObjectNode.");
  }

  @Test
  void legacyStringReplacementRemainsAvailable() {
    assertThat(SecretUtil.replaceSecrets("{{secrets.KEY}}", name -> "VALUE")).isEqualTo("VALUE");
  }

  private static Function<Secret, String> recording(
      List<String> asked, Map<String, String> secrets) {
    return secret -> {
      asked.add(secret.secretName());
      return secrets.get(secret.secretName());
    };
  }

  private static String replacedValue(
      String input, List<String> asked, Map<String, String> secrets) {
    return SecretUtil.replaceSecrets(wrap(input), recording(asked, secrets)).get("value").asText();
  }
}
