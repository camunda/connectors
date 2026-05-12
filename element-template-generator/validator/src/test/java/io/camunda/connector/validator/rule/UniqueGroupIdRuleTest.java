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

class UniqueGroupIdRuleTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final Path FILE = Path.of("test.json");
  private final UniqueGroupIdRule rule = new UniqueGroupIdRule();

  @Test
  void allUnique_noFindings() throws Exception {
    JsonNode template =
        read(
            """
        { "groups": [ {"id":"a"}, {"id":"b"}, {"id":"c"} ] }
        """);
    assertThat(rule.apply(FILE, template)).isEmpty();
  }

  @Test
  void duplicate_findingPointsAtSecondOccurrence() throws Exception {
    JsonNode template =
        read(
            """
        { "groups": [ {"id":"a"}, {"id":"b"}, {"id":"a"} ] }
        """);
    List<Finding> findings = rule.apply(FILE, template);
    assertThat(findings).hasSize(1);
    Finding f = findings.get(0);
    assertThat(f.jsonPointer()).isEqualTo("/groups/2/id");
    assertThat(f.message()).contains("a").contains("/groups/0/id");
  }

  @Test
  void noGroupsArray_noFindings() throws Exception {
    JsonNode template = read("{}");
    assertThat(rule.apply(FILE, template)).isEmpty();
  }

  private static JsonNode read(String json) throws Exception {
    return MAPPER.readTree(json);
  }
}
