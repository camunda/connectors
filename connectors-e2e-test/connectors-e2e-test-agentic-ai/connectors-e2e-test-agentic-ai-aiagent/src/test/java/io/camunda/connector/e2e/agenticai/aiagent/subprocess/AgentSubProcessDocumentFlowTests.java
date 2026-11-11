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

import static io.camunda.connector.e2e.agenticai.aiagent.AgentInstanceHistoryAudit.assertHistoryCoversEveryModelRequest;
import static io.camunda.connector.e2e.agenticai.aiagent.AgentInstanceHistoryAudit.awaitCommittedHistory;
import static io.camunda.connector.e2e.agenticai.aiagent.DocumentFlowAssertions.assertJson;
import static io.camunda.connector.e2e.agenticai.aiagent.DocumentFlowAssertions.assertNoDocumentInternalsInLlmRequests;
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
import static io.camunda.connector.e2e.agenticai.aiagent.DocumentFlowFixtures.handle;
import static io.camunda.connector.e2e.agenticai.aiagent.DocumentFlowFixtures.servedFile;
import static io.camunda.connector.e2e.agenticai.aiagent.DocumentFlowFixtures.stubUploadEndpoint;
import static io.camunda.connector.e2e.agenticai.aiagent.DocumentFlowFixtures.unknownUploadWithPingTurn;
import static io.camunda.connector.e2e.agenticai.aiagent.DocumentFlowFixtures.uploadCount;
import static io.camunda.connector.e2e.agenticai.aiagent.DocumentFlowFixtures.uploadTurn;
import static io.camunda.connector.e2e.agenticai.aiagent.DocumentFlowFixtures.uploadUrl;
import static io.camunda.connector.e2e.agenticai.aiagent.ToolCallResultDocumentAssertions.assertDocumentContentBlockJson;
import static io.camunda.connector.e2e.agenticai.aiagent.ToolCallResultDocumentAssertions.assertDocumentContentUserMessage;
import static io.camunda.connector.e2e.agenticai.aiagent.ToolCallResultDocumentAssertions.contentAlreadyInConversationSentence;
import static io.camunda.connector.e2e.agenticai.aiagent.ToolCallResultDocumentAssertions.contentBlockType;
import static io.camunda.connector.e2e.agenticai.aiagent.ToolCallResultDocumentAssertions.contentFollowsSentence;
import static io.camunda.connector.e2e.agenticai.aiagent.ToolCallResultDocumentAssertions.mimeType;
import static io.camunda.connector.e2e.agenticai.aiagent.ToolCallResultDocumentAssertions.parseDocumentHandle;
import static io.camunda.connector.e2e.agenticai.aiagent.wiremock.DocumentIdPlaceholderTransformer.documentId;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.client.api.command.AgentInstanceHistoryContent;
import io.camunda.client.api.search.enums.AgentInstanceHistoryRole;
import io.camunda.client.api.search.response.AgentInstanceHistory;
import io.camunda.connector.agenticai.aiagent.model.tool.DocumentParameter;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolDefinition;
import io.camunda.connector.api.document.DocumentCreationRequest;
import io.camunda.connector.e2e.ElementTemplate;
import io.camunda.connector.e2e.agenticai.aiagent.ToolCallResultDocumentAssertions.DocumentWithContent;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.openai.OpenAiCompletionsChatModelStubs;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.openai.OpenAiCompletionsChatModelStubs.ToolCall;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.openai.OpenAiCompletionsChatModelStubs.Turn;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.openai.OpenAiCompletionsRecordedConversation;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.openai.OpenAiCompletionsRecordedConversation.RecordedChatRequest;
import io.camunda.connector.e2e.agenticai.assertj.AgentSubProcessResponseAssert;
import io.camunda.connector.runtime.core.document.store.InMemoryDocumentStore;
import io.camunda.connector.test.utils.annotation.SlowTest;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Function;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;

/**
 * Document flow of the AI Agent Sub-process against a scripted model: a document parameter of a
 * tool (a real HTTP connector that uploads the document), loading a document, unknown document
 * handles, the window and the documents of tool results in the history.
 */
