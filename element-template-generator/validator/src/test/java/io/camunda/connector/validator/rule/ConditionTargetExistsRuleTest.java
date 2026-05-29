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

class ConditionTargetExistsRuleTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final Path FILE = Path.of("test.json");
  private final ConditionTargetExistsRule rule = new ConditionTargetExistsRule();

  @Test
  void noConditions_noFindings() throws Exception {
    JsonNode template =
        read(
            """
        { "properties": [ { "id": "a" }, { "id": "b" } ] }
        """);
    assertThat(rule.apply(FILE, template)).isEmpty();
  }

  @Test
  void simpleEqualsCondition_referencesExisting_noFindings() throws Exception {
    JsonNode template =
        read(
            """
        {
          "properties": [
            { "id": "authType" },
            { "id": "token", "condition": { "property": "authType", "equals": "pat" } }
          ]
        }
        """);
    assertThat(rule.apply(FILE, template)).isEmpty();
  }

  @Test
  void simpleOneOfCondition_referencesMissing_oneFinding() throws Exception {
    JsonNode template =
        read(
            """
        {
          "properties": [
            { "id": "operationGroup" },
            { "id": "x", "condition": { "property": "opGruop", "oneOf": ["issues"] } }
          ]
        }
        """);
    List<Finding> findings = rule.apply(FILE, template);
    assertThat(findings).hasSize(1);
    Finding f = findings.get(0);
    assertThat(f.ruleId()).isEqualTo("condition-target-exists");
    assertThat(f.jsonPointer()).isEqualTo("/properties/1/condition/property");
    assertThat(f.message()).contains("opGruop");
  }

  @Test
  void allMatchCompound_eachSubconditionChecked() throws Exception {
    JsonNode template =
        read(
            """
        {
          "properties": [
            { "id": "providerType" },
            { "id": "x", "condition": { "allMatch": [
              { "property": "providerType", "equals": "bedrock" },
              { "property": "missingOne", "equals": "credentials" }
            ]}}
          ]
        }
        """);
    List<Finding> findings = rule.apply(FILE, template);
    assertThat(findings).hasSize(1);
    assertThat(findings.get(0).jsonPointer())
        .isEqualTo("/properties/1/condition/allMatch/1/property");
    assertThat(findings.get(0).message()).contains("missingOne");
  }

  @Test
  void allMatchCompound_allValid_noFindings() throws Exception {
    JsonNode template =
        read(
            """
        {
          "properties": [
            { "id": "a" }, { "id": "b" },
            { "id": "x", "condition": { "allMatch": [
              { "property": "a", "equals": 1 },
              { "property": "b", "equals": 2 }
            ]}}
          ]
        }
        """);
    assertThat(rule.apply(FILE, template)).isEmpty();
  }

  @Test
  void dottedPropertyId_matchesLiterally() throws Exception {
    JsonNode template =
        read(
            """
        {
          "properties": [
            { "id": "authentication.authType" },
            { "id": "x", "condition": { "property": "authentication.authType", "equals": "pat" } }
          ]
        }
        """);
    assertThat(rule.apply(FILE, template)).isEmpty();
  }

  @Test
  void multipleMissingReferences_independentFindings() throws Exception {
    JsonNode template =
        read(
            """
        {
          "properties": [
            { "id": "a" },
            { "id": "x", "condition": { "property": "missing1", "equals": "v" } },
            { "id": "y", "condition": { "property": "missing2", "oneOf": ["v"] } }
          ]
        }
        """);
    List<Finding> findings = rule.apply(FILE, template);
    assertThat(findings).hasSize(2);
    assertThat(findings)
        .extracting(Finding::jsonPointer)
        .containsExactlyInAnyOrder(
            "/properties/1/condition/property", "/properties/2/condition/property");
  }

  private static JsonNode read(String json) throws Exception {
    return MAPPER.readTree(json);
  }
}
