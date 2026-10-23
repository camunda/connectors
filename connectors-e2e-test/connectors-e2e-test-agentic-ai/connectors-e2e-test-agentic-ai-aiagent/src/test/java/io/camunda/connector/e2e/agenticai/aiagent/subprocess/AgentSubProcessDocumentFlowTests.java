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

import static io.camunda.connector.e2e.agenticai.aiagent.DocumentFlowAssertions.assertContentReturnedOnceAndPassedByIdFlow;
import static io.camunda.connector.e2e.agenticai.aiagent.DocumentFlowAssertions.assertNoDocumentInternalsInLlmRequests;
import static io.camunda.connector.e2e.agenticai.aiagent.DocumentFlowAssertions.toolParameters;
import static io.camunda.connector.e2e.agenticai.aiagent.DocumentFlowFixtures.DOWNLOADED_FILE;
import static io.camunda.connector.e2e.agenticai.aiagent.DocumentFlowFixtures.DOWNLOAD_CALL_ID;
import static io.camunda.connector.e2e.agenticai.aiagent.DocumentFlowFixtures.FINAL_TEXT;
import static io.camunda.connector.e2e.agenticai.aiagent.DocumentFlowFixtures.GET_AGAIN_CALL_ID;
import static io.camunda.connector.e2e.agenticai.aiagent.DocumentFlowFixtures.GET_CALL_ID;
import static io.camunda.connector.e2e.agenticai.aiagent.DocumentFlowFixtures.PING_CALL_ID;
import static io.camunda.connector.e2e.agenticai.aiagent.DocumentFlowFixtures.UPLOAD_CALL_ID;
import static io.camunda.connector.e2e.agenticai.aiagent.DocumentFlowFixtures.assertUploaded;
import static io.camunda.connector.e2e.agenticai.aiagent.DocumentFlowFixtures.downloadTurn;
import static io.camunda.connector.e2e.agenticai.aiagent.DocumentFlowFixtures.finalTurn;
import static io.camunda.connector.e2e.agenticai.aiagent.DocumentFlowFixtures.getDocumentTurn;
import static io.camunda.connector.e2e.agenticai.aiagent.DocumentFlowFixtures.servedFile;
import static io.camunda.connector.e2e.agenticai.aiagent.DocumentFlowFixtures.stubUploadEndpoint;
import static io.camunda.connector.e2e.agenticai.aiagent.DocumentFlowFixtures.unknownUploadWithPingTurn;
import static io.camunda.connector.e2e.agenticai.aiagent.DocumentFlowFixtures.uploadCount;
import static io.camunda.connector.e2e.agenticai.aiagent.DocumentFlowFixtures.uploadTurn;
import static io.camunda.connector.e2e.agenticai.aiagent.DocumentFlowFixtures.uploadUrl;
import static io.camunda.connector.e2e.agenticai.aiagent.ToolCallResultDocumentAssertions.assertDocumentContentBlockJson;
import static io.camunda.connector.e2e.agenticai.aiagent.wiremock.DocumentIdPlaceholderTransformer.documentId;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.connector.agenticai.aiagent.model.tool.DocumentParameterSchema;
import io.camunda.connector.e2e.ElementTemplate;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.openai.OpenAiCompletionsChatModelStubs;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.openai.OpenAiCompletionsChatModelStubs.Turn;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.openai.OpenAiCompletionsRecordedConversation;
import io.camunda.connector.e2e.agenticai.assertj.AgentSubProcessResponseAssert;
import io.camunda.connector.test.utils.annotation.SlowTest;
import java.util.Map;
import java.util.function.Function;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;

/**
 * Document flow of the AI Agent Sub-process against a scripted model: a document parameter of a
 * tool (a real HTTP connector that uploads the document), loading a document, unknown document tags
 * and the window.
 */
@SlowTest
public class AgentSubProcessDocumentFlowTests extends BaseAgentSubProcessTest {

  @Value("classpath:agentic-ai-ahsp-connectors-documents.bpmn")
  private Resource documentsProcess;

  @Override
  protected Resource testProcess() {
    return documentsProcess;
  }

  @BeforeEach
  void setUpDocumentFlow() {
    stubUploadEndpoint();
  }

  @Test
  void
      toolDefinitionMarksTheDocumentParameterWithTheDocumentFormatAndTheProviderGetsAStringWithAHint()
          throws Exception {
    OpenAiCompletionsChatModelStubs.stubConversation(finalTurn());

    final var zeebeTest = awaitProcessCompletion(startProcess(e -> e));

    // the model provider sees a string schema without the format and with the hint
    final var rawRequest = OpenAiCompletionsRecordedConversation.recordedRawBodies().getFirst();
    final var parameters = toolParameters(rawRequest, "Upload_A_File");
    final var document = parameters.path("properties").path("document");
    assertThat(document.path("type").asText()).isEqualTo("string");
    assertThat(document.has("format")).isFalse();
    assertThat(document.path("description").asText())
        .startsWith("The document to upload.")
        .endsWith(DocumentParameterSchema.HINT);
    assertThat(parameters.path("required")).extracting(n -> n.asText()).contains("document");

    // the persisted tool definition keeps the format and the description as authored: the hint is
    // added for the model provider only
    assertAgentResponse(
        zeebeTest,
        agentResponse -> {
          final var upload =
              agentResponse.context().toolDefinitions().stream()
                  .filter(definition -> "Upload_A_File".equals(definition.name()))
                  .findFirst()
                  .orElseThrow();
          assertThat(upload.inputSchema().toString())
              .contains("format=camunda-document-id")
              .contains("description=The document to upload.")
              .doesNotContain(DocumentParameterSchema.HINT);
        });

    // the built-in tool is offered next to the tools of the process
    assertThat(OpenAiCompletionsRecordedConversation.recorded().lastRequest().toolNames())
        .containsExactlyInAnyOrder(
            "Download_A_File", "Upload_A_File", "Ping", "Slow_Tool", "c8__get_document");
    assertNoDocumentInternalsInLlmRequests();
  }

