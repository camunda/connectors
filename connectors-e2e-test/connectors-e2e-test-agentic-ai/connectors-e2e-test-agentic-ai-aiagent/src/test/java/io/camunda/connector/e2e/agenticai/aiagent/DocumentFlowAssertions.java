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
import static io.camunda.connector.e2e.agenticai.aiagent.DocumentFlowFixtures.FINAL_TEXT;
import static io.camunda.connector.e2e.agenticai.aiagent.DocumentFlowFixtures.GET_AGAIN_CALL_ID;
import static io.camunda.connector.e2e.agenticai.aiagent.DocumentFlowFixtures.GET_CALL_ID;
import static io.camunda.connector.e2e.agenticai.aiagent.DocumentFlowFixtures.UPLOAD_CALL_ID;
import static io.camunda.connector.e2e.agenticai.aiagent.ToolCallResultDocumentAssertions.assertDocumentContentUserMessage;
import static io.camunda.connector.e2e.agenticai.aiagent.ToolCallResultDocumentAssertions.contentAlreadyInConversationSentence;
import static io.camunda.connector.e2e.agenticai.aiagent.ToolCallResultDocumentAssertions.contentFollowsSentence;
import static io.camunda.connector.e2e.agenticai.aiagent.ToolCallResultDocumentAssertions.parseDocumentHandle;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.AgentInstanceHistoryContent;
import io.camunda.client.api.search.enums.AgentInstanceHistoryRole;
import io.camunda.client.api.search.response.AgentInstanceHistory;
import io.camunda.connector.e2e.agenticai.aiagent.ToolCallResultDocumentAssertions.DocumentWithContent;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.openai.OpenAiCompletionsRecordedConversation;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.spi.RecordedChatRequest;
import io.camunda.connector.e2e.agenticai.assertj.AgentInstanceEngineVerifier;
import java.time.Duration;
import java.util.List;
import java.util.function.Consumer;
import org.json.JSONException;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;

/** Assertions on the document flow that hold for every scenario and every model provider. */
public final class DocumentFlowAssertions {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  /** The turns of the load flow, by position. */
  private static final int DOWNLOAD = 0;

  private static final int FIRST_LOAD = 1;
  private static final int SECOND_LOAD = 2;
  private static final int UPLOAD = 3;

  private DocumentFlowAssertions() {}

  /**
   * Asserts the requests of the flow download, {@code c8__load_document}, {@code c8__load_document}
   * again, upload, final answer: the download shows the handle only, the first load adds the
   * content in a message after the tool result, the second load is answered without a message, and
   * the upload receives the id.
   *
   * @param requests the recorded requests of the five turns
   * @param contentBlockAssertion the check of the wire form of the document content
   */
  public static void assertContentReturnedOnceAndPassedByIdFlow(
      List<OpenAiCompletionsRecordedConversation.RecordedChatRequest> requests,
      Consumer<JsonNode> contentBlockAssertion)
      throws JSONException {
    // the download shows the handle only
    final var afterDownload = messagesAfter(requests, DOWNLOAD);
    assertThat(afterDownload)
        .extracting(m -> m.role())
        .containsExactly("system", "user", "assistant", "tool");
    assertThat(afterDownload.getLast().toolCallId()).isEqualTo(DOWNLOAD_CALL_ID);
    final var handle = parseDocumentHandle(afterDownload.getLast().content());

    // the first load: the tool result, then the message with the handle and the content
    final var afterFirstLoad = messagesAfter(requests, FIRST_LOAD);
    assertThat(afterFirstLoad)
        .extracting(m -> m.role())
        .containsExactly("system", "user", "assistant", "tool", "assistant", "tool", "user");
    final var firstLoadResult = afterFirstLoad.get(afterFirstLoad.size() - 2);
    assertThat(firstLoadResult.toolCallId()).isEqualTo(GET_CALL_ID);
    assertThat(firstLoadResult.content()).isEqualTo(contentFollowsSentence(handle));
    assertDocumentContentUserMessage(
        afterFirstLoad.getLast(), DocumentWithContent.of(handle, contentBlockAssertion));

    // the second load adds nothing but its own result
    final var afterSecondLoad = messagesAfter(requests, SECOND_LOAD);
    assertThat(afterSecondLoad).hasSize(afterFirstLoad.size() + 2);
    assertThat(afterSecondLoad.getLast().role()).isEqualTo("tool");
    assertThat(afterSecondLoad.getLast().toolCallId()).isEqualTo(GET_AGAIN_CALL_ID);
    assertThat(afterSecondLoad.getLast().content())
        .isEqualTo(contentAlreadyInConversationSentence(handle));

    // the tool received the id and the content is in the conversation once
    final var afterUpload = messagesAfter(requests, UPLOAD);
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
    assertThat(afterUpload.getLast().toolCallId()).isEqualTo(UPLOAD_CALL_ID);
    JSONAssert.assertEquals(
        "{\"status\":200}", afterUpload.getLast().content(), JSONCompareMode.STRICT);
  }

