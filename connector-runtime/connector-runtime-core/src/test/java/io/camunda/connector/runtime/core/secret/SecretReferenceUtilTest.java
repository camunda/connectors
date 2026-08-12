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
