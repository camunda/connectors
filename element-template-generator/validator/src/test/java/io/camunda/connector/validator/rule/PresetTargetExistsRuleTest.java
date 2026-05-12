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

class PresetTargetExistsRuleTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final Path FILE = Path.of("test.json");
  private final PresetTargetExistsRule rule = new PresetTargetExistsRule();

  @Test
  void noPresets_noFindings() throws Exception {
    JsonNode template =
        read(
            """
        {
          "properties": [
            { "id": "operationGroup", "choices": [{"value": "issues"}] }
          ]
        }
        """);
    assertThat(rule.apply(FILE, template)).isEmpty();
  }

  @Test
  void presetReferencesExistingPropertyAndValidChoice_noFindings() throws Exception {
    JsonNode template =
        read(
            """
        {
          "properties": [
            { "id": "operationGroup", "choices": [
              { "value": "actions" }, { "value": "issues" }
            ]}
          ],
          "steps": [
            {
              "name": "Actions",
              "steps": [
                { "name": "Create dispatch", "presets": { "operationGroup": "actions" } }
              ]
            }
          ]
        }
        """);
    assertThat(rule.apply(FILE, template)).isEmpty();
  }

  @Test
  void presetReferencesMissingProperty_oneFinding() throws Exception {
    JsonNode template =
        read(
            """
        {
          "properties": [
            { "id": "operationGroup", "choices": [{"value": "actions"}] }
          ],
          "steps": [
            { "steps": [ { "presets": { "operationGruop": "actions" } } ] }
          ]
        }
        """);
    List<Finding> findings = rule.apply(FILE, template);
    assertThat(findings).hasSize(1);
    Finding f = findings.get(0);
    assertThat(f.ruleId()).isEqualTo("preset-target-exists");
    assertThat(f.jsonPointer()).isEqualTo("/steps/0/steps/0/presets/operationGruop");
    assertThat(f.message()).contains("operationGruop");
  }

  @Test
  void presetValueNotInChoices_oneFinding() throws Exception {
    JsonNode template =
        read(
            """
        {
          "properties": [
            { "id": "operationGroup", "choices": [
              { "value": "actions" }, { "value": "issues" }
            ]}
          ],
          "steps": [
            { "steps": [ { "presets": { "operationGroup": "deployments" } } ] }
          ]
        }
        """);
    List<Finding> findings = rule.apply(FILE, template);
    assertThat(findings).hasSize(1);
    Finding f = findings.get(0);
    assertThat(f.jsonPointer()).isEqualTo("/steps/0/steps/0/presets/operationGroup");
    assertThat(f.message()).contains("deployments").contains("operationGroup");
  }

  @Test
  void multipleEntriesInOnePresetsObject_independentFindings() throws Exception {
    JsonNode template =
        read(
            """
        {
          "properties": [
            { "id": "operationGroup", "choices": [{"value": "actions"}] },
            { "id": "eventOperationType" }
          ],
          "steps": [
            { "steps": [ { "presets": {
              "operationGroup": "deployments",
              "eventOperationType": "createWorkflowDispatchEvent",
              "missing": "x"
            }}]}
          ]
        }
        """);
    List<Finding> findings = rule.apply(FILE, template);
    assertThat(findings).hasSize(2);
    assertThat(findings)
        .extracting(Finding::jsonPointer)
        .containsExactlyInAnyOrder(
            "/steps/0/steps/0/presets/operationGroup", "/steps/0/steps/0/presets/missing");
  }

  @Test
  void propertyWithoutChoices_anyValueAccepted() throws Exception {
    JsonNode template =
        read(
            """
        {
          "properties": [
            { "id": "freeText" }
          ],
          "steps": [
            { "steps": [ { "presets": { "freeText": "anything goes" } } ] }
          ]
        }
        """);
    assertThat(rule.apply(FILE, template)).isEmpty();
  }

  @Test
  void duplicateIdsWithDifferentChoices_unionAccepted() throws Exception {
    // Mutually-exclusive switching pattern: same id "eventOperationType" appears twice with
    // disjoint choices, gated on different operationGroup values. A preset value valid under
    // either variant must not be flagged.
    JsonNode template =
        read(
            """
        {
          "properties": [
            { "id": "operationGroup", "choices": [
              { "value": "actions" }, { "value": "issues" }
            ]},
            { "id": "eventOperationType",
              "choices": [{ "value": "createWorkflowDispatchEvent" }],
              "condition": { "property": "operationGroup", "equals": "actions" } },
            { "id": "eventOperationType",
              "choices": [{ "value": "createIssue" }, { "value": "closeIssue" }],
              "condition": { "property": "operationGroup", "equals": "issues" } }
          ],
          "steps": [
            { "steps": [ { "presets": {
              "operationGroup": "issues",
              "eventOperationType": "createIssue"
            }}]}
          ]
        }
        """);
    assertThat(rule.apply(FILE, template)).isEmpty();
  }

  @Test
  void duplicateIdsWithDifferentChoices_valueOutsideUnion_flagged() throws Exception {
    JsonNode template =
        read(
            """
        {
          "properties": [
            { "id": "eventOperationType", "choices": [{ "value": "a" }] },
            { "id": "eventOperationType", "choices": [{ "value": "b" }] }
          ],
          "steps": [
            { "steps": [ { "presets": { "eventOperationType": "c" } } ] }
          ]
        }
        """);
    List<Finding> findings = rule.apply(FILE, template);
    assertThat(findings).hasSize(1);
    assertThat(findings.get(0).message()).contains("\"c\"");
  }

  @Test
  void presetsAtTopLevel_alsoValidated() throws Exception {
    JsonNode template =
        read(
            """
        {
          "properties": [ { "id": "x" } ],
          "presets": { "missing": "y" }
        }
        """);
    List<Finding> findings = rule.apply(FILE, template);
    assertThat(findings).hasSize(1);
    assertThat(findings.get(0).jsonPointer()).isEqualTo("/presets/missing");
  }

  private static JsonNode read(String json) throws Exception {
    return MAPPER.readTree(json);
  }
}
