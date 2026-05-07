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

class UniqueIdVersionRuleTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private final UniqueIdVersionRule rule = new UniqueIdVersionRule();

  @Test
  void allUnique_noFindings() throws Exception {
    Map<Path, JsonNode> templates = new LinkedHashMap<>();
    templates.put(
        Path.of("connectors/foo/element-templates/foo.json"),
        read("{ \"id\": \"io.foo\", \"version\": 5 }"));
    templates.put(
        Path.of("connectors/foo/element-templates/versioned/foo-4.json"),
        read("{ \"id\": \"io.foo\", \"version\": 4 }"));
    templates.put(
        Path.of("connectors/bar/element-templates/bar.json"),
        read("{ \"id\": \"io.bar\", \"version\": 5 }"));
    assertThat(rule.apply(templates)).isEmpty();
  }

  @Test
  void twoFilesSameIdSameVersion_twoFindings() throws Exception {
    Map<Path, JsonNode> templates = new LinkedHashMap<>();
    templates.put(
        Path.of("connectors/foo/element-templates/foo.json"),
        read("{ \"id\": \"io.foo\", \"version\": 5 }"));
    templates.put(
        Path.of("connectors/foo/element-templates/versioned/foo-5.json"),
        read("{ \"id\": \"io.foo\", \"version\": 5 }"));
    List<Finding> findings = rule.apply(templates);
    assertThat(findings).hasSize(2);
    assertThat(findings).allMatch(f -> f.ruleId().equals(UniqueIdVersionRule.ID));
    assertThat(findings.get(0).message()).contains("io.foo").contains("5");
    assertThat(findings.get(0).message()).contains(findings.get(1).file().toString());
  }

  @Test
  void sameIdDifferentVersions_noFindings() throws Exception {
    Map<Path, JsonNode> templates = new LinkedHashMap<>();
    templates.put(
        Path.of("connectors/foo/element-templates/foo.json"),
        read("{ \"id\": \"io.foo\", \"version\": 5 }"));
    templates.put(
        Path.of("connectors/foo/element-templates/versioned/foo-4.json"),
        read("{ \"id\": \"io.foo\", \"version\": 4 }"));
    assertThat(rule.apply(templates)).isEmpty();
  }

  @Test
  void sameVersionDifferentIds_noFindings() throws Exception {
    Map<Path, JsonNode> templates = new LinkedHashMap<>();
    templates.put(
        Path.of("connectors/foo/element-templates/foo.json"),
        read("{ \"id\": \"io.foo\", \"version\": 5 }"));
    templates.put(
        Path.of("connectors/bar/element-templates/bar.json"),
        read("{ \"id\": \"io.bar\", \"version\": 5 }"));
    assertThat(rule.apply(templates)).isEmpty();
  }

  @Test
  void threeFilesSameKey_threeFindings() throws Exception {
    Map<Path, JsonNode> templates = new LinkedHashMap<>();
    templates.put(Path.of("a.json"), read("{ \"id\": \"x\", \"version\": 1 }"));
    templates.put(Path.of("b.json"), read("{ \"id\": \"x\", \"version\": 1 }"));
    templates.put(Path.of("c.json"), read("{ \"id\": \"x\", \"version\": 1 }"));
    List<Finding> findings = rule.apply(templates);
    assertThat(findings).hasSize(3);
  }

  @Test
  void missingIdOrVersion_skipped() throws Exception {
    Map<Path, JsonNode> templates = new LinkedHashMap<>();
    templates.put(Path.of("a.json"), read("{ \"id\": \"x\" }"));
    templates.put(Path.of("b.json"), read("{ \"version\": 1 }"));
    templates.put(Path.of("c.json"), read("{}"));
    assertThat(rule.apply(templates)).isEmpty();
  }

  @Test
  void multipleDistinctClashes_allReported() throws Exception {
    Map<Path, JsonNode> templates = new LinkedHashMap<>();
    templates.put(Path.of("a.json"), read("{ \"id\": \"x\", \"version\": 1 }"));
    templates.put(Path.of("b.json"), read("{ \"id\": \"x\", \"version\": 1 }"));
    templates.put(Path.of("c.json"), read("{ \"id\": \"y\", \"version\": 2 }"));
    templates.put(Path.of("d.json"), read("{ \"id\": \"y\", \"version\": 2 }"));
    assertThat(rule.apply(templates)).hasSize(4);
  }

  private static JsonNode read(String json) throws Exception {
    return MAPPER.readTree(json);
  }
}
