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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.camunda.connector.runtime.core.secret.SecretUtil;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
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
  void shouldAskTheReplacerAtMostOnceForAGivenNameAcrossBothPasses() {
    // secretReplacer is not guaranteed side-effect-free in production (a provider aggregator can
    // count each resolution), so a name the parentheses pass already resolved or denied must not
    // be looked up again by the bare pass's own denial check.
    var secretReplacer = mock(Function.class);
    when(secretReplacer.apply("FOO:BAR")).thenReturn(null);

    SecretUtil.replaceSecrets("{{secrets.FOO:BAR}}", secretReplacer);

    verify(secretReplacer, times(1)).apply("FOO:BAR");
  }

  @Test
  void shouldNotResolveABarePrefixInsideAStillDeniedBracketedReference() {
    // FOO is allowed on its own, but "FOO:BAR" is not declared anywhere and so is denied. The
    // parentheses pass correctly leaves the literal "{{secrets.FOO:BAR}}" untouched — but its own
    // text still contains "secrets.FOO", which the bare pass must not separately resolve.
    Function<String, String> secretReplacer = name -> "FOO".equals(name) ? "REAL_VALUE" : null;

    String result = SecretUtil.replaceSecrets("{{secrets.FOO:BAR}}", secretReplacer);

    assertThat(result).isEqualTo("{{secrets.FOO:BAR}}");
  }

  @Test
  void shouldNotResolveADeniedBracketedReferenceDuringAChainedRescan() {
    // The bare pass reruns once per match in the original text, so a resolved value that itself
    // looks like a secret reference can chain into a further replacement. A still-denied bracketed
    // reference elsewhere in the same text must stay excluded across every one of those reruns, not
    // just the first.
    Function<String, String> secretReplacer =
        name -> {
          if ("A".equals(name)) return "secrets.FOO";
          if ("FOO".equals(name)) return "REAL_VALUE";
          return null;
        };

    String result = SecretUtil.replaceSecrets("secrets.A and {{secrets.FOO:BAR}}", secretReplacer);

    assertThat(result).isEqualTo("REAL_VALUE and {{secrets.FOO:BAR}}");
  }

  @Test
  void shouldNotResolveABarePrefixInsideAChainGeneratedDeniedBracketedReference() {
    // A's own resolved value happens to spell "{{secrets.FOO:BAR}}" -- a bracketed reference the
    // parentheses pass never attempted, since it didn't exist in the original text. "FOO:BAR" is
    // not declared anywhere and is denied, even though the bare prefix "FOO" is separately
    // allowed. The bare pass must deny this chain-generated bracket exactly as it would an
    // original-input one, not just brackets an earlier pass happened to record.
    Function<String, String> secretReplacer =
        name -> {
          if ("A".equals(name)) return "{{secrets.FOO:BAR}}";
          if ("FOO".equals(name)) return "REAL_VALUE";
          return null;
        };

    String result = SecretUtil.replaceSecrets("{{secrets.A}}", secretReplacer);

    assertThat(result).isEqualTo("{{secrets.FOO:BAR}}");
  }

  @Test
  void shouldUseTheFullNameResolutionOfAChainGeneratedBracketRatherThanItsBarePrefix() {
    // A's own resolved value happens to spell "{{secrets.FOO:BAR}}". "FOO:BAR" is itself allowed
    // (a different name from its bare prefix "FOO", which is also separately allowed but to a
    // different value). The bare pass must apply FOO:BAR's own resolution to the whole bracket,
    // not resolve the truncated bare prefix "FOO" and discard the full name's actual value.
    Function<String, String> secretReplacer =
        name -> {
          if ("A".equals(name)) return "{{secrets.FOO:BAR}}";
          if ("FOO:BAR".equals(name)) return "FULL_VALUE";
          if ("FOO".equals(name)) return "PREFIX_VALUE";
          return null;
        };

    String result = SecretUtil.replaceSecrets("{{secrets.A}}", secretReplacer);

    assertThat(result).isEqualTo("FULL_VALUE");
  }

  @Test
  void shouldFullyResolveAMultiHopChainOfTruncatingBrackets() {
    // A -> {{secrets.B:C}} -> {{secrets.D:E}} -> FULL_VALUE. A single pass only resolves one hop;
    // the full-name resolution of a chain-generated truncating bracket must itself be re-attempted
    // as a full bracketed reference, since its own resolution can generate another one.
    Function<String, String> secretReplacer =
        name -> {
          if ("A".equals(name)) return "{{secrets.B:C}}";
          if ("B:C".equals(name)) return "{{secrets.D:E}}";
          if ("D:E".equals(name)) return "FULL_VALUE";
          if ("D".equals(name)) return "PREFIX_VALUE";
          return null;
        };

    String result = SecretUtil.replaceSecrets("{{secrets.A}}", secretReplacer);

    assertThat(result).isEqualTo("FULL_VALUE");
  }

  @Test
  void shouldTerminateOnASelfReferencingTruncatingBracketInsteadOfLoopingForever() {
    // FOO:BAR resolves to a bracketed reference of itself -- resolutionsByName memoizes by name,
    // so this reproduces byte-identical text on every pass and would loop forever without an
    // explicit bound.
    Function<String, String> secretReplacer =
        name -> "FOO:BAR".equals(name) ? "{{secrets.FOO:BAR}}" : null;

    String result = SecretUtil.replaceSecrets("{{secrets.FOO:BAR}}", secretReplacer);

    assertThat(result).isEqualTo("{{secrets.FOO:BAR}}");
  }

  @Test
  @org.junit.jupiter.api.Timeout(2)
  void shouldTerminateOnAMutualCycleOfTruncatingBracketsInsteadOfLoopingForever() {
    // "A:B" resolves to "{{secrets.C:D}}" and "C:D" resolves back to "{{secrets.A:B}}" -- an
    // alternating cycle that never produces byte-identical text between passes, so only the fixed
    // iteration cap (not the convergence check) can terminate it.
    Function<String, String> secretReplacer =
        name -> {
          if ("A:B".equals(name)) return "{{secrets.C:D}}";
          if ("C:D".equals(name)) return "{{secrets.A:B}}";
          return null;
        };

    String result = SecretUtil.replaceSecrets("{{secrets.A:B}}", secretReplacer);

    assertThat(result).isNotNull();
  }

  @Test
  @org.junit.jupiter.api.Timeout(5)
  void shouldStayFastWithManyDeniedSpecialCharacterReferences() {
    // Each of these has a ':' in the name, so it's excluded from the bare pass only via the
    // truncating-bracket path -- none of them ever resolve or disappear, so the bounded rescan
    // reruns once per reference. Without a linear (not quadratic) per-pass exclusion check, this
    // is cubic in the reference count and would blow well past the timeout.
    int count = 300;
    StringBuilder input = new StringBuilder();
    for (int i = 0; i < count; i++) {
      input.append("{{secrets.NAME").append(i).append(":SUFFIX}} ");
    }
    Function<String, String> secretReplacer = name -> null;

    String result = SecretUtil.replaceSecrets(input.toString(), secretReplacer);

    assertThat(result).isEqualTo(input.toString());
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
    Function<String, String> secretReplacer =
        name -> {
          if ("A".equals(name)) return "{{secrets.B}}";
          if ("B".equals(name)) return "FINAL";
          if ("PADDING".equals(name)) return "PADDING";
          return null;
        };

    String result = SecretUtil.replaceSecrets("secrets.A secrets.PADDING", secretReplacer);

    assertThat(result).isEqualTo("{{FINAL}} PADDING");
  }

  @Test
  void shouldResolveAChainedBracketedReferenceIntroducedByTheParenthesesPassItself() {
    // The parentheses pass is itself bounded by the original match count: with a single original
    // occurrence "{{secrets.A}}", it gets exactly one iteration, which resolves A to the literal
    // "{{secrets.B}}" and then exits -- it never attempts B. That leftover bracket text must not
    // be treated as "denied" by the bare pass just because it's present at the pass boundary; the
    // bare pass must get a real chance to resolve B.
    Function<String, String> secretReplacer =
        name -> {
          if ("A".equals(name)) return "{{secrets.B}}";
          if ("B".equals(name)) return "FINAL";
          return null;
        };

    String result = SecretUtil.replaceSecrets("{{secrets.A}}", secretReplacer);

    assertThat(result).isEqualTo("{{FINAL}}");
  }

  @Test
  void shouldTrimTheExtractedNameSoItMatchesWhatReplacementLooksUp() {
    // The parentheses pattern's capture reaches past the name to the closing braces, so
    // "{{ secrets.FOO }}" declares FOO, not "FOO ". Returning the untrimmed form left the
    // allow-list containing a name resolution never looks up, denying a legitimately declared
    // secret.
    var withWhitespace = "{{ secrets.FOO }}";
    Function<String, String> secretReplacer = name -> "FOO".equals(name) ? "resolved" : null;

    assertThat(SecretUtil.retrieveSecretKeysInInput(withWhitespace)).containsExactly("FOO");
    assertThat(SecretUtil.replaceSecrets(withWhitespace, secretReplacer)).isEqualTo("resolved");
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
  void shouldNotAdmitABarePrefixOfABracketedNameWithSpecialCharacters() {
    // {{secrets.DECLARED_A:SUB}} is one declaration. The bare pattern's narrower character class
    // (no ':') would also match "secrets.DECLARED_A" inside that same literal text, spuriously
    // admitting the shorter "DECLARED_A" into the allow-list this feeds — letting a runtime value
    // that merely spells {{secrets.DECLARED_A}} resolve a secret the model never declared.
    assertThat(SecretUtil.retrieveSecretKeysInInput("{{secrets.DECLARED_A:SUB}}"))
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
}