  /**
   * Asserts that the committed history of the agent instance shows the result of the download tool
   * as the model read it: the object with the handle of the document nested in it, followed by the
   * document once.
   */
  public static void assertDownloadResultInHistory(
      CamundaClient client,
      long agentInstanceKey,
      List<OpenAiCompletionsRecordedConversation.RecordedChatRequest> requests)
      throws Exception {
    final var history = awaitCommittedHistory(client, agentInstanceKey);
    final var downloaded =
        history.stream()
            .filter(item -> item.getRole() == AgentInstanceHistoryRole.TOOL_RESULT)
            .filter(item -> DOWNLOAD_CALL_ID.equals(item.getToolCalls().getFirst().getToolCallId()))
            .findFirst()
            .orElseThrow();
    final var resultTheModelRead =
        OBJECT_MAPPER.readTree(messagesAfter(requests, DOWNLOAD).getLast().content());

    assertThat(downloaded.getContent()).hasSize(2);
    assertThat(downloaded.getContent().get(0))
        .isInstanceOfSatisfying(
            AgentInstanceHistoryContent.ObjectContent.class,
            object -> {
              final JsonNode reported = OBJECT_MAPPER.valueToTree(object.getObject());
              assertThat(reported).isEqualTo(resultTheModelRead);
            });
    assertThat(downloaded.getContent().get(1))
        .isInstanceOf(AgentInstanceHistoryContent.DocumentContent.class);
  }

  /** The committed history of the agent instance once it holds the final answer of the model. */
  private static List<AgentInstanceHistory> awaitCommittedHistory(
      CamundaClient client, long agentInstanceKey) {
    return await()
        .alias("committed agent instance history")
        .atMost(Duration.ofSeconds(30))
        .ignoreExceptions()
        .until(
            () -> AgentInstanceEngineVerifier.committedHistory(client, agentInstanceKey),
            items ->
                items.stream()
                    .anyMatch(item ->
                        item.getRole() == AgentInstanceHistoryRole.ASSISTANT
                            && item.getContent().stream()
                                .anyMatch(block ->
                                    block instanceof AgentInstanceHistoryContent.TextContent text
                                        && FINAL_TEXT.equals(text.getText()))));
  }

  /** The messages of the request that carries the result of the turn. */
  private static List<OpenAiCompletionsRecordedConversation.RecordedMessage> messagesAfter(
      List<OpenAiCompletionsRecordedConversation.RecordedChatRequest> requests, int turn) {
    return requests.get(turn + 1).messages();
  }

  /**
   * Asserts that no request sent to the model carries a document reference field. The model only
   * ever sees document ids, never store ids or content hashes. No tool schema of the requests
   * carries the document marker either: the model provider only knows standard JSON schema types
   * and formats.
   */
  public static void assertNoDocumentInternalsInLlmRequests(List<RecordedChatRequest> requests) {
    assertThat(requests).as("recorded LLM requests").isNotEmpty();
    for (int i = 0; i < requests.size(); i++) {
      final var requestNumber = i + 1;
      final var request = requests.get(i);
      assertThat(request.rawBody())
          .as("LLM request %d", requestNumber)
          .doesNotContain("storeId", "contentHash", "camunda.document.type");
      request
          .toolDefinitions()
          .forEach(tool -> {
            final var schema = OBJECT_MAPPER.valueToTree(tool.inputSchema());
            assertThat(schemaTypes(schema))
                .as("schema types of tool '%s' in LLM request %d", tool.name(), requestNumber)
                .doesNotContain("document");
            assertThat(schema.toString())
                .as("schema of tool '%s' in LLM request %d", tool.name(), requestNumber)
                .doesNotContain("camunda-document-id");
          });
    }
  }

  private static List<String> schemaTypes(JsonNode schema) {
    return schema.findValues("type").stream().map(JsonNode::asText).toList();
  }
}
