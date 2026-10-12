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
package io.camunda.connector.e2e.agenticai.aiagent.subprocess;

import static io.camunda.connector.e2e.agenticai.aiagent.DocumentFlowAssertions.assertNoDocumentInternalsInLlmRequests;
import static io.camunda.connector.e2e.agenticai.aiagent.DocumentFlowAssertions.toolParameters;
import static io.camunda.connector.e2e.agenticai.aiagent.DocumentFlowFixtures.DOWNLOAD_CALL_ID;
import static io.camunda.connector.e2e.agenticai.aiagent.DocumentFlowFixtures.FINAL_TEXT;
import static io.camunda.connector.e2e.agenticai.aiagent.DocumentFlowFixtures.documentIdOfToolResult;
import static io.camunda.connector.e2e.agenticai.aiagent.DocumentFlowFixtures.downloadTurn;
import static io.camunda.connector.e2e.agenticai.aiagent.DocumentFlowFixtures.finalTurn;
import static io.camunda.connector.e2e.agenticai.aiagent.wiremock.DocumentIdPlaceholderTransformer.documentId;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.connector.agenticai.aiagent.model.tool.DocumentParameterSchema;
import io.camunda.connector.e2e.ZeebeTest;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.openai.OpenAiCompletionsChatModelStubs;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.openai.OpenAiCompletionsChatModelStubs.ToolCall;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.openai.OpenAiCompletionsChatModelStubs.Turn;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.openai.OpenAiCompletionsRecordedConversation;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.openai.OpenAiCompletionsRecordedConversation.RecordedMessage;
import io.camunda.connector.e2e.agenticai.assertj.AgentSubProcessResponseAssert;
import io.camunda.connector.test.utils.annotation.SlowTest;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;

/**
 * A tool parameter that takes text or a document ({@code fromAi(..., schema: {"anyOf": [{"type":
 * "string"}, {"type": "document"}]})}): the model provider gets a plain string with the hint, a tag
 * of a known document reaches the tool as the Camunda document reference, and every other text
 * reaches it as text.
 */
@SlowTest
public class AgentSubProcessUnionDocumentParameterTests extends BaseAgentSubProcessTest {

  private static final String TAG_CALL_ID = "call_send_tag";
  private static final String TEXT_CALL_ID = "call_send_text";
  private static final String BARE_ID_CALL_ID = "call_send_id";
  private static final String UNKNOWN_CALL_ID = "call_send_unknown";
  private static final String RETRY_CALL_ID = "call_send_retry";

  @Value("classpath:agentic-ai-ahsp-connectors-documents-union.bpmn")
  private Resource unionProcess;

  @Override
  protected Resource testProcess() {
    return unionProcess;
  }

  @Test
  void theProviderGetsAPlainStringWithTheHintForAParameterThatTakesTextOrADocument()
      throws Exception {
    OpenAiCompletionsChatModelStubs.stubConversation(finalTurn());

    final var zeebeTest = awaitProcessCompletion(startProcess());

    final var rawRequest = OpenAiCompletionsRecordedConversation.recordedRawBodies().getFirst();
    final var body = toolParameters(rawRequest, "Send_Message").path("properties").path("body");
    assertThat(body.fieldNames()).toIterable().containsExactlyInAnyOrder("type", "description");
    assertThat(body.path("type").asText()).isEqualTo("string");
    assertThat(body.path("description").asText())
        .startsWith("The message body.")
        .endsWith(DocumentParameterSchema.UNION_HINT);
    assertThat(toolParameters(rawRequest, "Send_Message").path("required"))
        .extracting(n -> n.asText())
        .contains("body");

    // the persisted definition keeps the alternatives with the document format
    assertAgentResponse(
        zeebeTest,
        agentResponse -> {
          final var send =
              agentResponse.context().toolDefinitions().stream()
                  .filter(definition -> "Send_Message".equals(definition.name()))
                  .findFirst()
                  .orElseThrow();
          assertThat(send.inputSchema().toString())
              .contains("anyOf", "format=camunda-document-id")
              .contains("The message body. " + DocumentParameterSchema.UNION_HINT);
        });
    assertNoDocumentInternalsInLlmRequests();
  }

