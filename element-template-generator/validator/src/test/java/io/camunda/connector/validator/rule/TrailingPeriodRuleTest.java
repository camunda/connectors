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

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.validator.core.Finding;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class TrailingPeriodRuleTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final Path OPTED_IN =
      Path.of("connectors/agentic-ai/connector-mcp/element-templates/t.json");
  private final TrailingPeriodRule rule = new TrailingPeriodRule();

  @Test
  void flagsDescriptionAndTooltipWithoutPeriod() throws Exception {
    var template =
        MAPPER.readTree(
            """
            {
              "groups": [ {"id": "g", "tooltip": "Group help"} ],
              "properties": [
                {"id": "a", "label": "No period", "description": "Ends here."},
                {"id": "b", "tooltip": "See <a href='x'>docs</a>"},
                {"id": "c", "tooltip": "<p>Wrapped.</p></div>"},
                {"id": "d", "description": "Is it?"}
              ]
            }
            """);

    List<Finding> findings = rule.apply(OPTED_IN, template);

    assertThat(findings)
        .extracting(Finding::jsonPointer)
        .containsExactly("/properties/1/tooltip", "/groups/0/tooltip");
  }

  @Test
  void skipsConnectorsThatHaveNotOptedIn() throws Exception {
    var template = MAPPER.readTree("{\"properties\": [{\"description\": \"No period\"}]}");

    assertThat(rule.apply(Path.of("connectors/http/element-templates/t.json"), template)).isEmpty();
  }
}
