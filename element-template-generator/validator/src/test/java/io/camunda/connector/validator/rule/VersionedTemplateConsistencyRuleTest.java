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

class VersionedTemplateConsistencyRuleTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private final VersionedTemplateConsistencyRule rule = new VersionedTemplateConsistencyRule();

  @Test
  void filenameVersionMatchesField_noFindings() throws Exception {
    Map<Path, JsonNode> templates = new LinkedHashMap<>();
    templates.put(
        Path.of("connectors/foo/element-templates/versioned/foo-3.json"),
        read("{ \"id\": \"io.foo\", \"version\": 3 }"));
    assertThat(rule.apply(templates)).isEmpty();
  }

  @Test
  void filenameVersionMismatch_oneFinding() throws Exception {
    Map<Path, JsonNode> templates = new LinkedHashMap<>();
    templates.put(
        Path.of("connectors/foo/element-templates/versioned/foo-3.json"),
        read("{ \"id\": \"io.foo\", \"version\": 4 }"));
    List<Finding> findings = rule.apply(templates);
    assertThat(findings).hasSize(1);
    assertThat(findings.get(0).jsonPointer()).isEqualTo("/version");
    assertThat(findings.get(0).message()).contains("3").contains("4");
  }

  @Test
  void idDriftAcrossVersions_notFlagged() throws Exception {
    Map<Path, JsonNode> templates = new LinkedHashMap<>();
    templates.put(
        Path.of("connectors/foo/element-templates/foo.json"),
        read("{ \"id\": \"io.foo.v2\", \"version\": 5 }"));
    templates.put(
        Path.of("connectors/foo/element-templates/versioned/foo-3.json"),
        read("{ \"id\": \"io.foo.legacy\", \"version\": 3 }"));
    assertThat(rule.apply(templates)).isEmpty();
  }

  @Test
  void filenameWithoutVersionSuffix_findingOnFormat() throws Exception {
    Map<Path, JsonNode> templates = new LinkedHashMap<>();
    templates.put(
        Path.of("connectors/foo/element-templates/versioned/badname.json"),
        read("{ \"id\": \"x\", \"version\": 1 }"));
    List<Finding> findings = rule.apply(templates);
    assertThat(findings).hasSize(1);
    assertThat(findings.get(0).message()).contains("{base}-{version}.json");
  }

  @Test
  void versionFieldMissing_oneFinding() throws Exception {
    Map<Path, JsonNode> templates = new LinkedHashMap<>();
    templates.put(
        Path.of("connectors/foo/element-templates/versioned/foo-3.json"),
        read("{ \"id\": \"x\" }"));
    List<Finding> findings = rule.apply(templates);
    assertThat(findings).hasSize(1);
    assertThat(findings.get(0).jsonPointer()).isEqualTo("/version");
  }

  @Test
  void nonVersionedFiles_ignored() throws Exception {
    Map<Path, JsonNode> templates = new LinkedHashMap<>();
    templates.put(Path.of("connectors/foo/element-templates/foo.json"), read("{ \"id\": \"x\" }"));
    assertThat(rule.apply(templates)).isEmpty();
  }

  @Test
  void leadingZeroSuffixWithMissingVersion_tolerated() throws Exception {
    Map<Path, JsonNode> templates = new LinkedHashMap<>();
    templates.put(
        Path.of("connectors/foo/element-templates/versioned/foo-0.json"),
        read("{ \"id\": \"io.foo\" }"));
    templates.put(
        Path.of("connectors/foo/element-templates/versioned/foo-01.json"),
        read("{ \"id\": \"io.foo\" }"));
    templates.put(
        Path.of("connectors/foo/element-templates/versioned/foo-02.json"),
        read("{ \"id\": \"io.foo\" }"));
    assertThat(rule.apply(templates)).isEmpty();
  }

  @Test
  void leadingZeroSuffixWithMatchingVersion_noFindings() throws Exception {
    Map<Path, JsonNode> templates = new LinkedHashMap<>();
    templates.put(
        Path.of("connectors/foo/element-templates/versioned/foo-02.json"),
        read("{ \"id\": \"io.foo\", \"version\": 2 }"));
    assertThat(rule.apply(templates)).isEmpty();
  }

  @Test
  void leadingZeroSuffixWithMismatchedVersion_oneFinding() throws Exception {
    Map<Path, JsonNode> templates = new LinkedHashMap<>();
    templates.put(
        Path.of("connectors/foo/element-templates/versioned/foo-01.json"),
        read("{ \"id\": \"io.foo\", \"version\": 5 }"));
    List<Finding> findings = rule.apply(templates);
    assertThat(findings).hasSize(1);
    assertThat(findings.get(0).jsonPointer()).isEqualTo("/version");
    assertThat(findings.get(0).message()).contains("1").contains("5");
  }

  private static JsonNode read(String json) throws Exception {
    return MAPPER.readTree(json);
  }
}
