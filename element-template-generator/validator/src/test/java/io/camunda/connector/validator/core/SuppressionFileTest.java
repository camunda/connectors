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

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class SuppressionFileTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final Path FILE = Path.of(SuppressionFile.FILE_NAME);

  @Test
  void validEntry_parsed() throws Exception {
    var result =
        parse(
            """
        { "suppressions": [
          { "rule": "empty-group", "template": "*.json", "property": "p", "reason": "Kept for X." },
          { "rule": "empty-group", "template": "hybrid/*.json", "reason": "Kept for X." }
        ] }
        """);
    assertThat(result.findings()).isEmpty();
    assertThat(result.suppressions())
        .extracting(Suppression::pointer, Suppression::property)
        .containsExactly(
            org.assertj.core.groups.Tuple.tuple("/suppressions/0", "p"),
            org.assertj.core.groups.Tuple.tuple("/suppressions/1", null));
  }

  @Test
  void wrongRootShape_rejected() throws Exception {
    assertThat(parse("{ \"suppressions\": {} }").findings())
        .extracting(Finding::jsonPointer)
        .containsExactly("");
    assertThat(parse("{ \"suppressions\": [], \"extra\": 1 }").findings()).hasSize(1);
  }

  @Test
  void missingBlankAndUnknownFields_rejected() throws Exception {
    var result =
        parse(
            """
        { "suppressions": [
          { "template": "*.json", "reason": " ", "property": 1, "ruleId": "x" }
        ] }
        """);
    assertThat(result.suppressions()).isEmpty();
    assertThat(result.findings())
        .extracting(Finding::jsonPointer)
        .containsExactlyInAnyOrder(
            "/suppressions/0/ruleId",
            "/suppressions/0",
            "/suppressions/0/property",
            "/suppressions/0/reason");
    assertThat(result.findings()).allMatch(f -> f.ruleId().equals(SuppressionFile.RULE_ID));
  }

  @Test
  void invalidGlob_rejected() throws Exception {
    var result =
        parse(
            """
        { "suppressions": [ { "rule": "r", "template": "[a", "reason": "R." } ] }
        """);
    assertThat(result.findings())
        .extracting(Finding::jsonPointer)
        .containsExactly("/suppressions/0/template");
  }

  @Test
  void duplicate_rejected() throws Exception {
    var result =
        parse(
            """
        { "suppressions": [
          { "rule": "r", "template": "a.json", "reason": "One." },
          { "rule": "r", "template": "a.json", "reason": "Two." }
        ] }
        """);
    assertThat(result.suppressions()).hasSize(1);
    assertThat(result.findings())
        .extracting(Finding::jsonPointer)
        .containsExactly("/suppressions/1");
  }

  private static SuppressionFile.Result parse(String json) throws Exception {
    return SuppressionFile.parse(FILE, MAPPER.readTree(json));
  }
}
