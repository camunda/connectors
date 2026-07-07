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

class PresetCoverageRuleTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final Path FILE = Path.of("my-connector/element-templates/foo.json");
  private final PresetCoverageRule rule = new PresetCoverageRule();

  @Test
  void oneLevelComplete_noFindings() throws Exception {
    // S3-like: one dropdown with three choices, three presets, three step leaves.
    JsonNode template =
        read(
            """
        {
          "properties": [
            { "id": "action", "choices": [
              { "value": "upload" }, { "value": "download" }, { "value": "delete" }
            ]}
          ],
          "steps": [
            { "presetId": "upload" }, { "presetId": "download" }, { "presetId": "delete" }
          ],
          "presets": [
            { "id": "upload",   "properties": { "action": "upload" } },
            { "id": "download", "properties": { "action": "download" } },
            { "id": "delete",   "properties": { "action": "delete" } }
          ]
        }
        """);
    assertThat(rule.apply(FILE, template)).isEmpty();
  }

  @Test
  void oneLevelMissingPreset_flagged() throws Exception {
    JsonNode template =
        read(
            """
        {
          "properties": [
            { "id": "action", "choices": [
              { "value": "upload" }, { "value": "download" }, { "value": "delete" }
            ]}
          ],
          "steps": [ { "presetId": "upload" }, { "presetId": "download" } ],
          "presets": [
            { "id": "upload",   "properties": { "action": "upload" } },
            { "id": "download", "properties": { "action": "download" } }
          ]
        }
        """);
    List<Finding> findings = rule.apply(FILE, template);
    assertThat(findings).extracting(Finding::message).anyMatch(m -> m.contains("delete"));
  }

  @Test
  void twoLevelConditionalSwitching_sumNotProduct() throws Exception {
    // DynamoDB-like: operationGroup gates two distinct inner dropdowns.
    // 3 + 4 = 7 reachable leaves (NOT 12).
    JsonNode template =
        read(
            """
        {
          "properties": [
            { "id": "operationGroup", "choices": [
              { "value": "table" }, { "value": "item" }
            ]},
            { "id": "tableOp",
              "condition": { "property": "operationGroup", "equals": "table" },
              "choices": [
                { "value": "createTable" }, { "value": "deleteTable" }, { "value": "scanTable" }
              ]},
            { "id": "itemOp",
              "condition": { "property": "operationGroup", "equals": "item" },
              "choices": [
                { "value": "getItem" }, { "value": "putItem" },
                { "value": "delItem" }, { "value": "updItem" }
              ]}
          ],
          "steps": [
            { "presetId": "createTable" },
            { "presetId": "deleteTable" },
            { "presetId": "scanTable" },
            { "presetId": "getItem" },
            { "presetId": "putItem" },
            { "presetId": "delItem" },
            { "presetId": "updItem" }
          ],
          "presets": [
            { "id": "createTable", "properties": { "operationGroup": "table", "tableOp": "createTable" } },
            { "id": "deleteTable", "properties": { "operationGroup": "table", "tableOp": "deleteTable" } },
            { "id": "scanTable",   "properties": { "operationGroup": "table", "tableOp": "scanTable" } },
            { "id": "getItem",     "properties": { "operationGroup": "item",  "itemOp":  "getItem" } },
            { "id": "putItem",     "properties": { "operationGroup": "item",  "itemOp":  "putItem" } },
            { "id": "delItem",     "properties": { "operationGroup": "item",  "itemOp":  "delItem" } },
            { "id": "updItem",     "properties": { "operationGroup": "item",  "itemOp":  "updItem" } }
          ]
        }
        """);
    assertThat(rule.apply(FILE, template)).isEmpty();
  }

  @Test
  void orphanPreset_flagged() throws Exception {
    JsonNode template =
        read(
            """
        {
          "properties": [
            { "id": "action", "choices": [ { "value": "upload" } ] }
          ],
          "steps": [ { "presetId": "upload" } ],
          "presets": [
            { "id": "upload", "properties": { "action": "upload" } },
            { "id": "bogus",  "properties": { "action": "wrong" } }
          ]
        }
        """);
    List<Finding> findings = rule.apply(FILE, template);
    assertThat(findings).extracting(Finding::message).anyMatch(m -> m.contains("bogus"));
  }

  @Test
  void duplicateAssignmentPresets_flagged() throws Exception {
    JsonNode template =
        read(
            """
        {
          "properties": [
            { "id": "action", "choices": [ { "value": "upload" } ] }
          ],
          "steps": [ { "presetId": "upload" } ],
          "presets": [
            { "id": "upload",  "properties": { "action": "upload" } },
            { "id": "upload2", "properties": { "action": "upload" } }
          ]
        }
        """);
    List<Finding> findings = rule.apply(FILE, template);
    assertThat(findings).extracting(Finding::message).anyMatch(m -> m.contains("duplicates"));
  }

  @Test
  void missingStepLeaf_flagged() throws Exception {
    JsonNode template =
        read(
            """
        {
          "properties": [
            { "id": "action", "choices": [ { "value": "a" }, { "value": "b" } ] }
          ],
          "steps": [ { "presetId": "pa" } ],
          "presets": [
            { "id": "pa", "properties": { "action": "a" } },
            { "id": "pb", "properties": { "action": "b" } }
          ]
        }
        """);
    List<Finding> findings = rule.apply(FILE, template);
    // Reachable {action=b} has a preset but no step leaf — flag.
    assertThat(findings)
        .extracting(Finding::message)
        .anyMatch(m -> m.contains("no matching leaf step"));
  }

  @Test
  void ignoredConnector_noFindings() throws Exception {
    JsonNode template =
        read(
            """
        {
          "properties": [],
          "presets": [ { "id": "p", "properties": { "x": "y" } } ]
        }
        """);
    assertThat(rule.apply(Path.of("connectors/agentic-ai/element-templates/x.json"), template))
        .isEmpty();
  }

  @Test
  void searchSpaceExceedsCap_singleFinding() throws Exception {
    // Build 7 op-keys with 9 choices each → (1+9)^7 = 10,000,000 candidate assignments,
    // well above the 5,000,000 cap.
    StringBuilder properties = new StringBuilder();
    StringBuilder presetProps = new StringBuilder();
    for (int k = 0; k < 7; k++) {
      if (k > 0) {
        properties.append(",");
        presetProps.append(",");
      }
      properties.append("{\"id\":\"k").append(k).append("\",\"choices\":[");
      for (int c = 0; c < 9; c++) {
        if (c > 0) properties.append(",");
        properties.append("{\"value\":\"v").append(c).append("\"}");
      }
      properties.append("]}");
      presetProps.append("\"k").append(k).append("\":\"v0\"");
    }
    String json =
        "{\"properties\":["
            + properties
            + "],\"presets\":[{\"id\":\"p\",\"properties\":{"
            + presetProps
            + "}}],\"steps\":[]}";
    List<Finding> findings = rule.apply(FILE, read(json));
    assertThat(findings).hasSize(1);
    assertThat(findings.get(0).jsonPointer()).isEqualTo("/presets");
    assertThat(findings.get(0).message()).contains("search space").contains("too large");
  }

  private static JsonNode read(String json) throws Exception {
    return MAPPER.readTree(json);
  }
}
