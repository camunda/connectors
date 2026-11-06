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

class DoubleWordRuleTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final Path OPTED_IN = Path.of("connectors/agentic-ai/element-templates/t.json");
  private final DoubleWordRule rule = new DoubleWordRule();

  @Test
  void flagsRepeatedWordsIgnoringCase() throws Exception {
    var template =
        MAPPER.readTree(
            """
            {
              "properties": [
                {"description": "The text to to be included."},
                {"tooltip": "The the model."},
                {"tooltip": "To top it off."}
              ]
            }
            """);

    List<Finding> findings = rule.apply(OPTED_IN, template);

    assertThat(findings)
        .extracting(Finding::jsonPointer)
        .containsExactly("/properties/0/description", "/properties/1/tooltip");
    assertThat(findings.get(0).message()).contains("to to");
  }
}
