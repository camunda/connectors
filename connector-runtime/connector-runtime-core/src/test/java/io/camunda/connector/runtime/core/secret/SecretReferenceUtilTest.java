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

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.api.error.ConnectorInputException;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SecretReferenceUtilTest {

  @Test
  void findReferences_returnsWholeDistinctReferences() {
    var input = "a=camunda.secrets.FOO;b=camunda.secrets.FOO;c=camunda.secrets.BAR";

    assertThat(SecretReferenceUtil.findReferences(input))
        .containsExactly("camunda.secrets.FOO", "camunda.secrets.BAR");
  }

  @Test
  void findReferences_charsetStopsAtDotDashSlash() {
    // [\p{Alnum}_]+ only - no dot, slash or dash - so the reference stops at the first
    // disallowed character instead of swallowing it like the legacy pattern would.
    assertThat(SecretReferenceUtil.findReferences("camunda.secrets.a-b"))
        .containsExactly("camunda.secrets.a");
    assertThat(SecretReferenceUtil.findReferences("camunda.secrets.a.b"))
        .containsExactly("camunda.secrets.a");
    assertThat(SecretReferenceUtil.findReferences("camunda.secrets.a_b"))
        .containsExactly("camunda.secrets.a_b");
  }

  @Test
  void wholeValueReference_acceptsAValueThatIsExactlyAReferenceExpression() {
    assertThat(SecretReferenceUtil.wholeValueReference("=camunda.secrets.FOO"))
        .contains("camunda.secrets.FOO");
    // Whitespace a FEEL evaluator would trim must not change the answer.
    assertThat(SecretReferenceUtil.wholeValueReference("  =  camunda.secrets.FOO  "))
        .contains("camunda.secrets.FOO");
  }

  @Test
  void wholeValueReference_rejectsEverythingElse() {
    // No leading '=' - the unpoliced bare form ADR-0007 §11 drops.
    assertThat(SecretReferenceUtil.wholeValueReference("camunda.secrets.FOO")).isEmpty();
    // Embedded in a longer value.
    assertThat(SecretReferenceUtil.wholeValueReference("=x?key=camunda.secrets.FOO")).isEmpty();
    // Mixed into a larger expression (ADR-0007 §13).
    assertThat(SecretReferenceUtil.wholeValueReference("=\"Bearer \" + camunda.secrets.FOO"))
        .isEmpty();
    // A trailing path access is a longer qualified name, not a reference.
    assertThat(SecretReferenceUtil.wholeValueReference("=camunda.secrets.FOO.length")).isEmpty();
    // Quoted, which the engine rejects at deployment for an input mapping.
    assertThat(SecretReferenceUtil.wholeValueReference("=\"camunda.secrets.FOO\"")).isEmpty();
    assertThat(SecretReferenceUtil.wholeValueReference("")).isEmpty();
    assertThat(SecretReferenceUtil.wholeValueReference(null)).isEmpty();
  }

  @Test
  void bareName_stripsPrefix() {
    assertThat(SecretReferenceUtil.bareName("camunda.secrets.FOO")).isEqualTo("FOO");
  }

  @Test
  void bareName_rejectsAnythingThatIsNotAWholeReference() {
    assertThatThrownBy(() -> SecretReferenceUtil.bareName("FOO"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void replaceReferences_resolvedBecomesValue_refusedIsLeftVerbatim() {
    var input = "a=camunda.secrets.RESOLVED;b=camunda.secrets.REFUSED";
    Map<String, String> resolved = Map.of("camunda.secrets.RESOLVED", "value");
    Set<String> refused = Set.of("camunda.secrets.REFUSED");

    var result = SecretReferenceUtil.replaceReferences(input, resolved, refused);

    assertThat(result).isEqualTo("a=value;b=camunda.secrets.REFUSED");
  }

  @Test
  void replaceReferences_neitherResolvedNorRefused_throws() {
    assertThatThrownBy(
            () ->
                SecretReferenceUtil.replaceReferences(
                    "camunda.secrets.MISSING", Map.of(), Set.of()))
        .isInstanceOf(ConnectorInputException.class)
        .hasMessage("Secret with name 'MISSING' is not available");
  }

  @Test
  void replaceReferences_doesNotRescanASubstitutedValue() {
    // A resolved value that happens to contain text matching the reference pattern must stay
    // opaque - it must never be re-scanned as if it were another reference in the input.
    String input = "X=camunda.secrets.A;Y=camunda.secrets.ALSO_PRESENT";
    Map<String, String> resolved =
        Map.of(
            "camunda.secrets.A", "embedded-camunda.secrets.GHOST-text",
            "camunda.secrets.ALSO_PRESENT", "fine");

    String result = SecretReferenceUtil.replaceReferences(input, resolved, Set.of());

    assertThat(result).isEqualTo("X=embedded-camunda.secrets.GHOST-text;Y=fine");
  }

  @Test
  void replaceReferences_valueIsJsonEscaped() throws Exception {
    // quote, backslash, carriage return and an attempted field-injection payload.
    String maliciousValue = "\"quote\\backslash\rcr, \"injected\": \"pwned";
    Map<String, String> resolved = Map.of("camunda.secrets.FOO", maliciousValue);
    String input = "{\"value\": \"camunda.secrets.FOO\"}";

    String output = SecretReferenceUtil.replaceReferences(input, resolved, Set.of());

    var node = new ObjectMapper().readTree(output);
    assertThat(node.size()).isEqualTo(1);
    assertThat(node.get("value").asText()).isEqualTo(maliciousValue);
  }
}
