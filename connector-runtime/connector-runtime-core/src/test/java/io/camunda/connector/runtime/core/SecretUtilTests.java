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
import java.util.List;
import java.util.Map;
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
  void shouldNotAdmitABarePrefixOfABracketedNameWithSpecialCharacters() {
    // {{secrets.DECLARED_A:SUB}} is one declaration. The bare pattern's narrower character class
    // (no ':') would also match "secrets.DECLARED_A" inside that same literal text, spuriously
    // admitting the shorter "DECLARED_A" into the allow-list this feeds — letting a runtime value
    // that merely spells {{secrets.DECLARED_A}} resolve a secret the model never declared.
    assertThat(SecretUtil.retrieveSecretKeysInInput("{{secrets.DECLARED_A:SUB}}"))
        .containsExactly("DECLARED_A:SUB");
    assertThat(SecretUtil.retrieveLegacySecretKeysInInput("{{secrets.DECLARED_A:SUB}}"))
        .containsExactly("DECLARED_A:SUB");
  }

  @Test
  void shouldStillReportABareReferenceOutsideAnyBracketedOccurrence() {
    // The exclusion only applies to a bare match nested inside a bracketed one; a genuinely
    // separate bare occurrence elsewhere in the text must still be reported.
    assertThat(
            SecretUtil.retrieveSecretKeysInInput(
                "{{secrets.DECLARED_A:SUB}} and also secrets.OTHER_BARE"))
        .containsExactlyInAnyOrder("DECLARED_A:SUB", "OTHER_BARE");
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
  void shouldNotResolveABarePrefixInsideAStillDeniedBracketedReference() {
    // FOO is allowed on its own, but "FOO:BAR" is not declared anywhere and so is denied. The
    // parentheses pass correctly leaves the literal "{{secrets.FOO:BAR}}" untouched — but its own
    // text still contains "secrets.FOO", which the bare pass must not separately resolve.
    SecretReplacer secretReplacer = (name, context) -> "FOO".equals(name) ? "REAL_VALUE" : null;

    String result = SecretUtil.replaceSecrets("{{secrets.FOO:BAR}}", null, secretReplacer);

    assertThat(result).isEqualTo("{{secrets.FOO:BAR}}");
  }

  @Test
  void shouldNotResolveADeniedBracketedReferenceDuringAChainedRescan() {
    // The bare pass reruns once per match in the original text, so a resolved value that itself
    // looks like a secret reference can chain into a further replacement. A still-denied bracketed
    // reference elsewhere in the same text must stay excluded across every one of those reruns, not
    // just the first.
    SecretReplacer secretReplacer =
        (name, context) -> {
          if ("A".equals(name)) return "secrets.FOO";
          if ("FOO".equals(name)) return "REAL_VALUE";
          return null;
        };

    String result =
        SecretUtil.replaceSecrets("secrets.A and {{secrets.FOO:BAR}}", null, secretReplacer);

    assertThat(result).isEqualTo("REAL_VALUE and {{secrets.FOO:BAR}}");
  }

  @Test
  void shouldResolveAChainedBracketedReferenceIntroducedByAnEarlierResolution() {
    // A's own resolved value happens to spell "{{secrets.B}}" -- the parentheses pass never saw
    // this occurrence, since it didn't exist in the original text, so it was never "denied" by
    // anything. The bare pass's per-iteration recompute of denied brackets must not treat every
    // bracketed occurrence visible in the current text as denied just because it's still there;
    // only a bracket the parentheses pass actually attempted (present in the *original* input) is
    // denied. "secrets.PADDING" exists purely to give the bounded rescan a second iteration to
    // run in -- it's bounded by the original match count, not by whether more work remains.
    SecretReplacer secretReplacer =
        (name, context) -> {
          if ("A".equals(name)) return "{{secrets.B}}";
          if ("B".equals(name)) return "FINAL";
          if ("PADDING".equals(name)) return "PADDING";
          return null;
        };

    String result = SecretUtil.replaceSecrets("secrets.A secrets.PADDING", null, secretReplacer);

    assertThat(result).isEqualTo("{{FINAL}} PADDING");
  }

  @Test
  void shouldResolveAChainedBracketedReferenceIntroducedByTheParenthesesPassItself() {
    // The parentheses pass is itself bounded by the original match count: with a single original
    // occurrence "{{secrets.A}}", it gets exactly one iteration, which resolves A to the literal
    // "{{secrets.B}}" and then exits -- it never attempts B. That leftover bracket text must not
    // be treated as "denied" by the bare pass just because it's present at the pass boundary; the
    // bare pass must get a real chance to resolve B.
    SecretReplacer secretReplacer =
        (name, context) -> {
          if ("A".equals(name)) return "{{secrets.B}}";
          if ("B".equals(name)) return "FINAL";
          return null;
        };

    String result = SecretUtil.replaceSecrets("{{secrets.A}}", null, secretReplacer);

    assertThat(result).isEqualTo("{{FINAL}}");
  }

  @Test
  void shouldNotAdmitANameNestedInsideABracketedReferenceViaTheCamundaSecretsForm() {
    // {{secrets.camunda.secrets.FOO}} declares one name: "camunda.secrets.FOO". The
    // camunda.secrets.<name> pattern would separately match "FOO" inside that same literal text —
    // an undeclared name that must not be admitted just because it happens to appear nested inside
    // a bracketed declaration of something else.
    assertThat(SecretUtil.retrieveSecretKeysInInput("{{secrets.camunda.secrets.FOO}}"))
        .containsExactly("camunda.secrets.FOO");
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
}