@SlowTest
public class AgentSubProcessDocumentFlowTests extends BaseAgentSubProcessTest {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  @Value("classpath:agentic-ai-ahsp-connectors-documents.bpmn")
  private Resource documentsProcess;

  @Value("classpath:agentic-ai-ahsp-connectors-documents-results.bpmn")
  private Resource resultDocumentsProcess;

  @Override
  protected Resource testProcess() {
    return documentsProcess;
  }

  @BeforeEach
  void setUpDocumentFlow() {
    stubUploadEndpoint();
  }

  @Test
  void toolDefinitionHasTheHandleSchemaForTheDocumentParameterAndTheProviderGetsItAsItIs()
      throws Exception {
    OpenAiCompletionsChatModelStubs.stubConversation(finalTurn());

    final var zeebeTest = awaitProcessCompletion(startProcess(e -> e));

    // the model provider sees the object schema of a document handle
    final var recorded = OpenAiCompletionsRecordedConversation.recorded();
    final var upload = toolDefinition(recorded.lastRequest(), "Upload_A_File");
    assertThat(upload.inputSchema())
        .extractingByKey("properties")
        .asInstanceOf(InstanceOfAssertFactories.MAP)
        .containsEntry("document", DocumentParameter.documentSchema("The document to upload."));
    assertThat(upload.inputSchema())
        .extractingByKey("required")
        .asInstanceOf(InstanceOfAssertFactories.LIST)
        .contains("document");

    // the persisted tool definition holds the same schema
    assertAgentResponse(
        zeebeTest,
        agentResponse -> {
          final var persistedUpload =
              agentResponse.context().toolDefinitions().stream()
                  .filter(definition -> "Upload_A_File".equals(definition.name()))
                  .findFirst()
                  .orElseThrow();
          assertThat(persistedUpload.inputSchema())
              .extractingByKey("properties")
              .asInstanceOf(InstanceOfAssertFactories.MAP)
              .containsEntry(
                  "document", DocumentParameter.documentSchema("The document to upload."));
        });

    // the built-in tool is offered next to the tools of the process
    assertThat(recorded.lastRequest().toolNames())
        .containsExactlyInAnyOrder(
            "Download_A_File", "Upload_A_File", "Ping", "Slow_Tool", "c8__load_document");
    assertNoDocumentInternalsInLlmRequests(recorded.providerNeutralRequests());
  }

  /**
   * A tool returns a document, the model loads it twice and passes its handle to a tool: the first
   * load puts the content next to the handle, the second one is answered without loading again, and
   * the tool receives the real bytes. The content is a text for a text document, an image for an
   * image and a file for a PDF. The history covers every message the model received.
   */
  @ParameterizedTest(name = "{0}")
  @ValueSource(
      strings = {
        "test.csv",
        "test.gif",
        "test.jpg",
        "test.json",
        "test.pdf",
        "test.png",
        "test.txt",
        "test.webp",
        "test.xml",
        "test.yaml"
      })
  void loadsAFetchedDocumentOnceAndPassesItsHandleToATool(String file) throws Exception {
    final var turns =
        List.of(
            downloadTurn(wireMock.getHttpBaseUrl(), file),
            getDocumentTurn(GET_CALL_ID, documentId(0)),
            getDocumentTurn(GET_AGAIN_CALL_ID, documentId(0)),
            uploadTurn(),
            finalTurn());
    OpenAiCompletionsChatModelStubs.stubConversation(turns.toArray(Turn[]::new));

    final var zeebeTest = awaitProcessCompletion(startProcess(e -> e));

    final var recorded = OpenAiCompletionsRecordedConversation.recorded();
    assertThat(recorded.modelCallCount()).isEqualTo(turns.size());
    assertContentReturnedOnceAndPassedByIdFlow(
        recorded.requests(),
        block -> assertDocumentContentBlockJson(block, contentBlockType(file), mimeType(file)));
    assertUploaded(servedFile(file));

    final var agentInstanceKey = new AtomicLong();
    assertAgentResponse(
        zeebeTest,
        agentResponse -> {
          AgentSubProcessResponseAssert.assertThat(agentResponse)
              .isReady()
              .hasResponseText(FINAL_TEXT);
          agentInstanceKey.set(agentResponse.context().metadata().agentInstanceKey().value());
        });
    assertNoDocumentInternalsInLlmRequests(recorded.providerNeutralRequests());
    assertHistoryCoversEveryModelRequest(
        awaitCommittedHistory(camundaClient, agentInstanceKey.get()),
        recorded.providerNeutralRequests());
  }