  @Test
  void theToolReceivesTheDocumentReferenceOfATagAndTextForEveryOtherValue() throws Exception {
    OpenAiCompletionsChatModelStubs.stubConversation(
        downloadTurn(wireMock.getHttpBaseUrl()),
        Turn.toolCalls(
            "I will send three messages.",
            10,
            20,
            ToolCall.of(
                TAG_CALL_ID,
                "Send_Message",
                "{\"body\": \"<doc id=\\\"%s\\\" />\"}".formatted(documentId(0))),
            ToolCall.of(TEXT_CALL_ID, "Send_Message", "{\"body\": \"plain text\"}"),
            ToolCall.of(
                BARE_ID_CALL_ID, "Send_Message", "{\"body\": \"%s\"}".formatted(documentId(0)))),
        finalTurn());

    final var zeebeTest = awaitProcessCompletion(startProcess());

    final var recorded = OpenAiCompletionsRecordedConversation.recorded();
    assertThat(recorded.modelCallCount()).isEqualTo(3);
    final var id = documentIdOfToolResult(recorded, DOWNLOAD_CALL_ID);

    final var messages = recorded.lastRequest().messages();
    // a tag is the document, text is text, also text that equals the id of a known document
    assertThat(toolResult(messages, TAG_CALL_ID)).contains("document:" + id);
    assertThat(toolResult(messages, TEXT_CALL_ID)).contains("text:plain text");
    assertThat(toolResult(messages, BARE_ID_CALL_ID)).contains("text:" + id);

    assertAgentResponse(
        zeebeTest,
        agentResponse ->
            AgentSubProcessResponseAssert.assertThat(agentResponse)
                .isReady()
                .hasResponseText(FINAL_TEXT));
    assertNoDocumentInternalsInLlmRequests();
  }

  @Test
  void aTagWithAnUnknownIdIsAnsweredWithTheKnownDocumentsAndTheModelRetriesWithText()
      throws Exception {
    OpenAiCompletionsChatModelStubs.stubConversation(
        downloadTurn(wireMock.getHttpBaseUrl()),
        Turn.toolCalls(
            "I will send a message.",
            10,
            20,
            ToolCall.of(
                UNKNOWN_CALL_ID, "Send_Message", "{\"body\": \"<doc id=\\\"gone\\\" />\"}")),
        Turn.toolCalls(
            "I will send the text instead.",
            10,
            20,
            ToolCall.of(RETRY_CALL_ID, "Send_Message", "{\"body\": \"see the report\"}")),
        finalTurn());

    final var zeebeTest = awaitProcessCompletion(startProcess());

    final var recorded = OpenAiCompletionsRecordedConversation.recorded();
    assertThat(recorded.modelCallCount()).isEqualTo(4);
    final var id = documentIdOfToolResult(recorded, DOWNLOAD_CALL_ID);

    // the call is answered in the loop and never reaches the tool
    assertThat(toolResult(recorded.requests().get(2).messages(), UNKNOWN_CALL_ID))
        .contains("Parameter 'body' must be the exact id of a known document")
        .contains("got 'gone'")
        .contains("Known documents:")
        .contains(id);
    assertThat(toolResult(recorded.lastRequest().messages(), RETRY_CALL_ID))
        .contains("text:see the report");

    assertAgentResponse(
        zeebeTest,
        agentResponse ->
            AgentSubProcessResponseAssert.assertThat(agentResponse)
                .isReady()
                .hasResponseText(FINAL_TEXT));
  }

  private static String toolResult(java.util.List<RecordedMessage> messages, String toolCallId) {
    return messages.stream()
        .filter(message -> toolCallId.equals(message.toolCallId()))
        .findFirst()
        .orElseThrow(() -> new AssertionError("No tool result for call " + toolCallId))
        .content();
  }

  private ZeebeTest startProcess() throws Exception {
    return createProcessInstance(
        e -> e, Map.of("userPrompt", "Download the file and send it as a message."));
  }
}
