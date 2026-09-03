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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

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
  void testSecretPattern(String input, String secret, Boolean shouldDetect) {
    var secretReplacer = mock(Function.class);
    SecretUtil.replaceSecrets(input, secretReplacer);
    if (shouldDetect) {
      verify(secretReplacer).apply(secret);
    } else {
      verifyNoInteractions(secretReplacer);
    }
  }

  Map<String, String> secrets =
      Map.of(
          "KEY1", "VALUE1",
          "KEY2", "VALUE2",
          "KEY3", "VALUE3");

  @ParameterizedTest
  @CsvSource(
      value = {
        "{\"field1\": \"secrets.KEY1\"}|{\"field1\": \"VALUE1\"}",
        "{\"field1\": \"secrets.KEY1\", \"field2\": \"secrets.KEY2\"}|{\"field1\": \"VALUE1\", \"field2\": \"VALUE2\"}",
        "{\"field1\": \"{{secrets.KEY1}}\"}|{\"field1\": \"VALUE1\"}",
        "{\"field1\": \"{{secrets.KEY1}}\", \"field2\": \"{{secrets.KEY2}}\"}|{\"field1\": \"VALUE1\", \"field2\": \"VALUE2\"}",
      },
      delimiter = '|') // delimiter is needed to escape the comma in the json
  void testSecretReplacementWithJsonInput(String input, String output) {
    Function<String, String> secretReplacer = (name) -> secrets.get(name);
    var result = SecretUtil.replaceSecrets(input, secretReplacer);
    assertThat(result).isEqualTo(output);
  }

  @Test
  void shouldOnlyReplaceAllowListedSecrets() {
    List<String> allowList = List.of("KEY1", "KEY2");
    Function<String, String> secretReplacer =
        name -> allowList.contains(name) ? secrets.get(name) : null;
    String content = "Hello {{secrets.KEY1}} and {{secrets.KEY2}} and {{secrets.KEY3}}";
    String replacedContent = SecretUtil.replaceSecrets(content, secretReplacer);
    assertThat(replacedContent).isEqualTo("Hello VALUE1 and VALUE2 and {{secrets.KEY3}}");
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
  void shouldTrimSecretKeyExtractedFromBracketedReference() {
    var keys = SecretUtil.retrieveSecretKeysInInput("{{ secrets.FOO:BAR }}");
    assertThat(keys).contains("FOO:BAR");
  }

  @Test
  void shouldNotResolveABarePrefixOfADeniedBracketedName() {
    var asked = new ArrayList<String>();

    assertThat(
            SecretUtil.replaceSecrets(
                "{{secrets.PROD:API}}", recording(asked, Map.of("PROD", "p4ssw0rd"))))
        .isEqualTo("{{secrets.PROD:API}}");
    assertThat(asked).containsExactly("PROD:API");
  }

  @Test
  void shouldNotResolveABracketedReferenceAResolvedValueIntroduces() {
    var asked = new ArrayList<String>();
    var secrets = Map.of("A", "{{secrets.PROD:API}}", "PROD", "p4ssw0rd");

    assertThat(SecretUtil.replaceSecrets("{{secrets.A}}", recording(asked, secrets)))
        .isEqualTo("{{secrets.PROD:API}}");
    assertThat(asked).containsExactly("A");
  }

  @Test
  void shouldNotResolveABareReferenceAResolvedValueIntroduces() {
    var asked = new ArrayList<String>();
    var secrets = Map.of("NOTE", "see secrets.OTHER", "OTHER", "TOP_SECRET");

    assertThat(SecretUtil.replaceSecrets("{{secrets.NOTE}}", recording(asked, secrets)))
        .isEqualTo("see secrets.OTHER");
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
  void shouldAskForEveryNameAtMostOnce() {
    var asked = new ArrayList<String>();

    assertThat(
            SecretUtil.replaceSecrets(
                "secrets.K secrets.K {{secrets.K}}", recording(asked, Map.of("K", "V"))))
        .isEqualTo("V V V");
    assertThat(asked).containsExactly("K");
  }

  @Test
  void shouldAskForEveryDeniedNameAtMostOnce() {
    var asked = new ArrayList<String>();
    var input = "{{secrets.DENIED}} secrets.DENIED {{secrets.DENIED}}";

    assertThat(SecretUtil.replaceSecrets(input, recording(asked, Map.of()))).isEqualTo(input);
    assertThat(asked).containsExactly("DENIED");
  }

  @Test
  void shouldScanAPayloadOfDeniedReferencesOnce() {
    var asked = new ArrayList<String>();
    var payload =
        IntStream.range(0, 5000)
            .mapToObj(i -> "{{secrets.DENIED" + i + ":X}}")
            .collect(Collectors.joining(" "));

    assertThat(SecretUtil.replaceSecrets(payload, recording(asked, Map.of()))).isEqualTo(payload);
    assertThat(asked).hasSize(5000);
  }

  @Test
  void shouldNotWriteTheTextNullWhereANameIsAllWhitespace() {
    // The NUL is written as an escape rather than as a raw byte: a raw NUL makes git treat the
    // whole file as binary, which hides every later change to it from review.
    var asked = new ArrayList<String>();
    var input = "{\"pw\":\"{{secrets.\0}}\"}";

    assertThat(SecretUtil.replaceSecrets(input, recording(asked, Map.of()))).isEqualTo(input);
    assertThat(asked).containsExactly("\0");
  }

  private static Function<String, String> recording(
      List<String> asked, Map<String, String> secrets) {
    return name -> {
      asked.add(name);
      return secrets.get(name);
    };
  }
}
