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

class UniquePropertyIdRuleTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final Path FILE = Path.of("template.json");
  private final UniquePropertyIdRule rule = new UniquePropertyIdRule();

  @Test
  void allUnique_noFindings() throws Exception {
    JsonNode t =
        read("{ \"properties\": [{ \"id\": \"a\" }, { \"id\": \"b\" }, { \"id\": \"c\" } ] }");
    assertThat(rule.apply(FILE, t)).isEmpty();
  }

  @Test
  void duplicateId_noConditions_oneFinding() throws Exception {
    JsonNode t =
        read("{ \"properties\": [{ \"id\": \"a\" }, { \"id\": \"b\" }, { \"id\": \"a\" } ] }");
    List<Finding> findings = rule.apply(FILE, t);
    assertThat(findings).hasSize(1);
    assertThat(findings.get(0).jsonPointer()).isEqualTo("/properties/2/id");
    assertThat(findings.get(0).message()).contains("\"a\"").contains("/properties/0/id");
  }

  @Test
  void tripleDuplicate_noConditions_twoFindings() throws Exception {
    JsonNode t =
        read("{ \"properties\": [{ \"id\": \"x\" }, { \"id\": \"x\" }, { \"id\": \"x\" } ] }");
    List<Finding> findings = rule.apply(FILE, t);
    assertThat(findings).hasSize(2);
    assertThat(findings.get(0).jsonPointer()).isEqualTo("/properties/1/id");
    assertThat(findings.get(1).jsonPointer()).isEqualTo("/properties/2/id");
  }

  @Test
  void mutuallyExclusiveDuplicates_equals_noFindings() throws Exception {
    JsonNode t =
        read(
            "{ \"properties\": ["
                + "{ \"id\": \"x\", \"condition\": { \"property\": \"op\", \"equals\": \"a\" } },"
                + "{ \"id\": \"x\", \"condition\": { \"property\": \"op\", \"equals\": \"b\" } } ] }");
    assertThat(rule.apply(FILE, t)).isEmpty();
  }

  @Test
  void mutuallyExclusiveDuplicates_oneOf_noFindings() throws Exception {
    JsonNode t =
        read(
            "{ \"properties\": ["
                + "{ \"id\": \"x\", \"condition\": { \"property\": \"op\", \"oneOf\": [\"a\", \"b\"] } },"
                + "{ \"id\": \"x\", \"condition\": { \"property\": \"op\", \"oneOf\": [\"c\"] } },"
                + "{ \"id\": \"x\", \"condition\": { \"property\": \"op\", \"oneOf\": [\"d\", \"e\"] } } ] }");
    assertThat(rule.apply(FILE, t)).isEmpty();
  }

  @Test
  void overlappingValues_flagged() throws Exception {
    JsonNode t =
        read(
            "{ \"properties\": ["
                + "{ \"id\": \"x\", \"condition\": { \"property\": \"op\", \"oneOf\": [\"a\", \"b\"] } },"
                + "{ \"id\": \"x\", \"condition\": { \"property\": \"op\", \"equals\": \"b\" } } ] }");
    List<Finding> findings = rule.apply(FILE, t);
    assertThat(findings).hasSize(1);
    assertThat(findings.get(0).jsonPointer()).isEqualTo("/properties/1/id");
  }

  @Test
  void differentGatingProperties_flagged() throws Exception {
    JsonNode t =
        read(
            "{ \"properties\": ["
                + "{ \"id\": \"x\", \"condition\": { \"property\": \"opA\", \"equals\": \"v\" } },"
                + "{ \"id\": \"x\", \"condition\": { \"property\": \"opB\", \"equals\": \"v\" } } ] }");
    List<Finding> findings = rule.apply(FILE, t);
    assertThat(findings).hasSize(1);
  }

  @Test
  void unconditionalDuplicateMixedWithConditional_flagged() throws Exception {
    JsonNode t =
        read(
            "{ \"properties\": ["
                + "{ \"id\": \"x\" },"
                + "{ \"id\": \"x\", \"condition\": { \"property\": \"op\", \"equals\": \"v\" } } ] }");
    List<Finding> findings = rule.apply(FILE, t);
    assertThat(findings).hasSize(1);
  }

  @Test
  void allMatchCompoundDisjoint_noFindings() throws Exception {
    // allMatch contains a disjoint sub-condition pair → mutually exclusive
    JsonNode t =
        read(
            "{ \"properties\": ["
                + "{ \"id\": \"x\", \"condition\": { \"allMatch\": ["
                + "  { \"property\": \"op\", \"equals\": \"a\" },"
                + "  { \"property\": \"foo\", \"equals\": \"1\" } ] } },"
                + "{ \"id\": \"x\", \"condition\": { \"allMatch\": ["
                + "  { \"property\": \"op\", \"equals\": \"b\" },"
                + "  { \"property\": \"foo\", \"equals\": \"1\" } ] } } ] }");
    assertThat(rule.apply(FILE, t)).isEmpty();
  }

  @Test
  void propertiesWithoutId_ignored() throws Exception {
    JsonNode t =
        read(
            "{ \"properties\": ["
                + "{ \"type\": \"Hidden\" }, { \"type\": \"Hidden\" }, { \"id\": \"a\" } ] }");
    assertThat(rule.apply(FILE, t)).isEmpty();
  }

  @Test
  void missingPropertiesArray_noFindings() throws Exception {
    assertThat(rule.apply(FILE, read("{}"))).isEmpty();
    assertThat(rule.apply(FILE, read("{ \"properties\": null }"))).isEmpty();
  }

  private static JsonNode read(String json) throws Exception {
    return MAPPER.readTree(json);
  }
}
