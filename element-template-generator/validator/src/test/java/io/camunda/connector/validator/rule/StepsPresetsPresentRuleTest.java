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

class StepsPresetsPresentRuleTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  // A path that does NOT match any connector on the ignore list.
  private static final Path FILE = Path.of("my-connector/element-templates/foo.json");
  private final StepsPresetsPresentRule rule = new StepsPresetsPresentRule();

  @Test
  void bothPresentAndNonEmpty_noFindings() throws Exception {
    JsonNode template =
        read(
            """
        {
          "steps":  [ { "name": "x", "presetId": "p" } ],
          "presets": [ { "id": "p", "properties": {} } ]
        }
        """);
    assertThat(rule.apply(FILE, template)).isEmpty();
  }

  @Test
  void missingSteps_oneFinding() throws Exception {
    JsonNode template = read("{ \"presets\": [ { \"id\": \"p\" } ] }");
    List<Finding> findings = rule.apply(FILE, template);
    assertThat(findings).hasSize(1);
    assertThat(findings.get(0).jsonPointer()).isEqualTo("/steps");
    assertThat(findings.get(0).message()).contains("missing");
  }

  @Test
  void emptyPresets_oneFinding() throws Exception {
    JsonNode template =
        read(
            """
        { "steps": [ { "name": "x" } ], "presets": [] }
        """);
    List<Finding> findings = rule.apply(FILE, template);
    assertThat(findings).hasSize(1);
    assertThat(findings.get(0).jsonPointer()).isEqualTo("/presets");
    assertThat(findings.get(0).message()).contains("non-empty");
  }

  @Test
  void stepsNotAnArray_oneFinding() throws Exception {
    JsonNode template =
        read(
            """
        { "steps": "nope", "presets": [{}] }
        """);
    List<Finding> findings = rule.apply(FILE, template);
    assertThat(findings).hasSize(1);
    assertThat(findings.get(0).jsonPointer()).isEqualTo("/steps");
    assertThat(findings.get(0).message()).contains("must be an array");
  }

  @Test
  void ignoredConnector_noFindings() throws Exception {
    JsonNode template = read("{}");
    // "agentic-ai" is on the ignore list.
    Path ignored = Path.of("connectors/agentic-ai/element-templates/foo.json");
    assertThat(rule.apply(ignored, template)).isEmpty();
  }

  private static JsonNode read(String json) throws Exception {
    return MAPPER.readTree(json);
  }
}
