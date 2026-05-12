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

class TaskDefinitionBindingFormRuleTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final Path FILE = Path.of("template.json");
  private final TaskDefinitionBindingFormRule rule = new TaskDefinitionBindingFormRule();

  @Test
  void canonicalForm_noFindings() throws Exception {
    JsonNode t =
        read(
            "{ \"properties\": [{ \"type\": \"Hidden\", \"value\": \"io.camunda:foo:1\","
                + " \"binding\": { \"type\": \"zeebe:taskDefinition\", \"property\": \"type\" } }] }");
    assertThat(rule.apply(FILE, t)).isEmpty();
  }

  @Test
  void legacyForm_oneFinding() throws Exception {
    JsonNode t =
        read(
            "{ \"properties\": [{ \"type\": \"Hidden\", \"value\": \"io.camunda:foo:1\","
                + " \"binding\": { \"type\": \"zeebe:taskDefinition:type\" } }] }");
    List<Finding> findings = rule.apply(FILE, t);
    assertThat(findings).hasSize(1);
    assertThat(findings.get(0).jsonPointer()).isEqualTo("/properties/0/binding/type");
    assertThat(findings.get(0).message())
        .contains("zeebe:taskDefinition:type")
        .contains("zeebe:taskDefinition");
  }

  @Test
  void multipleLegacyOccurrences_multipleFindings() throws Exception {
    JsonNode t =
        read(
            "{ \"properties\": ["
                + "{ \"binding\": { \"type\": \"zeebe:taskDefinition:type\" } },"
                + "{ \"binding\": { \"type\": \"zeebe:input\", \"name\": \"x\" } },"
                + "{ \"binding\": { \"type\": \"zeebe:taskDefinition:type\" } } ] }");
    List<Finding> findings = rule.apply(FILE, t);
    assertThat(findings).hasSize(2);
    assertThat(findings.get(0).jsonPointer()).isEqualTo("/properties/0/binding/type");
    assertThat(findings.get(1).jsonPointer()).isEqualTo("/properties/2/binding/type");
  }

  @Test
  void otherBindingTypes_ignored() throws Exception {
    JsonNode t =
        read(
            "{ \"properties\": ["
                + "{ \"binding\": { \"type\": \"zeebe:input\", \"name\": \"x\" } },"
                + "{ \"binding\": { \"type\": \"zeebe:property\", \"name\": \"y\" } },"
                + "{ \"binding\": { \"type\": \"zeebe:taskHeader\", \"key\": \"k\" } } ] }");
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
