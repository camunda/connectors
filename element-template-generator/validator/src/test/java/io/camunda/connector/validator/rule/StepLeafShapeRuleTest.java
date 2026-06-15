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

class StepLeafShapeRuleTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final Path FILE = Path.of("my-connector/element-templates/foo.json");
  private final StepLeafShapeRule rule = new StepLeafShapeRule();

  @Test
  void wellFormedLeaf_noFindings() throws Exception {
    JsonNode template =
        read(
            """
        { "steps": [ { "name": "x", "presetId": "p", "keywords": ["x"] } ] }
        """);
    assertThat(rule.apply(FILE, template)).isEmpty();
  }

  @Test
  void leafMissingPresetId_flagged() throws Exception {
    JsonNode template =
        read(
            """
        { "steps": [ { "name": "x", "keywords": ["x"] } ] }
        """);
    List<Finding> findings = rule.apply(FILE, template);
    assertThat(findings).hasSize(1);
    assertThat(findings.get(0).jsonPointer()).isEqualTo("/steps/0/presetId");
  }

  @Test
  void leafBlankPresetId_flagged() throws Exception {
    JsonNode template =
        read(
            """
        { "steps": [ { "presetId": "  ", "keywords": ["x"] } ] }
        """);
    assertThat(rule.apply(FILE, template)).hasSize(1);
  }

  @Test
  void leafMissingKeywords_flagged() throws Exception {
    JsonNode template = read("{ \"steps\": [ { \"presetId\": \"p\" } ] }");
    List<Finding> findings = rule.apply(FILE, template);
    assertThat(findings).hasSize(1);
    assertThat(findings.get(0).jsonPointer()).isEqualTo("/steps/0/keywords");
  }

  @Test
  void leafEmptyKeywords_flagged() throws Exception {
    JsonNode template = read("{ \"steps\": [ { \"presetId\": \"p\", \"keywords\": [] } ] }");
    List<Finding> findings = rule.apply(FILE, template);
    assertThat(findings).hasSize(1);
    assertThat(findings.get(0).message()).contains("non-empty");
  }

  @Test
  void leafKeywordsNotArray_flagged() throws Exception {
    JsonNode template = read("{ \"steps\": [ { \"presetId\": \"p\", \"keywords\": \"x\" } ] }");
    List<Finding> findings = rule.apply(FILE, template);
    assertThat(findings).hasSize(1);
    assertThat(findings.get(0).jsonPointer()).isEqualTo("/steps/0/keywords");
    assertThat(findings.get(0).message()).contains("must be an array");
  }

  @Test
  void nestedLeafChecked() throws Exception {
    JsonNode template =
        read(
            """
        { "steps": [
          { "name": "g", "steps": [ { "name": "leaf", "presetId": "p" } ] }
        ] }
        """);
    List<Finding> findings = rule.apply(FILE, template);
    assertThat(findings).hasSize(1);
    assertThat(findings.get(0).jsonPointer()).isEqualTo("/steps/0/steps/0/keywords");
  }

  @Test
  void groupNodes_notFlagged() throws Exception {
    JsonNode template =
        read(
            """
        { "steps": [
          { "name": "g",
            "steps": [ { "presetId": "p", "keywords": ["x"] } ] }
        ] }
        """);
    assertThat(rule.apply(FILE, template)).isEmpty();
  }

  @Test
  void ignoredConnector_noFindings() throws Exception {
    JsonNode template = read("{ \"steps\": [ { } ] }");
    assertThat(rule.apply(Path.of("connectors/aws/element-templates/aws.json"), template))
        .isEmpty();
  }

  private static JsonNode read(String json) throws Exception {
    return MAPPER.readTree(json);
  }
}
