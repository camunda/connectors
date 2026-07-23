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

class ConditionValueInChoicesRuleTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final Path FILE = Path.of("test.json");
  private final ConditionValueInChoicesRule rule = new ConditionValueInChoicesRule();

  @Test
  void equalsMatchesChoice_noFindings() throws Exception {
    JsonNode template =
        read(
            """
        {
          "properties": [
            { "id": "authType", "choices": [{"value":"pat"},{"value":"oauth"}] },
            { "id": "x", "condition": { "property": "authType", "equals": "pat" } }
          ]
        }
        """);
    assertThat(rule.apply(FILE, template)).isEmpty();
  }

  @Test
  void equalsNotInChoices_oneFinding() throws Exception {
    JsonNode template =
        read(
            """
        {
          "properties": [
            { "id": "authType", "choices": [{"value":"pat"},{"value":"oauth"}] },
            { "id": "x", "condition": { "property": "authType", "equals": "patt" } }
          ]
        }
        """);
    List<Finding> findings = rule.apply(FILE, template);
    assertThat(findings).hasSize(1);
    assertThat(findings.get(0).jsonPointer()).isEqualTo("/properties/1/condition/equals");
    assertThat(findings.get(0).message()).contains("patt");
  }

  @Test
  void oneOfWithSomeInvalid_findsOnlyInvalid() throws Exception {
    JsonNode template =
        read(
            """
        {
          "properties": [
            { "id": "op", "choices": [{"value":"a"},{"value":"b"}] },
            { "id": "x", "condition": { "property": "op", "oneOf": ["a", "c", "b", "d"] } }
          ]
        }
        """);
    List<Finding> findings = rule.apply(FILE, template);
    assertThat(findings).hasSize(2);
    assertThat(findings)
        .extracting(Finding::jsonPointer)
        .containsExactlyInAnyOrder(
            "/properties/1/condition/oneOf/1", "/properties/1/condition/oneOf/3");
  }

  @Test
  void allMatchSubconditionsChecked() throws Exception {
    JsonNode template =
        read(
            """
        {
          "properties": [
            { "id": "p", "choices": [{"value":"a"}] },
            { "id": "q", "choices": [{"value":"x"}] },
            { "id": "r", "condition": { "allMatch": [
              { "property": "p", "equals": "a" },
              { "property": "q", "equals": "y" }
            ]}}
          ]
        }
        """);
    List<Finding> findings = rule.apply(FILE, template);
    assertThat(findings).hasSize(1);
    assertThat(findings.get(0).jsonPointer())
        .isEqualTo("/properties/2/condition/allMatch/1/equals");
  }

  @Test
  void targetPropertyHasNoChoices_noEnforcement() throws Exception {
    JsonNode template =
        read(
            """
        {
          "properties": [
            { "id": "freeText" },
            { "id": "x", "condition": { "property": "freeText", "equals": "anything" } }
          ]
        }
        """);
    assertThat(rule.apply(FILE, template)).isEmpty();
  }

  @Test
  void choicesUnionedAcrossDuplicateIds() throws Exception {
    JsonNode template =
        read(
            """
        {
          "properties": [
            { "id": "op", "choices": [{"value":"a"}], "condition": { "property": "g", "equals": "x" } },
            { "id": "op", "choices": [{"value":"b"}], "condition": { "property": "g", "equals": "y" } },
            { "id": "z", "condition": { "property": "op", "equals": "b" } }
          ]
        }
        """);
    assertThat(rule.apply(FILE, template)).extracting(Finding::ruleId).doesNotContain(rule.id());
  }

  /**
   * A configuration template's {@code properties[]} is its own scope for choice validation too,
   * independent of the host's — even when a property of the same id exists on the host with a
   * different set of choices.
   */
  @Test
  void configurationTemplateCondition_checkedAgainstOwnChoices_notHosts() throws Exception {
    JsonNode template =
        read(
            """
        {
          "properties": [
            { "id": "authType", "choices": [{"value":"pat"},{"value":"oauth"}] }
          ],
          "configurationTemplates": [
            {
              "properties": [
                { "id": "authType", "choices": [{"value":"basic"},{"value":"bearer"}] },
                { "id": "x", "condition": { "property": "authType", "equals": "basic" } }
              ]
            }
          ]
        }
        """);
    assertThat(rule.apply(FILE, template)).isEmpty();
  }

  @Test
  void configurationTemplateCondition_hostChoiceDoesNotLeakIn_oneFinding() throws Exception {
    JsonNode template =
        read(
            """
        {
          "properties": [
            { "id": "authType", "choices": [{"value":"pat"},{"value":"oauth"}] }
          ],
          "configurationTemplates": [
            {
              "properties": [
                { "id": "authType", "choices": [{"value":"basic"},{"value":"bearer"}] },
                { "id": "x", "condition": { "property": "authType", "equals": "pat" } }
              ]
            }
          ]
        }
        """);
    List<Finding> findings = rule.apply(FILE, template);
    assertThat(findings).hasSize(1);
    assertThat(findings.get(0).jsonPointer())
        .isEqualTo("/configurationTemplates/0/properties/1/condition/equals");
  }

  private static JsonNode read(String json) throws Exception {
    return MAPPER.readTree(json);
  }
}
