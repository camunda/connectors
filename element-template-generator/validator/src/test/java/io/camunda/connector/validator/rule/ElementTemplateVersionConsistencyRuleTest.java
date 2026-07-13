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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ElementTemplateVersionConsistencyRuleTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private final ElementTemplateVersionConsistencyRule rule =
      new ElementTemplateVersionConsistencyRule();

  @Test
  void hiddenValueMatchesVersion_noFindings() throws Exception {
    Map<Path, JsonNode> templates = new LinkedHashMap<>();
    templates.put(Path.of("connectors/foo/element-templates/foo.json"), read(hidden(6, "\"6\"")));
    assertThat(rule.apply(templates)).isEmpty();
  }

  @Test
  void hiddenValueLagsVersion_oneFinding() throws Exception {
    Map<Path, JsonNode> templates = new LinkedHashMap<>();
    templates.put(Path.of("connectors/foo/element-templates/foo.json"), read(hidden(6, "\"4\"")));
    List<Finding> findings = rule.apply(templates);
    assertThat(findings).hasSize(1);
    assertThat(findings.get(0).jsonPointer()).isEqualTo("/properties/0/value");
    assertThat(findings.get(0).message()).contains("4").contains("6");
  }

  @Test
  void noHiddenProperty_silent() throws Exception {
    Map<Path, JsonNode> templates = new LinkedHashMap<>();
    templates.put(
        Path.of("connectors/foo/element-templates/foo.json"),
        read(
            "{ \"version\": 3, \"properties\": [ { \"value\": \"x\", \"binding\": { \"key\":"
                + " \"someHeader\", \"type\": \"zeebe:taskHeader\" } } ] }"));
    assertThat(rule.apply(templates)).isEmpty();
  }

  @Test
  void noPropertiesArray_silent() throws Exception {
    Map<Path, JsonNode> templates = new LinkedHashMap<>();
    templates.put(Path.of("connectors/foo/element-templates/foo.json"), read("{ \"version\": 3 }"));
    assertThat(rule.apply(templates)).isEmpty();
  }

  @Test
  void missingHiddenValue_oneFinding() throws Exception {
    Map<Path, JsonNode> templates = new LinkedHashMap<>();
    templates.put(
        Path.of("connectors/foo/element-templates/foo.json"),
        read(
            "{ \"version\": 3, \"properties\": [ { \"binding\": { \"key\":"
                + " \"elementTemplateVersion\", \"type\": \"zeebe:taskHeader\" } } ] }"));
    List<Finding> findings = rule.apply(templates);
    assertThat(findings).hasSize(1);
    assertThat(findings.get(0).message()).contains("<missing>").contains("3");
  }

  @Test
  void versionedSnapshotMatchesOwnVersion_noFindings() throws Exception {
    Map<Path, JsonNode> templates = new LinkedHashMap<>();
    templates.put(
        Path.of("connectors/foo/element-templates/versioned/foo-5.json"), read(hidden(5, "\"5\"")));
    assertThat(rule.apply(templates)).isEmpty();
  }

  @Test
  void versionedSnapshotDrifts_oneFinding() throws Exception {
    Map<Path, JsonNode> templates = new LinkedHashMap<>();
    templates.put(
        Path.of("connectors/foo/element-templates/versioned/foo-5.json"), read(hidden(5, "\"4\"")));
    List<Finding> findings = rule.apply(templates);
    assertThat(findings).hasSize(1);
    assertThat(findings.get(0).message()).contains("4").contains("5");
  }

  @Test
  void nonNumericTopLevelVersion_silent() throws Exception {
    // A missing/non-numeric top-level version is another rule's concern; nothing to compare here.
    Map<Path, JsonNode> templates = new LinkedHashMap<>();
    templates.put(
        Path.of("connectors/foo/element-templates/foo.json"),
        read(
            "{ \"properties\": [ { \"value\": \"4\", \"binding\": { \"key\":"
                + " \"elementTemplateVersion\", \"type\": \"zeebe:taskHeader\" } } ] }"));
    assertThat(rule.apply(templates)).isEmpty();
  }

  @Test
  void wrongBindingType_silent() throws Exception {
    // key matches but binding type is not a task header — not the property we guard.
    Map<Path, JsonNode> templates = new LinkedHashMap<>();
    templates.put(
        Path.of("connectors/foo/element-templates/foo.json"),
        read(
            "{ \"version\": 6, \"properties\": [ { \"value\": \"4\", \"binding\": { \"key\":"
                + " \"elementTemplateVersion\", \"type\": \"zeebe:input\" } } ] }"));
    assertThat(rule.apply(templates)).isEmpty();
  }

  private static String hidden(int version, String value) {
    return "{ \"version\": "
        + version
        + ", \"properties\": [ { \"id\": \"version\", \"value\": "
        + value
        + ", \"type\": \"Hidden\", \"binding\": { \"key\": \"elementTemplateVersion\", \"type\":"
        + " \"zeebe:taskHeader\" } } ] }";
  }

  private static JsonNode read(String json) throws Exception {
    return MAPPER.readTree(json);
  }
}
