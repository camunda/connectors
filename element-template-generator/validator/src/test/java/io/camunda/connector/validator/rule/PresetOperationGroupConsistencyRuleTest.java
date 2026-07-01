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

class PresetOperationGroupConsistencyRuleTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final Path FILE = Path.of("my-connector/element-templates/foo.json");
  private final PresetOperationGroupConsistencyRule rule =
      new PresetOperationGroupConsistencyRule();

  @Test
  void wellFormed_noFindings() throws Exception {
    JsonNode template =
        read(
            """
        {
          "groups": [ { "id": "operation" }, { "id": "auth" } ],
          "properties": [
            { "id": "operationGroup", "group": "operation" },
            { "id": "creds", "group": "auth" }
          ],
          "presets": [
            { "id": "p", "properties": { "operationGroup": "x" } }
          ]
        }
        """);
    assertThat(rule.apply(FILE, template)).isEmpty();
  }

  @Test
  void noPresets_noFindings() throws Exception {
    JsonNode template = read("{ \"groups\": [ { \"id\": \"auth\" } ] }");
    assertThat(rule.apply(FILE, template)).isEmpty();
  }

  @Test
  void presetReferencesNonOperationProperty_flagged() throws Exception {
    JsonNode template =
        read(
            """
        {
          "groups": [ { "id": "operation" } ],
          "properties": [
            { "id": "operationGroup", "group": "operation" },
            { "id": "creds", "group": "auth" }
          ],
          "presets": [
            { "id": "p", "properties": { "operationGroup": "x", "creds": "y" } }
          ]
        }
        """);
    List<Finding> findings = rule.apply(FILE, template);
    assertThat(findings).hasSize(1);
    assertThat(findings.get(0).message()).contains("creds").contains("operation");
  }

  @Test
  void operationGroupNotFirst_flagged() throws Exception {
    JsonNode template =
        read(
            """
        {
          "groups": [ { "id": "auth" }, { "id": "operation" } ],
          "properties": [ { "id": "operationGroup", "group": "operation" } ],
          "presets": [ { "id": "p", "properties": { "operationGroup": "x" } } ]
        }
        """);
    List<Finding> findings = rule.apply(FILE, template);
    assertThat(findings).hasSize(1);
    assertThat(findings.get(0).jsonPointer()).isEqualTo("/groups/1");
    assertThat(findings.get(0).message()).contains("first");
  }

  @Test
  void operationGroupMissing_flagged() throws Exception {
    JsonNode template =
        read(
            """
        {
          "groups": [ { "id": "auth" } ],
          "properties": [ { "id": "operationGroup", "group": "auth" } ],
          "presets": [ { "id": "p", "properties": { "operationGroup": "x" } } ]
        }
        """);
    List<Finding> findings = rule.apply(FILE, template);
    assertThat(findings).hasSize(2);
    assertThat(findings).extracting(Finding::jsonPointer).contains("/groups");
  }

  @Test
  void ignoredConnector_noFindings() throws Exception {
    JsonNode template =
        read(
            """
        {
          "groups": [],
          "properties": [ { "id": "x", "group": "wrong" } ],
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
