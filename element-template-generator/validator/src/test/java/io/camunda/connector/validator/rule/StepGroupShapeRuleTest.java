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

class StepGroupShapeRuleTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final Path FILE = Path.of("my-connector/element-templates/foo.json");
  private final StepGroupShapeRule rule = new StepGroupShapeRule();

  @Test
  void wellFormedGroup_noFindings() throws Exception {
    JsonNode template =
        read(
            """
        { "steps": [
          { "name": "Table",
            "description": "Table ops",
            "steps": [ { "name": "Create", "presetId": "p", "keywords": ["x"] } ]
          }
        ] }
        """);
    assertThat(rule.apply(FILE, template)).isEmpty();
  }

  @Test
  void groupWithKeywords_flagged() throws Exception {
    JsonNode template =
        read(
            """
        { "steps": [
          { "name": "g", "keywords": ["x"],
            "steps": [ { "name": "leaf", "presetId": "p" } ] }
        ] }
        """);
    List<Finding> findings = rule.apply(FILE, template);
    assertThat(findings).hasSize(1);
    assertThat(findings.get(0).jsonPointer()).isEqualTo("/steps/0/keywords");
    assertThat(findings.get(0).message()).contains("keywords");
  }

  @Test
  void groupWithPresetId_flagged() throws Exception {
    JsonNode template =
        read(
            """
        { "steps": [
          { "name": "g", "presetId": "p",
            "steps": [ { "name": "leaf", "presetId": "p" } ] }
        ] }
        """);
    List<Finding> findings = rule.apply(FILE, template);
    assertThat(findings).hasSize(1);
    assertThat(findings.get(0).jsonPointer()).isEqualTo("/steps/0/presetId");
  }

  @Test
  void emptyGroupStepsArray_flagged() throws Exception {
    JsonNode template =
        read(
            """
        { "steps": [ { "name": "g", "steps": [] } ] }
        """);
    List<Finding> findings = rule.apply(FILE, template);
    assertThat(findings).hasSize(1);
    assertThat(findings.get(0).jsonPointer()).isEqualTo("/steps/0/steps");
    assertThat(findings.get(0).message()).contains("non-empty");
  }

  @Test
  void leafNodes_notFlagged() throws Exception {
    JsonNode template =
        read(
            """
        { "steps": [ { "name": "leaf", "presetId": "p", "keywords": ["x"] } ] }
        """);
    assertThat(rule.apply(FILE, template)).isEmpty();
  }

  @Test
  void ignoredConnector_noFindings() throws Exception {
    JsonNode template = read("{ \"steps\": [ { \"steps\": [], \"keywords\": [\"x\"] } ] }");
    assertThat(rule.apply(Path.of("connectors/agentic-ai/element-templates/aws.json"), template))
        .isEmpty();
  }

  private static JsonNode read(String json) throws Exception {
    return MAPPER.readTree(json);
  }
}
