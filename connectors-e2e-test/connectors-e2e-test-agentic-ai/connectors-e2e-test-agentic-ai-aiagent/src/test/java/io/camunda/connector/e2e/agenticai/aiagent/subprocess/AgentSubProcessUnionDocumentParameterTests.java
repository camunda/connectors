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

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.agenticai.aiagent.api.tool.DocumentSchemas;
import io.camunda.connector.agenticai.aiagent.model.document.AgentDocumentId;
import io.camunda.connector.api.document.DocumentReference.CamundaDocumentReference;
import io.camunda.connector.e2e.ZeebeTest;
import io.camunda.connector.e2e.agenticai.aiagent.DocumentFlowFixtures;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.openai.OpenAiCompletionsChatModelStubs;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.openai.OpenAiCompletionsChatModelStubs.ToolCall;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.openai.OpenAiCompletionsChatModelStubs.Turn;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.openai.OpenAiCompletionsRecordedConversation;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.openai.OpenAiCompletionsRecordedConversation.RecordedMessage;
import io.camunda.connector.e2e.agenticai.assertj.AgentSubProcessResponseAssert;
import io.camunda.connector.test.utils.annotation.SlowTest;
import java.util.Map;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;

/**
 * A tool parameter that takes text or a document ({@code fromAi(..., schema: {"anyOf": [{"type":
 * "string"}, {"type": "document"}]})}): the model provider gets the union of a string and the
 * schema of a document handle, the handle of a known document reaches the tool as the Camunda
 * document reference, and every string reaches it as text.
 */
@SlowTest
public class AgentSubProcessUnionDocumentParameterTests extends BaseAgentSubProcessTest {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  private static final String HANDLE_CALL_ID = "call_send_handle";
  private static final String TEXT_CALL_ID = "call_send_text";
  private static final String BARE_ID_CALL_ID = "call_send_id";

  @Value("classpath:agentic-ai-ahsp-connectors-documents-union.bpmn")
  private Resource unionProcess;

  @Override
  protected Resource testProcess() {
    return unionProcess;
  }

  @Test
  void theToolReceivesTheDocumentReferenceOfAHandleAndTextForEveryString() throws Exception {
    OpenAiCompletionsChatModelStubs.stubConversation(
        downloadTurn(wireMock.getHttpBaseUrl()),
        Turn.toolCalls(
            "I will send three messages.",
            10,
            20,
            ToolCall.of(
                HANDLE_CALL_ID,
                "Send_Message",
                "{\"body\": %s}".formatted(DocumentFlowFixtures.handle(documentId(0)))),
            ToolCall.of(TEXT_CALL_ID, "Send_Message", "{\"body\": \"plain text\"}"),
            ToolCall.of(
                BARE_ID_CALL_ID, "Send_Message", "{\"body\": \"%s\"}".formatted(documentId(0)))),
        finalTurn());

    final var zeebeTest = awaitProcessCompletion(startProcess());

    final var recorded = OpenAiCompletionsRecordedConversation.recorded();
    assertThat(recorded.modelCallCount()).isEqualTo(3);
    final var id = documentIdOfToolResult(recorded, DOWNLOAD_CALL_ID);

    final var messages = recorded.lastRequest().messages();
    // a handle object is the document, a string is text, also one that equals the id of a known
    // document
    assertThat(toolResult(messages, TEXT_CALL_ID)).contains("text:plain text");
    assertThat(toolResult(messages, BARE_ID_CALL_ID)).contains("text:" + id);

    // the model provider gets the union of a string and the schema of a document handle
    final var rawRequest = OpenAiCompletionsRecordedConversation.recordedRawBodies().getFirst();
    final var body = toolParameters(rawRequest, "Send_Message").path("properties").path("body");
    assertThat(body)
        .isEqualTo(OBJECT_MAPPER.valueToTree(DocumentSchemas.textOrDocument("The message body.")));

    assertAgentResponse(
        zeebeTest,
        agentResponse -> {
          AgentSubProcessResponseAssert.assertThat(agentResponse)
              .isReady()
              .hasResponseText(FINAL_TEXT);
          // the tool gets the Camunda document reference of the document the handle names
          final var camundaDocumentId =
              ((CamundaDocumentReference)
                      agentResponse
                          .context()
                          .documents()
                          .find(AgentDocumentId.of(id))
                          .orElseThrow()
                          .reference())
                  .getDocumentId();
          assertThat(toolResult(messages, HANDLE_CALL_ID))
              .contains("document:" + camundaDocumentId);
          // the persisted definition holds the same union
          final var send =
              agentResponse.context().toolDefinitions().stream()
                  .filter(definition -> "Send_Message".equals(definition.name()))
                  .findFirst()
                  .orElseThrow();
          assertThat(send.inputSchema())
              .extractingByKey("properties")
              .asInstanceOf(InstanceOfAssertFactories.MAP)
              .containsEntry("body", DocumentSchemas.textOrDocument("The message body."));
        });
    assertNoDocumentInternalsInLlmRequests();
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
