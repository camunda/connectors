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
package io.camunda.connector.e2e.agenticai.aiagent;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.openai.OpenAiCompletionsRecordedConversation;
import java.util.List;
import java.util.stream.StreamSupport;

/** Assertions on the document flow that hold for every scenario. */
public final class DocumentFlowAssertions {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  private DocumentFlowAssertions() {}

  /**
   * Asserts that no request sent to the model carries a document reference field. The model only
   * ever sees document ids, never store ids or content hashes.
   */
  public static void assertNoDocumentInternalsInLlmRequests() {
    final var bodies = OpenAiCompletionsRecordedConversation.recordedRawBodies();
    assertThat(bodies).as("recorded LLM requests").isNotEmpty();
    for (int i = 0; i < bodies.size(); i++) {
      assertThat(bodies.get(i))
          .as("LLM request %d", i + 1)
          .doesNotContain("storeId", "contentHash", "camunda.document.type");
      assertNoDocumentTypeInToolSchemas(bodies.get(i), i + 1);
    }
  }

  /**
   * Asserts that no tool schema of the request carries the document marker: the model provider only
   * knows standard JSON schema types and formats.
   */
  private static void assertNoDocumentTypeInToolSchemas(String rawRequestBody, int requestNumber) {
    final JsonNode body;
    try {
      body = OBJECT_MAPPER.readTree(rawRequestBody);
    } catch (Exception e) {
      throw new IllegalStateException("Unparseable request body: " + rawRequestBody, e);
    }
    body.path("tools")
        .forEach(
            tool ->
                assertThat(
                        tool.path("function").path("parameters").findValues("type").stream()
                            .map(JsonNode::asText))
                    .as(
                        "schema types of tool '%s' in LLM request %d",
                        tool.path("function").path("name").asText(), requestNumber)
                    .doesNotContain("document"));
    assertThat(body.path("tools").toString()).doesNotContain("camunda-document");
  }

  /**
   * The {@code parameters} schema of the tool with the given name in the raw request body, as it
   * goes over the wire.
   */
  public static JsonNode toolParameters(String rawRequestBody, String toolName) {
    final JsonNode body;
    try {
      body = OBJECT_MAPPER.readTree(rawRequestBody);
    } catch (Exception e) {
      throw new IllegalStateException("Unparseable request body: " + rawRequestBody, e);
    }
    final List<JsonNode> matches =
        StreamSupport.stream(body.path("tools").spliterator(), false)
            .filter(tool -> toolName.equals(tool.path("function").path("name").asText()))
            .toList();
    assertThat(matches).as("tool '%s' in request", toolName).hasSize(1);
    return matches.getFirst().path("function").path("parameters");
  }
}
