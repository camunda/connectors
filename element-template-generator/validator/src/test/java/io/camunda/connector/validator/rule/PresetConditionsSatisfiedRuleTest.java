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

class PresetConditionsSatisfiedRuleTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final Path FILE = Path.of("my-connector/element-templates/foo.json");
  private final PresetConditionsSatisfiedRule rule = new PresetConditionsSatisfiedRule();

  @Test
  void consistentPreset_noFindings() throws Exception {
    JsonNode template =
        read(
            """
        {
          "properties": [
            { "id": "operationGroup" },
            { "id": "tableOp",
              "condition": { "property": "operationGroup", "equals": "table" } }
          ],
          "presets": [
            { "id": "p", "properties": { "operationGroup": "table", "tableOp": "create" } }
          ]
        }
        """);
    assertThat(rule.apply(FILE, template)).isEmpty();
  }

  @Test
  void inconsistentPreset_flagged() throws Exception {
    JsonNode template =
        read(
            """
        {
          "properties": [
            { "id": "operationGroup" },
            { "id": "tableOp",
              "condition": { "property": "operationGroup", "equals": "table" } }
          ],
          "presets": [
            { "id": "bad", "properties": { "operationGroup": "item", "tableOp": "create" } }
          ]
        }
        """);
    List<Finding> findings = rule.apply(FILE, template);
    assertThat(findings).hasSize(1);
    assertThat(findings.get(0).jsonPointer()).isEqualTo("/presets/0/properties/tableOp");
    assertThat(findings.get(0).message()).contains("tableOp");
  }

  @Test
  void switchingPattern_eitherDeclarationHolds_noFindings() throws Exception {
    JsonNode template =
        read(
            """
        {
          "properties": [
            { "id": "operationGroup" },
            { "id": "innerOp",
              "condition": { "property": "operationGroup", "equals": "table" } },
            { "id": "innerOp",
              "condition": { "property": "operationGroup", "equals": "item" } }
          ],
          "presets": [
            { "id": "p", "properties": { "operationGroup": "item", "innerOp": "getItem" } }
          ]
        }
        """);
    assertThat(rule.apply(FILE, template)).isEmpty();
  }

  @Test
  void unconditionalProperty_alwaysHolds() throws Exception {
    JsonNode template =
        read(
            """
        {
          "properties": [ { "id": "foo" } ],
          "presets": [ { "id": "p", "properties": { "foo": "anything" } } ]
        }
        """);
    assertThat(rule.apply(FILE, template)).isEmpty();
  }

  @Test
  void allMatchCondition_evaluated() throws Exception {
    JsonNode template =
        read(
            """
        {
          "properties": [
            { "id": "a" }, { "id": "b" },
            { "id": "c",
              "condition": { "allMatch": [
                { "property": "a", "equals": "1" },
                { "property": "b", "equals": "2" }
              ]}}
          ],
          "presets": [
            { "id": "ok",  "properties": { "a": "1", "b": "2", "c": "x" } },
            { "id": "bad", "properties": { "a": "1", "b": "3", "c": "x" } }
          ]
        }
        """);
    List<Finding> findings = rule.apply(FILE, template);
    assertThat(findings).hasSize(1);
    assertThat(findings.get(0).jsonPointer()).isEqualTo("/presets/1/properties/c");
  }

  @Test
  void ignoredConnector_noFindings() throws Exception {
    JsonNode template =
        read(
            """
        {
          "properties": [
            { "id": "x",
              "condition": { "property": "y", "equals": "z" } }
          ],
          "presets": [ { "id": "p", "properties": { "x": "v" } } ]
        }
        """);
    assertThat(rule.apply(Path.of("connectors/agentic-ai/element-templates/x.json"), template))
        .isEmpty();
  }

  private static JsonNode read(String json) throws Exception {
    return MAPPER.readTree(json);
  }
}
