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
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import io.camunda.connector.api.secret.SecretContext;
import io.camunda.connector.runtime.core.secret.SecretReplacer;
import io.camunda.connector.runtime.core.secret.SecretUtil;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
    var secretReplacer = mock(SecretReplacer.class);
    SecretUtil.replaceSecrets(input, null, secretReplacer);
    if (shouldDetect) {
      verify(secretReplacer).replaceSecrets(eq(secret), any());
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
    SecretReplacer secretReplacer = (name, context) -> secrets.get(name);
    var result = SecretUtil.replaceSecrets(input, null, secretReplacer);
    assertThat(result).isEqualTo(output);
  }

  @Test
  void shouldNotReplaceInsideACamundaSecretsReference() {
    var secretReplacer = mock(SecretReplacer.class);

    String result = SecretUtil.replaceSecrets("=camunda.secrets.FOO", null, secretReplacer);

    assertThat(result).isEqualTo("=camunda.secrets.FOO");
    verifyNoInteractions(secretReplacer);
  }

  @Test
  void shouldStillReplaceALegacyReferenceAlongsideACamundaSecretsReference() {
    SecretReplacer secretReplacer = (name, context) -> "FOO".equals(name) ? "resolved" : null;

    String result =
        SecretUtil.replaceSecrets("camunda.secrets.FOO and {{secrets.FOO}}", null, secretReplacer);

    assertThat(result).isEqualTo("camunda.secrets.FOO and resolved");
  }

  @Test
  void shouldStillReplaceASecretsPrefixedWordThatIsNotTheCamundaPrefix() {
    SecretReplacer secretReplacer = (name, context) -> "FOO".equals(name) ? "resolved" : null;

    assertThat(SecretUtil.replaceSecrets("other.secrets.FOO", null, secretReplacer))
        .isEqualTo("other.resolved");
  }

  @Test
  void shouldReportSecretKeysDeclaredInEitherForm() {
    assertThat(
            SecretUtil.retrieveSecretKeysInInput(
                "{{secrets.BRACED}} secrets.BARE camunda.secrets.REFERENCE"))
        .containsExactlyInAnyOrder("BRACED", "BARE", "REFERENCE");
  }

  @Test
  void shouldReportTheSameNameReplacementLooksUp() {
    // The parentheses capture reaches past the name to the closing braces, and replaceSecrets trims
    // before looking a name up. Reporting the untrimmed form left every caller comparing against a
    // name nothing resolves — error masking asked for "FOO " and masked nothing.
    var withWhitespace = "{{ secrets.FOO }}";
    var replaced =
        SecretUtil.replaceSecrets(
            withWhitespace, null, (name, context) -> "FOO".equals(name) ? "resolved" : null);

    assertThat(replaced).isEqualTo("resolved");
    assertThat(SecretUtil.retrieveSecretKeysInInput(withWhitespace)).containsExactly("FOO");
    assertThat(SecretUtil.retrieveLegacySecretKeysInInput(withWhitespace)).containsExactly("FOO");
  }

  @Test
  void shouldReportOnlyLegacyKeysWhenAskedForWhatTheLegacyProvidersHold() {
    // The new form is resolved by the cluster, never by a legacy provider, so a caller asking what
    // the legacy providers were responsible for must not be handed a name they never held.
    assertThat(
            SecretUtil.retrieveLegacySecretKeysInInput(
                "{{secrets.BRACED}} secrets.BARE camunda.secrets.REFERENCE"))
        .containsExactlyInAnyOrder("BRACED", "BARE");
  }

  @Test
  void shouldOnlyReplaceAllowListedSecrets() {
    List<String> allowList = List.of("KEY1", "KEY2");
    SecretReplacer secretReplacer =
        (name, context) -> allowList.contains(name) ? secrets.get(name) : null;
    String content = "Hello {{secrets.KEY1}} and {{secrets.KEY2}} and {{secrets.KEY3}}";
    SecretContext secretContext = new SecretContext("tenantId", "processId");
    String replacedContent = SecretUtil.replaceSecrets(content, secretContext, secretReplacer);
    assertThat(replacedContent).isEqualTo("Hello VALUE1 and VALUE2 and {{secrets.KEY3}}");
  }

  @Test
  void shouldNotResolveABarePrefixOfADeniedBracketedName() {
    var asked = new ArrayList<String>();

    assertThat(
            SecretUtil.replaceSecrets(
                "{{secrets.PROD:API}}", null, recording(asked, Map.of("PROD", "p4ssw0rd"))))
        .isEqualTo("{{secrets.PROD:API}}");
    assertThat(asked).containsExactly("PROD:API");
  }

  @Test
  void shouldNotResolveABracketedReferenceAResolvedValueIntroduces() {
    var asked = new ArrayList<String>();
    var secrets = Map.of("A", "{{secrets.PROD:API}}", "PROD", "p4ssw0rd");

    assertThat(SecretUtil.replaceSecrets("{{secrets.A}}", null, recording(asked, secrets)))
        .isEqualTo("{{secrets.PROD:API}}");
    assertThat(asked).containsExactly("A");
  }

  @Test
  void shouldNotResolveABareReferenceAResolvedValueIntroduces() {
    var asked = new ArrayList<String>();
    var secrets = Map.of("NOTE", "see secrets.OTHER", "OTHER", "TOP_SECRET");

    assertThat(SecretUtil.replaceSecrets("{{secrets.NOTE}}", null, recording(asked, secrets)))
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
                "secrets.K secrets.K {{secrets.K}}", null, recording(asked, Map.of("K", "V"))))
        .isEqualTo("V V V");
    assertThat(asked).containsExactly("K");
  }

  @Test
  void shouldAskForEveryDeniedNameAtMostOnce() {
    var asked = new ArrayList<String>();
    var input = "{{secrets.DENIED}} secrets.DENIED {{secrets.DENIED}}";

    assertThat(SecretUtil.replaceSecrets(input, null, recording(asked, Map.of()))).isEqualTo(input);
    assertThat(asked).containsExactly("DENIED");
  }

  @Test
  void shouldScanAPayloadOfDeniedReferencesOnce() {
    var asked = new ArrayList<String>();
    var payload =
        IntStream.range(0, 5000)
            .mapToObj(i -> "{{secrets.DENIED" + i + ":X}}")
            .collect(Collectors.joining(" "));

    assertThat(SecretUtil.replaceSecrets(payload, null, recording(asked, Map.of())))
        .isEqualTo(payload);
    assertThat(asked).hasSize(5000);
  }

  @Test
  void shouldNotWriteTheTextNullWhereANameIsAllWhitespace() {
    // The NUL is written as an escape rather than as a raw byte: a raw NUL makes git treat the
    // whole file as binary, which hides every later change to it from review.
    var asked = new ArrayList<String>();
    var input = "{\"pw\":\"{{secrets.\0}}\"}";

    assertThat(SecretUtil.replaceSecrets(input, null, recording(asked, Map.of()))).isEqualTo(input);
    assertThat(asked).containsExactly("\0");
  }

  private static SecretReplacer recording(List<String> asked, Map<String, String> secrets) {
    return (name, context) -> {
      asked.add(name);
      return secrets.get(name);
    };
  }
}