  /**
   * Two tools return the attached document: one as the whole result, one nested in an object (and
   * twice in it). The history shows the document of the first result as its handle and the document
   * and the second result as the object with the handles nested in it followed by the document
   * once.
   */
  @Test
  void everyModelVisibleMessageOfToolResultDocumentsIsInTheHistory() throws Exception {
    OpenAiCompletionsChatModelStubs.stubConversation(
        Turn.toolCalls(
            "I will fetch the file and describe it.",
            10,
            20,
            ToolCall.of("call_fetch", "Fetch_A_File", "{}"),
            ToolCall.of("call_describe", "Describe_A_File", "{}")),
        finalTurn());

    final var agentInstanceKey = runWithPromptDocument(resultDocumentsProcess);

    final var recorded = OpenAiCompletionsRecordedConversation.recorded();
    final var history = awaitCommittedHistory(camundaClient, agentInstanceKey);
    assertHistoryCoversEveryModelRequest(history, recorded.providerNeutralRequests());

    final var fetched = toolResultOf(history, "call_fetch");
    final var described = toolResultOf(history, "call_describe");
    final var promptDocumentHandle = promptDocumentHandleInRequest(recorded);
    assertThat(fetched.getContent()).hasSize(2);
    assertThat(fetched.getContent().get(0))
        .isInstanceOfSatisfying(
            AgentInstanceHistoryContent.ObjectContent.class,
            object -> assertThat(object.getObject()).isEqualTo(promptDocumentHandle));
    assertThat(fetched.getContent().get(1))
        .isInstanceOf(AgentInstanceHistoryContent.DocumentContent.class);

    assertThat(described.getContent()).hasSize(2);
    assertThat(described.getContent().get(0))
        .isInstanceOfSatisfying(
            AgentInstanceHistoryContent.ObjectContent.class,
            object ->
                assertThat(object.getObject())
                    .isEqualTo(
                        Map.of(
                            "status",
                            "found",
                            "file",
                            promptDocumentHandle,
                            "related",
                            List.of(promptDocumentHandle))));
    assertThat(described.getContent().get(1))
        .isInstanceOf(AgentInstanceHistoryContent.DocumentContent.class);
  }

