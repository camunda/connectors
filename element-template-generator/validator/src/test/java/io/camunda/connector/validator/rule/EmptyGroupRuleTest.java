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

class EmptyGroupRuleTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final Path FILE = Path.of("test.json");
  private final EmptyGroupRule rule = new EmptyGroupRule();

  @Test
  void everyGroupReferenced_noFindings() throws Exception {
    JsonNode template =
        read(
            """
        {
          "groups": [ {"id":"a"}, {"id":"b"} ],
          "properties": [ { "group": "a" }, { "group": "b" } ]
        }
        """);
    assertThat(rule.apply(FILE, template)).isEmpty();
  }

  @Test
  void unreferencedGroup_oneFinding() throws Exception {
    JsonNode template =
        read(
            """
        {
          "groups": [ {"id":"a"}, {"id":"orphan"} ],
          "properties": [ { "group": "a" } ]
        }
        """);
    List<Finding> findings = rule.apply(FILE, template);
    assertThat(findings).hasSize(1);
    assertThat(findings.get(0).jsonPointer()).isEqualTo("/groups/1/id");
    assertThat(findings.get(0).message()).contains("orphan");
  }

  private static JsonNode read(String json) throws Exception {
    return MAPPER.readTree(json);
  }
}
