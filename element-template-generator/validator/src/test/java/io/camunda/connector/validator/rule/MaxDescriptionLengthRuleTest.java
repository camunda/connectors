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

class MaxDescriptionLengthRuleTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final Path OPTED_IN =
      Path.of("connectors/agentic-ai/connector-mcp/element-templates/t.json");
  private final MaxDescriptionLengthRule rule = new MaxDescriptionLengthRule();

  private static String repeat(int length) {
    return "a".repeat(length);
  }

  private static JsonNode descriptionTemplate(String description) {
    var template = MAPPER.createObjectNode();
    template.putArray("properties").addObject().put("id", "a").put("description", description);
    return template;
  }

  @Test
  void flagsDescriptionsOverTheLimitButNotTooltips() throws Exception {
    var template =
        MAPPER.readTree(
            """
            {
              "groups": [ {"id": "g", "description": "%s"} ],
              "properties": [
                {"id": "short", "description": "Within the limit."},
                {"id": "long", "description": "%s"},
                {"id": "longTooltip", "tooltip": "%s"}
              ]
            }
            """
                .formatted(repeat(101), repeat(101), repeat(200)));

    List<Finding> findings = rule.apply(OPTED_IN, template);

    assertThat(findings)
        .extracting(Finding::jsonPointer)
        .containsExactly("/properties/1/description", "/groups/0/description");
  }

  @Test
  void ignoresHtmlTagsAndEntitiesWhenCountingLength() throws Exception {
    // 95 visible characters wrapped in a link and <br> that push the raw string well over 100.
    String description =
        "<a href=\"https://example.com/a/very/long/url/that/should/not/count\">"
            + repeat(95)
            + "</a><br>&amp;";
    var template = MAPPER.createObjectNode();
    template.putArray("properties").addObject().put("id", "a").put("description", description);

    assertThat(rule.apply(OPTED_IN, template)).isEmpty();
  }

  @Test
  void acceptsExactLimitWhenLinkFormattingFollowsText() throws Exception {
    // Renders as exactly 100 characters: 96 + space + "AB" + ".".
    String description = repeat(96) + " <a href=\"https://example.com\">AB</a>.";

    assertThat(rule.apply(OPTED_IN, descriptionTemplate(description))).isEmpty();
  }

  @Test
  void countsEachEntityAsOneCharacter() throws Exception {
    // Renders as 100 characters, then 101 characters.
    assertThat(rule.apply(OPTED_IN, descriptionTemplate(repeat(98) + "&amp;&lt;"))).isEmpty();
    assertThat(rule.apply(OPTED_IN, descriptionTemplate(repeat(99) + "&amp;&lt;"))).hasSize(1);
  }

  @Test
  void skipsConnectorsThatHaveNotOptedIn() throws Exception {
    var template =
        MAPPER.readTree("{\"properties\": [{\"description\": \"" + repeat(200) + "\"}]}");

    assertThat(rule.apply(Path.of("connectors/http/element-templates/t.json"), template)).isEmpty();
  }
}
