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
package io.camunda.connector.validator.rule;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.validator.core.Finding;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class ConditionPropertyOrderRuleTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final Path FILE = Path.of("template.json");
  private final ConditionPropertyOrderRule rule = new ConditionPropertyOrderRule();

  @Test
  void inOrder_noFindings() throws Exception {
    JsonNode t =
        read(
            "{ \"properties\": ["
                + "{ \"id\": \"op\" },"
                + "{ \"id\": \"sub\", \"condition\": { \"property\": \"op\", \"equals\": \"v\" } } ] }");
    assertThat(rule.apply(FILE, t)).isEmpty();
  }

  @Test
  void outOfOrder_oneFinding() throws Exception {
    JsonNode t =
        read(
            "{ \"properties\": ["
                + "{ \"id\": \"sub\", \"condition\": { \"property\": \"op\", \"equals\": \"v\" } },"
                + "{ \"id\": \"op\" } ] }");
    List<Finding> findings = rule.apply(FILE, t);
    assertThat(findings).hasSize(1);
    assertThat(findings.get(0).jsonPointer()).isEqualTo("/properties/0/condition/property");
    assertThat(findings.get(0).message()).contains("\"op\"").contains("after");
  }

  @Test
  void selfReference_oneFinding() throws Exception {
    JsonNode t =
        read(
            "{ \"properties\": ["
                + "{ \"id\": \"x\", \"condition\": { \"property\": \"x\", \"equals\": \"v\" } } ] }");
    List<Finding> findings = rule.apply(FILE, t);
    assertThat(findings).hasSize(1);
    assertThat(findings.get(0).message()).contains("the property itself");
  }

  @Test
  void allMatchOneSubViolating_oneFindingForThatSub() throws Exception {
    JsonNode t =
        read(
            "{ \"properties\": ["
                + "{ \"id\": \"a\" },"
                + "{ \"id\": \"b\", \"condition\": { \"allMatch\": ["
                + "  { \"property\": \"a\", \"equals\": \"x\" },"
                + "  { \"property\": \"c\", \"equals\": \"y\" } ] } },"
                + "{ \"id\": \"c\" } ] }");
    List<Finding> findings = rule.apply(FILE, t);
    assertThat(findings).hasSize(1);
    assertThat(findings.get(0).jsonPointer())
        .isEqualTo("/properties/1/condition/allMatch/1/property");
    assertThat(findings.get(0).message()).contains("\"c\"");
  }

  @Test
  void choiceConditionForwardReference_oneFinding() throws Exception {
    JsonNode t =
        read(
            "{ \"properties\": ["
                + "{ \"id\": \"sub\", \"choices\": ["
                + "  { \"value\": \"x\", \"condition\": { \"property\": \"op\", \"equals\": \"v\" } } ] },"
                + "{ \"id\": \"op\" } ] }");
    List<Finding> findings = rule.apply(FILE, t);
    assertThat(findings).hasSize(1);
    assertThat(findings.get(0).jsonPointer())
        .isEqualTo("/properties/0/choices/0/condition/property");
  }

  @Test
  void nonExistentReference_skipped() throws Exception {
    JsonNode t =
        read(
            "{ \"properties\": ["
                + "{ \"id\": \"a\", \"condition\": { \"property\": \"missing\", \"equals\": \"v\" } } ] }");
    assertThat(rule.apply(FILE, t)).isEmpty();
  }

  @Test
  void duplicateIdSwitchingPattern_firstOccurrenceUsed() throws Exception {
    // First "op" is at index 0, "dependent" at index 1, second "op" at index 2.
    // Dependent's condition referencing "op" is OK because firstIndex(op)=0 < 1.
    JsonNode t =
        read(
            "{ \"properties\": ["
                + "{ \"id\": \"op\", \"condition\": { \"property\": \"group\", \"equals\": \"a\" } },"
                + "{ \"id\": \"dependent\", \"condition\": { \"property\": \"op\", \"equals\": \"x\" } },"
                + "{ \"id\": \"op\", \"condition\": { \"property\": \"group\", \"equals\": \"b\" } } ] }");
    // The two "op" properties are mutually exclusive (group=a vs group=b) so the rule's
    // unique-property-id sibling tolerates them; this rule also accepts the dependent ordering.
    // But "group" is referenced before it appears (or doesn't exist) — for this test we don't
    // include "group" as a property, so condition-target-exists handles that case; the order
    // rule must not fire here.
    assertThat(rule.apply(FILE, t)).isEmpty();
  }

  @Test
  void missingPropertiesArray_noFindings() throws Exception {
    assertThat(rule.apply(FILE, read("{}"))).isEmpty();
    assertThat(rule.apply(FILE, read("{ \"properties\": null }"))).isEmpty();
  }

  private static JsonNode read(String json) throws Exception {
    return MAPPER.readTree(json);
  }
}
