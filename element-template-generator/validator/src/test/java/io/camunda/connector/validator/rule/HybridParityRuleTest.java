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

class HybridParityRuleTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private final HybridParityRule rule = new HybridParityRule();

  @Test
  void siblingsHaveIdenticalIdSets_noFindings() throws Exception {
    Map<Path, JsonNode> templates = new LinkedHashMap<>();
    templates.put(
        Path.of("connectors/foo/element-templates/foo.json"),
        read(
            """
        { "groups": [{"id":"g1"}], "properties": [{"id":"p1"},{"id":"p2"}] }
        """));
    templates.put(
        Path.of("connectors/foo/element-templates/hybrid/foo-hybrid.json"),
        read(
            """
        { "groups": [{"id":"g1"}], "properties": [{"id":"p2"},{"id":"p1"}] }
        """));
    assertThat(rule.apply(templates)).isEmpty();
  }

  @Test
  void hybridGainsProperty_oneFinding() throws Exception {
    Map<Path, JsonNode> templates = new LinkedHashMap<>();
    templates.put(
        Path.of("connectors/foo/element-templates/foo.json"),
        read(
            """
        { "properties": [{"id":"p1"}] }
        """));
    templates.put(
        Path.of("connectors/foo/element-templates/hybrid/foo-hybrid.json"),
        read(
            """
        { "properties": [{"id":"p1"},{"id":"extra"}] }
        """));
    List<Finding> findings = rule.apply(templates);
    assertThat(findings).hasSize(1);
    assertThat(findings.get(0).message()).contains("extra");
  }

  @Test
  void hybridMissesProperty_oneFinding() throws Exception {
    Map<Path, JsonNode> templates = new LinkedHashMap<>();
    templates.put(
        Path.of("connectors/foo/element-templates/foo.json"),
        read(
            """
        { "properties": [{"id":"p1"},{"id":"p2"}] }
        """));
    templates.put(
        Path.of("connectors/foo/element-templates/hybrid/foo-hybrid.json"),
        read(
            """
        { "properties": [{"id":"p1"}] }
        """));
    List<Finding> findings = rule.apply(templates);
    assertThat(findings).hasSize(1);
    assertThat(findings.get(0).message()).contains("p2");
  }

  @Test
  void hybridWithoutSibling_oneFinding() throws Exception {
    Map<Path, JsonNode> templates = new LinkedHashMap<>();
    templates.put(
        Path.of("connectors/foo/element-templates/hybrid/orphan-hybrid.json"),
        read(
            """
        { "properties": [] }
        """));
    List<Finding> findings = rule.apply(templates);
    assertThat(findings).hasSize(1);
    assertThat(findings.get(0).message()).contains("no matching non-hybrid sibling");
  }

  @Test
  void hybridPrefixedSibling_resolvedByStrippingBoth() throws Exception {
    Map<Path, JsonNode> templates = new LinkedHashMap<>();
    templates.put(
        Path.of("connectors/foo/element-templates/foo.json"),
        read(
            """
        { "properties": [{"id":"p1"}] }
        """));
    templates.put(
        Path.of("connectors/foo/element-templates/hybrid/hybrid-foo-hybrid.json"),
        read(
            """
        { "properties": [{"id":"p1"}] }
        """));
    assertThat(rule.apply(templates)).isEmpty();
  }

  @Test
  void groupSetsAlsoCompared() throws Exception {
    Map<Path, JsonNode> templates = new LinkedHashMap<>();
    templates.put(
        Path.of("connectors/foo/element-templates/foo.json"),
        read(
            """
        { "groups": [{"id":"a"}], "properties": [] }
        """));
    templates.put(
        Path.of("connectors/foo/element-templates/hybrid/foo-hybrid.json"),
        read(
            """
        { "groups": [{"id":"b"}], "properties": [] }
        """));
    List<Finding> findings = rule.apply(templates);
    assertThat(findings).hasSize(2);
    assertThat(findings).extracting(Finding::jsonPointer).containsExactly("/groups", "/groups");
  }

  private static JsonNode read(String json) throws Exception {
    return MAPPER.readTree(json);
  }
}