  /**
   * A tool returns a document, the model loads it twice and passes its tag to a tool: the first
   * load puts the content next to the tag, the second one is answered without loading again, and
   * the tool receives the real bytes. The content is a text for a text document and a file for a
   * PDF.
   */
  @ParameterizedTest(name = "{0}")
  @ValueSource(strings = {"test.txt", "test.pdf"})
  void loadsAFetchedDocumentOnceAndPassesItsTagToATool(String file) throws Exception {
    OpenAiCompletionsChatModelStubs.stubConversation(
        downloadTurn(wireMock.getHttpBaseUrl(), file),
        getDocumentTurn(GET_CALL_ID, documentId(0)),
        getDocumentTurn(GET_AGAIN_CALL_ID, documentId(0)),
        uploadTurn(),
        finalTurn());

    final var zeebeTest = awaitProcessCompletion(startProcess(e -> e));

    assertContentReturnedOnceAndPassedByIdFlow(
        OpenAiCompletionsRecordedConversation.recorded(),
        block ->
            assertDocumentContentBlockJson(
                block,
                file.endsWith(".pdf") ? "base64" : "text",
                file.endsWith(".pdf") ? "application/pdf" : "text/plain"));
    assertUploaded(servedFile(file));

    assertAgentResponse(
        zeebeTest,
        agentResponse ->
            AgentSubProcessResponseAssert.assertThat(agentResponse)
                .isReady()
                .hasResponseText(FINAL_TEXT));
    assertNoDocumentInternalsInLlmRequests();
  }

  /**
   * The call with the unknown document tag gets its error result, the valid call of the same batch
   * runs, and the model corrects the tag with one more call.
   */
  @Test
  void mixedBatchWithAnUnknownDocumentTagRunsTheValidCallAndAnswersTheInvalidOne()
      throws Exception {
    OpenAiCompletionsChatModelStubs.stubConversation(
        downloadTurn(wireMock.getHttpBaseUrl()),
        unknownUploadWithPingTurn(),
        uploadTurn(),
        finalTurn());

    final var zeebeTest = awaitProcessCompletion(startProcess(e -> e));

    final var recorded = OpenAiCompletionsRecordedConversation.recorded();
    assertThat(recorded.modelCallCount()).isEqualTo(4);

    final var messages = recorded.requests().get(2).messages();
    final var results = messages.subList(messages.size() - 2, messages.size());
    assertThat(results)
        .extracting(m -> m.toolCallId())
        .containsExactly(UPLOAD_CALL_ID, PING_CALL_ID);
    assertThat(results.get(0).content())
        .isEqualTo(
            "Parameter 'document' must be the <doc /> tag of a known document, exactly as you read it, got '<doc id=\"no-such-document\" />'.");
    assertThat(results.get(1).content()).contains("pong");

    // only the retry with the tag of the downloaded document uploads
    assertThat(uploadCount()).isEqualTo(1);
    assertUploaded(servedFile(DOWNLOADED_FILE));

    assertAgentResponse(
        zeebeTest,
        agentResponse -> {
          AgentSubProcessResponseAssert.assertThat(agentResponse)
              .isReady()
              .hasResponseText(FINAL_TEXT);
        });
    assertNoDocumentInternalsInLlmRequests();
  }

  @Test
  void documentOfAnEvictedMessageStaysPassable() throws Exception {
    OpenAiCompletionsChatModelStubs.stubConversation(
        downloadTurn(wireMock.getHttpBaseUrl()),
        // the model keeps the tag in its own text, which stays in the window
        Turn.toolCalls(
            "I downloaded <doc id=\"" + documentId(0) + "\"/>. Let me check that the tools work.",
            10,
            20,
            OpenAiCompletionsChatModelStubs.ToolCall.of("call_ping", "Ping", "{}")),
        uploadTurn(),
        finalTurn());

    awaitProcessCompletion(startProcess(e -> e.property("data.memory.contextWindowSize", "=3")));

    final var recorded = OpenAiCompletionsRecordedConversation.recorded();
    assertThat(recorded.modelCallCount()).isEqualTo(4);

    // the window of the third request no longer holds the download or its tool result
    final var windowed = recorded.requests().get(2).messages();
    assertThat(windowed).extracting(m -> m.toolCallId()).doesNotContain(DOWNLOAD_CALL_ID);
    assertThat(windowed)
        .noneMatch(
            m -> m.contentParts().stream().anyMatch(p -> "file".equals(p.path("type").asText())));

    // the model passed the tag of the evicted document and the tool still got the real bytes
    assertUploaded(servedFile(DOWNLOADED_FILE));
    assertNoDocumentInternalsInLlmRequests();
  }

  private io.camunda.connector.e2e.ZeebeTest startProcess(
      Function<ElementTemplate, ElementTemplate> elementTemplateModifier) throws Exception {
    return createProcessInstance(
        elementTemplateModifier,
        Map.of(
            "userPrompt",
            "Download the file, upload it and give it back to me.",
            "uploadUrl",
            uploadUrl(wireMock.getHttpBaseUrl())));
  }
}
