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

class CurrentVersionBumpRuleTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private final CurrentVersionBumpRule rule = new CurrentVersionBumpRule();

  @Test
  void currentExactlyOneHigher_noFindings() throws Exception {
    Map<Path, JsonNode> templates = new LinkedHashMap<>();
    templates.put(
        Path.of("connectors/foo/element-templates/foo.json"),
        read("{ \"id\": \"io.foo\", \"version\": 5 }"));
    templates.put(
        Path.of("connectors/foo/element-templates/versioned/foo-4.json"),
        read("{ \"id\": \"io.foo\", \"version\": 4 }"));
    templates.put(
        Path.of("connectors/foo/element-templates/versioned/foo-3.json"),
        read("{ \"id\": \"io.foo\", \"version\": 3 }"));
    assertThat(rule.apply(templates)).isEmpty();
  }

  @Test
  void currentTwoHigher_oneFinding() throws Exception {
    Map<Path, JsonNode> templates = new LinkedHashMap<>();
    templates.put(
        Path.of("connectors/foo/element-templates/foo.json"),
        read("{ \"id\": \"io.foo\", \"version\": 6 }"));
    templates.put(
        Path.of("connectors/foo/element-templates/versioned/foo-4.json"),
        read("{ \"id\": \"io.foo\", \"version\": 4 }"));
    List<Finding> findings = rule.apply(templates);
    assertThat(findings).hasSize(1);
    assertThat(findings.get(0).jsonPointer()).isEqualTo("/version");
    assertThat(findings.get(0).message()).contains("6").contains("4").contains("5");
  }

  @Test
  void currentEqualsLatestSnapshot_oneFinding() throws Exception {
    Map<Path, JsonNode> templates = new LinkedHashMap<>();
    templates.put(
        Path.of("connectors/foo/element-templates/foo.json"),
        read("{ \"id\": \"io.foo\", \"version\": 4 }"));
    templates.put(
        Path.of("connectors/foo/element-templates/versioned/foo-4.json"),
        read("{ \"id\": \"io.foo\", \"version\": 4 }"));
    List<Finding> findings = rule.apply(templates);
    assertThat(findings).hasSize(1);
    assertThat(findings.get(0).message()).contains("4").contains("5");
  }

  @Test
  void noVersionedSiblingWithSameId_silent() throws Exception {
    // Intentional id rename: current is .v2 while every snapshot is .v1.
    Map<Path, JsonNode> templates = new LinkedHashMap<>();
    templates.put(
        Path.of("connectors/foo/element-templates/foo.json"),
        read("{ \"id\": \"io.foo.v2\", \"version\": 1 }"));
    templates.put(
        Path.of("connectors/foo/element-templates/versioned/foo-7.json"),
        read("{ \"id\": \"io.foo.v1\", \"version\": 7 }"));
    assertThat(rule.apply(templates)).isEmpty();
  }

  @Test
  void currentVersionMissing_oneFinding() throws Exception {
    Map<Path, JsonNode> templates = new LinkedHashMap<>();
    templates.put(
        Path.of("connectors/foo/element-templates/foo.json"), read("{ \"id\": \"io.foo\" }"));
    templates.put(
        Path.of("connectors/foo/element-templates/versioned/foo-4.json"),
        read("{ \"id\": \"io.foo\", \"version\": 4 }"));
    List<Finding> findings = rule.apply(templates);
    assertThat(findings).hasSize(1);
    assertThat(findings.get(0).message()).contains("missing").contains("5");
  }

  @Test
  void scopedPerConnector_noCrossConnectorMatch() throws Exception {
    // Same id under two different element-templates trees should not interfere.
    Map<Path, JsonNode> templates = new LinkedHashMap<>();
    templates.put(
        Path.of("connectors/foo/element-templates/x.json"),
        read("{ \"id\": \"shared.id\", \"version\": 2 }"));
    templates.put(
        Path.of("connectors/foo/element-templates/versioned/x-1.json"),
        read("{ \"id\": \"shared.id\", \"version\": 1 }"));
    templates.put(
        Path.of("connectors/bar/element-templates/x.json"),
        read("{ \"id\": \"shared.id\", \"version\": 9 }"));
    templates.put(
        Path.of("connectors/bar/element-templates/versioned/x-8.json"),
        read("{ \"id\": \"shared.id\", \"version\": 8 }"));
    assertThat(rule.apply(templates)).isEmpty();
  }

  @Test
  void hybridLineageIndependent_noFindings() throws Exception {
    // Hybrid has its own id, so it's compared only against versioned hybrid snapshots.
    Map<Path, JsonNode> templates = new LinkedHashMap<>();
    templates.put(
        Path.of("connectors/foo/element-templates/foo.json"),
        read("{ \"id\": \"io.foo\", \"version\": 5 }"));
    templates.put(
        Path.of("connectors/foo/element-templates/versioned/foo-4.json"),
        read("{ \"id\": \"io.foo\", \"version\": 4 }"));
    templates.put(
        Path.of("connectors/foo/element-templates/hybrid/foo-hybrid.json"),
        read("{ \"id\": \"io.foo.hybrid\", \"version\": 3 }"));
    templates.put(
        Path.of("connectors/foo/element-templates/versioned/foo-hybrid-2.json"),
        read("{ \"id\": \"io.foo.hybrid\", \"version\": 2 }"));
    assertThat(rule.apply(templates)).isEmpty();
  }

  @Test
  void multipleSnapshots_usesMaximum() throws Exception {
    Map<Path, JsonNode> templates = new LinkedHashMap<>();
    templates.put(
        Path.of("connectors/foo/element-templates/foo.json"),
        read("{ \"id\": \"io.foo\", \"version\": 8 }"));
    templates.put(
        Path.of("connectors/foo/element-templates/versioned/foo-7.json"),
        read("{ \"id\": \"io.foo\", \"version\": 7 }"));
    templates.put(
        Path.of("connectors/foo/element-templates/versioned/foo-3.json"),
        read("{ \"id\": \"io.foo\", \"version\": 3 }"));
    templates.put(
        Path.of("connectors/foo/element-templates/versioned/foo-5.json"),
        read("{ \"id\": \"io.foo\", \"version\": 5 }"));
    assertThat(rule.apply(templates)).isEmpty();
  }

  private static JsonNode read(String json) throws Exception {
    return MAPPER.readTree(json);
  }
}