  /**
   * The call with the unknown document handle gets its error result, the valid call of the same
   * batch runs, and the model corrects the handle with one more call.
   */
  @Test
  void mixedBatchWithAnUnknownDocumentHandleRunsTheValidCallAndAnswersTheInvalidOne()
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
            "Parameter 'document' has the id 'doc_000000000000', but the conversation has no document"
                + " with this id. Pass the handle of a document from the conversation: a JSON object"
                + " such as {\"type\":\"camunda.agenticai.document\",\"id\":\"doc_…\"}.");
    assertThat(results.get(1).content()).isEqualTo("pong");

    // only the retry with the handle of the downloaded document uploads
    assertThat(uploadCount()).isEqualTo(1);
    assertUploaded(servedFile(DOWNLOADED_FILE));

    assertAgentResponse(
        zeebeTest,
        agentResponse -> {
          AgentSubProcessResponseAssert.assertThat(agentResponse)
              .isReady()
              .hasResponseText(FINAL_TEXT);
        });
    assertNoDocumentInternalsInLlmRequests(recorded.providerNeutralRequests());
  }

  @Test
  void documentOfAnEvictedMessageStaysPassable() throws Exception {
    OpenAiCompletionsChatModelStubs.stubConversation(
        downloadTurn(wireMock.getHttpBaseUrl()),
        // the model keeps the handle in its own text, which stays in the window
        Turn.toolCalls(
            "I downloaded " + handle(documentId(0)) + ". Let me check that the tools work.",
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

    // the model passed the handle of the evicted document and the tool still got the real bytes
    assertUploaded(servedFile(DOWNLOADED_FILE));
    assertNoDocumentInternalsInLlmRequests(recorded.providerNeutralRequests());
  }

  /** The turns of {@link #loadsAFetchedDocumentOnceAndPassesItsHandleToATool}, by position. */
  private static final int DOWNLOAD = 0;

  private static final int FIRST_LOAD = 1;
  private static final int SECOND_LOAD = 2;
  private static final int UPLOAD = 3;

  /** The messages of the request that carries the result of the turn. */
  private static List<OpenAiCompletionsRecordedConversation.RecordedMessage> messagesAfter(
      List<RecordedChatRequest> requests, int turn) {
    return requests.get(turn + 1).messages();
  }

  /**
   * Asserts the requests of the flow download, {@code c8__load_document}, {@code c8__load_document}
   * again, upload, final answer: the download shows the handle only, the first load adds the
   * content in a message after the tool result, the second load is answered without a message, and
   * the upload receives the id.
   *
   * @param contentBlockAssertion the check of the wire form of the document content
   */
  private static void assertContentReturnedOnceAndPassedByIdFlow(
      List<RecordedChatRequest> requests, Consumer<JsonNode> contentBlockAssertion) {
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
    assertJson(afterUpload.getLast().content(), "{\"status\":200}");
  }

  private static ToolDefinition toolDefinition(RecordedChatRequest request, String name) {
    final var matches =
        request.toolDefinitions().stream().filter(tool -> name.equals(tool.name())).toList();
    assertThat(matches).as("tool '%s' in request", name).hasSize(1);
    return matches.getFirst();
  }

  private static AgentInstanceHistory toolResultOf(
      List<AgentInstanceHistory> history, String toolCallId) {
    return history.stream()
        .filter(item -> item.getRole() == AgentInstanceHistoryRole.TOOL_RESULT)
        .filter(item -> toolCallId.equals(item.getToolCalls().getFirst().getToolCallId()))
        .findFirst()
        .orElseThrow();
  }

  /** The handle of the prompt document as the model read it in the result of the first tool. */
  private static Map<String, Object> promptDocumentHandleInRequest(
      OpenAiCompletionsRecordedConversation recorded) {
    final var result =
        recorded.lastRequest().messages().stream()
            .filter(message -> "call_fetch".equals(message.toolCallId()))
            .findFirst()
            .orElseThrow();
    try {
      return OBJECT_MAPPER.readValue(result.content(), new TypeReference<>() {});
    } catch (Exception e) {
      throw new IllegalStateException(
          "The result of the tool is no handle: " + result.content(), e);
    }
  }

  /**
   * Runs the process with a prompt document and returns the key of the agent instance. The tools of
   * the process return the prompt document.
   */
  private long runWithPromptDocument(Resource process) throws Exception {
    final var promptDocument =
        InMemoryDocumentStore.INSTANCE.createDocument(
            DocumentCreationRequest.from("the prompt document".getBytes())
                .fileName("prompt.txt")
                .contentType("text/plain")
                .build());
    final Map<String, Object> reference =
        Map.of(
            "camunda.document.type", "camunda",
            "storeId", promptDocument.getStoreId(),
            "documentId", promptDocument.getDocumentId(),
            "contentHash", promptDocument.getContentHash(),
            "metadata", Map.of("fileName", "prompt.txt", "contentType", "text/plain"));

    final var zeebeTest =
        awaitProcessCompletion(
            createProcessInstance(
                process,
                e -> e,
                Map.of(
                    "userPrompt",
                    "Look at the attached file and give me back.",
                    "downloadedFiles",
                    List.of(reference),
                    "uploadUrl",
                    uploadUrl(wireMock.getHttpBaseUrl()))));

    final var agentInstanceKey = new AtomicLong();
    assertAgentResponse(
        zeebeTest,
        agentResponse ->
            agentInstanceKey.set(agentResponse.context().metadata().agentInstanceKey().value()));
    return agentInstanceKey.get();
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
