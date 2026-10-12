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

import static io.camunda.connector.e2e.agenticai.aiagent.DocumentFlowFixtures.DOWNLOAD_CALL_ID;
import static io.camunda.connector.e2e.agenticai.aiagent.DocumentFlowFixtures.GET_AGAIN_CALL_ID;
import static io.camunda.connector.e2e.agenticai.aiagent.DocumentFlowFixtures.GET_CALL_ID;
import static io.camunda.connector.e2e.agenticai.aiagent.DocumentFlowFixtures.UPLOAD_CALL_ID;
import static io.camunda.connector.e2e.agenticai.aiagent.ToolCallResultDocumentAssertions.assertLoadedDocumentsUserMessage;
import static io.camunda.connector.e2e.agenticai.aiagent.ToolCallResultDocumentAssertions.parseDocumentTag;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.e2e.agenticai.aiagent.ToolCallResultDocumentAssertions.LoadedDocument;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.openai.OpenAiCompletionsRecordedConversation;
import java.util.List;
import java.util.function.Consumer;
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
    assertThat(body.path("tools").toString()).doesNotContain("camunda-document-id");
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

  /**
   * Asserts the requests of the flow download, {@code c8__get_document}, {@code c8__get_document}
   * again, upload, final answer: the download shows the tag only, the first load adds the tag and
   * the content in a message after the tool result, the second load is answered without a message,
   * and the upload receives the id.
   *
   * @param contentBlockAssertion the check of the wire form of the document content
   */
  public static void assertLoadedOnceAndPassedByIdFlow(
      OpenAiCompletionsRecordedConversation recorded, Consumer<JsonNode> contentBlockAssertion) {
    assertThat(recorded.modelCallCount()).isEqualTo(5);
    final var requests = recorded.requests();

    // the download shows the tag only
    final var afterDownload = requests.get(1).messages();
    assertThat(afterDownload)
        .extracting(m -> m.role())
        .containsExactly("system", "user", "assistant", "tool");
    assertThat(afterDownload.get(3).toolCallId()).isEqualTo(DOWNLOAD_CALL_ID);
    final var tag = parseDocumentTag(afterDownload.get(3).content());

    // the first load: the tool result, then the message with the tag and the content
    final var afterLoad = requests.get(2).messages();
    assertThat(afterLoad)
        .extracting(m -> m.role())
        .containsExactly("system", "user", "assistant", "tool", "assistant", "tool", "user");
    assertThat(afterLoad.get(5).toolCallId()).isEqualTo(GET_CALL_ID);
    assertThat(afterLoad.get(5).content())
        .startsWith("Loaded the document <doc id=\"" + tag.id() + "\"")
        .endsWith(" />. Its content follows in the next message.");
    assertLoadedDocumentsUserMessage(
        afterLoad.get(6),
        LoadedDocument.forToolCall(
            DOWNLOAD_CALL_ID, "Download_A_File", tag, contentBlockAssertion));

    // the second load adds nothing
    final var afterSecondLoad = requests.get(3).messages();
    assertThat(afterSecondLoad).hasSize(9);
    assertThat(afterSecondLoad.get(8).role()).isEqualTo("tool");
    assertThat(afterSecondLoad.get(8).toolCallId()).isEqualTo(GET_AGAIN_CALL_ID);
    assertThat(afterSecondLoad.get(8).content())
        .isEqualTo("Document " + tag.id() + " is already in the conversation.");

    // the tool received the id and the content is in the conversation once
    final var afterUpload = requests.get(4).messages();
    assertThat(afterUpload.get(10).toolCallId()).isEqualTo(UPLOAD_CALL_ID);
    assertThat(afterUpload.get(10).content()).contains("200");
    assertThat(afterUpload)
        .extracting(m -> m.role())
        .containsExactly(
            "system",
            "user",
            "assistant",
            "tool",
            "assistant",
            "tool",
            "user",
            "assistant",
            "tool",
            "assistant",
            "tool");
  }
}
