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
import static io.camunda.connector.e2e.agenticai.aiagent.DocumentFlowAssertions.assertDownloadResultInHistory;
import static io.camunda.connector.e2e.agenticai.aiagent.DocumentFlowAssertions.assertNoDocumentInternalsInLlmRequests;
import static io.camunda.connector.e2e.agenticai.aiagent.DocumentFlowFixtures.COMPLEX_TOOL;
import static io.camunda.connector.e2e.agenticai.aiagent.DocumentFlowFixtures.COMPLEX_TOOL_CALL_ID;
import static io.camunda.connector.e2e.agenticai.aiagent.DocumentFlowFixtures.DOWNLOADED_FILE;
import static io.camunda.connector.e2e.agenticai.aiagent.DocumentFlowFixtures.DOWNLOAD_CALL_ID;
import static io.camunda.connector.e2e.agenticai.aiagent.DocumentFlowFixtures.FINAL_TEXT;
import static io.camunda.connector.e2e.agenticai.aiagent.DocumentFlowFixtures.GET_AGAIN_CALL_ID;
import static io.camunda.connector.e2e.agenticai.aiagent.DocumentFlowFixtures.GET_CALL_ID;
import static io.camunda.connector.e2e.agenticai.aiagent.DocumentFlowFixtures.RETURN_CALL_ID;
import static io.camunda.connector.e2e.agenticai.aiagent.DocumentFlowFixtures.RETURN_DESCRIPTION;
import static io.camunda.connector.e2e.agenticai.aiagent.DocumentFlowFixtures.RETURN_KEY;
import static io.camunda.connector.e2e.agenticai.aiagent.DocumentFlowFixtures.SUPERFLUX_CALL_ID;
import static io.camunda.connector.e2e.agenticai.aiagent.DocumentFlowFixtures.SUPERFLUX_RESULT;
import static io.camunda.connector.e2e.agenticai.aiagent.DocumentFlowFixtures.UPLOAD_CALL_ID;
import static io.camunda.connector.e2e.agenticai.aiagent.DocumentFlowFixtures.UPLOAD_TOOL;
import static io.camunda.connector.e2e.agenticai.aiagent.DocumentFlowFixtures.assertResponseDocument;
import static io.camunda.connector.e2e.agenticai.aiagent.DocumentFlowFixtures.assertUploaded;
import static io.camunda.connector.e2e.agenticai.aiagent.DocumentFlowFixtures.downloadTurn;
import static io.camunda.connector.e2e.agenticai.aiagent.DocumentFlowFixtures.finalTurn;
import static io.camunda.connector.e2e.agenticai.aiagent.DocumentFlowFixtures.getDocumentTurn;
import static io.camunda.connector.e2e.agenticai.aiagent.DocumentFlowFixtures.handle;
import static io.camunda.connector.e2e.agenticai.aiagent.DocumentFlowFixtures.returnDocumentWithComplexToolTurn;
import static io.camunda.connector.e2e.agenticai.aiagent.DocumentFlowFixtures.roundTripTurns;
import static io.camunda.connector.e2e.agenticai.aiagent.DocumentFlowFixtures.servedFile;
import static io.camunda.connector.e2e.agenticai.aiagent.DocumentFlowFixtures.stubUploadEndpoint;
import static io.camunda.connector.e2e.agenticai.aiagent.DocumentFlowFixtures.unknownUploadWithSuperfluxTurn;
import static io.camunda.connector.e2e.agenticai.aiagent.DocumentFlowFixtures.uploadCount;
import static io.camunda.connector.e2e.agenticai.aiagent.DocumentFlowFixtures.uploadTurn;
import static io.camunda.connector.e2e.agenticai.aiagent.DocumentFlowFixtures.uploadUrl;
import static io.camunda.connector.e2e.agenticai.aiagent.ToolCallResultDocumentAssertions.assertDocumentContentBlockJson;
import static io.camunda.connector.e2e.agenticai.aiagent.ToolCallResultDocumentAssertions.parseDocumentHandle;
import static io.camunda.connector.e2e.agenticai.aiagent.fixtures.AgentTestFixtures.AI_AGENT_ELEMENT_ID;
import static io.camunda.connector.e2e.agenticai.aiagent.wiremock.DocumentIdPlaceholderTransformer.documentId;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.connector.agenticai.aiagent.model.document.AgentDocumentHandle;
import io.camunda.connector.agenticai.aiagent.model.document.AgentDocumentId;
import io.camunda.connector.agenticai.aiagent.model.document.ResponseDocumentSelection;
import io.camunda.connector.agenticai.aiagent.model.tool.DocumentParameter;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolDefinition;
import io.camunda.connector.e2e.ElementTemplate;
import io.camunda.connector.e2e.ZeebeTest;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.openai.OpenAiCompletionsChatModelStubs;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.openai.OpenAiCompletionsChatModelStubs.ToolCall;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.openai.OpenAiCompletionsChatModelStubs.Turn;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.openai.OpenAiCompletionsRecordedConversation;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.openai.OpenAiCompletionsRecordedConversation.RecordedChatRequest;
import io.camunda.connector.e2e.agenticai.assertj.AgentInstanceEngineVerifier;
import io.camunda.connector.e2e.agenticai.assertj.AgentSubProcessResponseAssert;
import io.camunda.connector.test.utils.annotation.SlowTest;
import io.camunda.process.test.api.CamundaAssert;
import io.camunda.process.test.api.CamundaProcessTestContext;
import io.camunda.process.test.api.assertions.JobSelectors;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Document flow of the AI Agent Sub-process against a scripted model: a document parameter of a
 * tool (a real HTTP connector that uploads the document), loading a document, returning documents,
 * unknown document handles and the window. The process is the default one with the upload tool
 * added.
 */
@SlowTest
public class AgentSubProcessDocumentFlowTests extends BaseAgentSubProcessTest {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  @Value("classpath:agentic-ai-ahsp-connectors-documents.bpmn")
  private Resource documentsProcess;

  @Value("classpath:agentic-ai-ahsp-connectors-documents-content.bpmn")
  private Resource contentDocumentsProcess;

  @Autowired private CamundaProcessTestContext processTestContext;

  @Override
  protected BpmnModelInstance customizeModel(BpmnModelInstance model) {
    return withUploadTool(model, AI_AGENT_ELEMENT_ID);
  }

  @BeforeEach
  void setUpDocumentFlow() {
    stubUploadEndpoint();
    enqueueUserFeedback(userSatisfiedFeedback());
  }

  /**
   * The document is downloaded in one job, passed to a tool in a later one and returned in the last
   * one. The agent documents and the response documents are part of the agent context. The history
   * of the agent instance shows the handle of the document in the arguments of both calls.
   */
  @Test
  void passesADownloadedDocumentToAToolAndReturnsIt() throws Exception {
    final var turns = roundTripTurns(wireMock.getHttpBaseUrl());
    OpenAiCompletionsChatModelStubs.stubConversation(turns.toArray(Turn[]::new));

    final var zeebeTest = awaitProcessCompletion(startProcess(e -> e));

    final var recorded = OpenAiCompletionsRecordedConversation.recorded();
    assertThat(recorded.modelCallCount()).isEqualTo(turns.size());
    assertThat(recorded.requests().getFirst().toolNames())
        .contains("Download_A_File", UPLOAD_TOOL, "c8__return_document", "c8__load_document");

    // the id the model works with is the one shown with the downloaded document
    final var downloadResult = recorded.requests().get(1).messages().get(3);
    assertThat(downloadResult.toolCallId()).isEqualTo(DOWNLOAD_CALL_ID);
    final var documentId = parseDocumentHandle(downloadResult.content()).id().value();

    // the tool received the real bytes, the model only got the outcome
    assertUploaded(servedFile(DOWNLOADED_FILE));
    final var uploadResult = recorded.requests().get(2).messages().getLast();
    assertThat(uploadResult.role()).isEqualTo("tool");
    assertThat(uploadResult.toolCallId()).isEqualTo(UPLOAD_CALL_ID);
    JSONAssert.assertEquals("{\"status\":200}", uploadResult.content(), JSONCompareMode.STRICT);

    final var returnResult = recorded.lastRequest().messages().getLast();
    assertThat(returnResult.toolCallId()).isEqualTo(RETURN_CALL_ID);
    assertThat(returnResult.content()).isEqualTo("Returned the document under the key 'report'.");

    final var agentInstanceKey = new AtomicLong();
    assertAgentResponse(
        zeebeTest,
        agentResponse -> {
          AgentSubProcessResponseAssert.assertThat(agentResponse)
              .isReady()
              .hasResponseText(FINAL_TEXT);
          assertResponseDocument(
              agentResponse.context(), agentResponse.responseDocuments(), documentId);
          // the agent documents and the response documents are part of the agent context
          assertThat(agentResponse.context().documents().all())
              .extracting(entry -> entry.id().value())
              .containsExactly(documentId);
          assertThat(agentResponse.context().responseDocuments())
              .containsExactly(
                  new ResponseDocumentSelection(
                      RETURN_KEY, AgentDocumentId.of(documentId), RETURN_DESCRIPTION));
          agentInstanceKey.set(agentResponse.context().metadata().agentInstanceKey().value());
        });

    // the history of the agent instance shows the arguments of a call as the model sent them: the
    // handle of the document, for a tool of the process and for the runtime tool
    final var documentHandle = Map.of("type", AgentDocumentHandle.TYPE, "id", documentId);
    AgentInstanceEngineVerifier.verify(camundaClient, agentInstanceKey.get())
        .hasToolCallArguments(
            "Upload_A_File",
            arguments -> assertThat(arguments).isEqualTo(Map.of("document", documentHandle)))
        .hasToolCallArguments(
            "c8__return_document",
            arguments ->
                assertThat(arguments)
                    .isEqualTo(
                        Map.of(
                            "key",
                            RETURN_KEY,
                            "document",
                            documentHandle,
                            "description",
                            RETURN_DESCRIPTION)))
        .verify();

    assertNoDocumentInternalsInLlmRequests(recorded.providerNeutralRequests());
  }

  /**
   * A tool returns a document, the model loads it twice and passes its handle to a tool: the first
   * load puts the content next to the handle, the second one is answered without loading again, and
   * the tool receives the real bytes. The content is a text for a text document, an image for an
   * image and a file for a PDF. This is the e2e coverage of all file types. The model provider sees
   * the object schema of a document handle for the document parameter of the tool, the persisted
   * tool definition holds the same schema, and the history shows the result of the tool with the
   * handle nested in it.
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
        recorded.requests(), block -> assertDocumentContentBlockJson(block, file));
    assertUploaded(servedFile(file));

    // the model provider sees the object schema of a document handle
    final var upload = toolDefinition(recorded.lastRequest(), UPLOAD_TOOL);
    assertDocumentParameterSchema(upload);
    assertThat(recorded.lastRequest().toolNames()).contains("c8__load_document");
    assertNoDocumentInternalsInLlmRequests(recorded.providerNeutralRequests());

    final var agentInstanceKey = new AtomicLong();
    assertAgentResponse(
        zeebeTest,
        agentResponse -> {
          AgentSubProcessResponseAssert.assertThat(agentResponse)
              .isReady()
              .hasResponseText(FINAL_TEXT);
          // the persisted tool definition holds the same schema
          assertDocumentParameterSchema(
              agentResponse.context().toolDefinitions().stream()
                  .filter(definition -> UPLOAD_TOOL.equals(definition.name()))
                  .findFirst()
                  .orElseThrow());
          agentInstanceKey.set(agentResponse.context().metadata().agentInstanceKey().value());
        });

    assertDownloadResultInHistory(camundaClient, agentInstanceKey.get(), recorded.requests());
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

    final var agentInstanceKey = runWithPromptDocument(documentsProcess);

    final var recorded = OpenAiCompletionsRecordedConversation.recorded();
    final var history = awaitCommittedHistory(agentInstanceKey);

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
   * A tool whose activity sets the document delivery {@code content} returns a document: the agent
   * adds its content to the request in the message after the tool result, so the model reads it
   * without a {@code c8__load_document} call and still passes its handle to a tool. The tool
   * receives the real bytes.
   */
  @ParameterizedTest(name = "{0}")
  @ValueSource(strings = {"test.txt", "test.pdf"})
  void addsTheContentOfTheDocumentsOfAToolWithTheDeliveryContentForTheModel(String file)
      throws Exception {
    final var turns =
        List.of(downloadTurn(wireMock.getHttpBaseUrl(), file), uploadTurn(), finalTurn());
    OpenAiCompletionsChatModelStubs.stubConversation(turns.toArray(Turn[]::new));

    final var zeebeTest = awaitProcessCompletion(startDocumentsContentProcess());

    final var recorded = OpenAiCompletionsRecordedConversation.recorded();
    assertThat(recorded.modelCallCount()).isEqualTo(turns.size());
    final var afterDownload = recorded.requests().get(1).messages();
    assertThat(afterDownload)
        .extracting(m -> m.role())
        .containsExactly("system", "user", "assistant", "tool", "user");
    assertThat(afterDownload.get(3).toolCallId()).isEqualTo(DOWNLOAD_CALL_ID);
    final var handle = parseDocumentHandle(afterDownload.get(3).content());
    assertThat(afterDownload.get(3).content())
        .isEqualTo(
            "{\"status\":200,\"document\":"
                + handle.text()
                + "}\n"
                + contentFollowsSentence(handle));
    assertDocumentContentUserMessage(
        afterDownload.get(4),
        DocumentWithContent.of(
            handle,
            block ->
                assertDocumentContentBlockJson(
                    block,
                    file.endsWith(".pdf") ? "base64" : "text",
                    file.endsWith(".pdf") ? "application/pdf" : "text/plain")));

    // no load by the model: the next request holds the upload and nothing else
    final var afterUpload = recorded.lastRequest().messages();
    assertThat(afterUpload)
        .extracting(m -> m.role())
        .containsExactly("system", "user", "assistant", "tool", "user", "assistant", "tool");
    assertThat(afterUpload.get(6).toolCallId()).isEqualTo(UPLOAD_CALL_ID);
    assertThat(afterUpload)
        .flatExtracting(message -> message.toolCalls())
        .extracting(toolCall -> toolCall.name())
        .containsExactly("Download_A_File", "Upload_A_File");
    assertUploaded(servedFile(file));

    assertAgentResponse(
        zeebeTest,
        agentResponse ->
            AgentSubProcessResponseAssert.assertThat(agentResponse)
                .isReady()
                .hasResponseText(FINAL_TEXT));
    assertNoDocumentInternalsInLlmRequests(recorded.providerNeutralRequests());
  }

  /**
   * The valid {@code c8__return_document} call is answered by the agent, the real call runs in the
   * process, and the model sees both results in the order of its calls without an extra model call:
   * one call before the tools run and one after. The agent instance lists the runtime tool without
   * a BPMN element and reports its call and its result next to the real call.
   */
  @Test
  void mixedBatchOfReturnDocumentAndARealToolCostsNoExtraModelCall() throws Exception {
    final var turns =
        List.of(
            downloadTurn(wireMock.getHttpBaseUrl()),
            returnDocumentWithComplexToolTurn(),
            finalTurn());
    OpenAiCompletionsChatModelStubs.stubConversation(turns.toArray(Turn[]::new));

    final var zeebeTest = startProcess(e -> e);
    final var processInstance = zeebeTest.getProcessInstanceEvent();
    CamundaAssert.assertThat(processInstance).hasActiveElements(COMPLEX_TOOL);
    final var agentInstanceKey =
        AgentInstanceEngineVerifier.awaitAgentInstanceKey(
            camundaClient, processInstance.getProcessInstanceKey());

    // the complex tool has not answered, so the history holds no result of the batch yet
    AgentInstanceEngineVerifier.verify(camundaClient, agentInstanceKey)
        .hasExactlyToolResultsInOrder("Download_A_File")
        .verify();
    CamundaAssert.assertThat(processInstance).hasActiveElements(COMPLEX_TOOL);

    processTestContext.completeJob(
        JobSelectors.byElementId(COMPLEX_TOOL)
            .and(JobSelectors.byProcessInstanceKey(processInstance.getProcessInstanceKey())),
        Map.of("toolCallResult", "complex tool done"));
    awaitProcessCompletion(zeebeTest);

    final var recorded = OpenAiCompletionsRecordedConversation.recorded();
    assertThat(recorded.modelCallCount()).isEqualTo(turns.size());

    final var documentId =
        parseDocumentHandle(recorded.requests().get(1).messages().get(3).content()).id().value();
    final var messages = recorded.lastRequest().messages();
    final var results = messages.subList(messages.size() - 2, messages.size());
    assertThat(results).extracting(m -> m.role()).containsExactly("tool", "tool");
    assertThat(results)
        .extracting(m -> m.toolCallId())
        .containsExactly(RETURN_CALL_ID, COMPLEX_TOOL_CALL_ID);
    assertThat(results.get(0).content()).isEqualTo("Returned the document under the key 'report'.");
    assertThat(results.get(1).content()).isEqualTo("complex tool done");

    assertAgentResponse(
        zeebeTest,
        agentResponse -> {
          AgentSubProcessResponseAssert.assertThat(agentResponse)
              .isReady()
              .hasResponseText(FINAL_TEXT);
          assertResponseDocument(
              agentResponse.context(), agentResponse.responseDocuments(), documentId);
          assertThat(agentResponse.context().responseDocuments())
              .containsExactly(
                  new ResponseDocumentSelection(
                      RETURN_KEY, AgentDocumentId.of(documentId), RETURN_DESCRIPTION));
        });

    // the results of the batch are reported together, in the order of the calls
    AgentInstanceEngineVerifier.verify(camundaClient, agentInstanceKey)
        .hasToolsContaining("Download_A_File", UPLOAD_TOOL, COMPLEX_TOOL, "c8__return_document")
        .hasToolWithoutElementId("c8__return_document")
        .hasExactlyToolResultsInOrder("Download_A_File", "c8__return_document", COMPLEX_TOOL)
        .hasToolCallsWithElementId("c8__return_document", null)
        .hasToolCallsWithElementId(COMPLEX_TOOL, COMPLEX_TOOL)
        .verify();
    assertNoDocumentInternalsInLlmRequests(recorded.providerNeutralRequests());
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
        unknownUploadWithSuperfluxTurn(),
        uploadTurn(),
        finalTurn());

    final var zeebeTest = awaitProcessCompletion(startProcess(e -> e));

    final var recorded = OpenAiCompletionsRecordedConversation.recorded();
    assertThat(recorded.modelCallCount()).isEqualTo(4);

    final var messages = recorded.requests().get(2).messages();
    final var results = messages.subList(messages.size() - 2, messages.size());
    assertThat(results)
        .extracting(m -> m.toolCallId())
        .containsExactly(UPLOAD_CALL_ID, SUPERFLUX_CALL_ID);
    assertThat(results.get(0).content())
        .isEqualTo(
            "Parameter 'document' has the id 'doc_000000000000', but the conversation has no document"
                + " with this id. Pass the handle of a document from the conversation: a JSON object"
                + " such as {\"type\":\"camunda.agenticai.document\",\"id\":\"doc_…\"}.");
    assertThat(results.get(1).content()).isEqualTo(SUPERFLUX_RESULT);

    // only the retry with the handle of the downloaded document uploads
    assertThat(uploadCount()).isEqualTo(1);
    assertUploaded(servedFile(DOWNLOADED_FILE));

    assertAgentResponse(
        zeebeTest,
        agentResponse ->
            AgentSubProcessResponseAssert.assertThat(agentResponse)
                .isReady()
                .hasResponseText(FINAL_TEXT));
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
            ToolCall.of(SUPERFLUX_CALL_ID, "SuperfluxProduct", "{\"a\": 5, \"b\": 3}")),
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

  private static ToolDefinition toolDefinition(RecordedChatRequest request, String name) {
    final var matches =
        request.toolDefinitions().stream().filter(tool -> name.equals(tool.name())).toList();
    assertThat(matches).as("tool '%s' in request", name).hasSize(1);
    return matches.getFirst();
  }

  private static void assertDocumentParameterSchema(ToolDefinition upload) {
    assertThat(upload.inputSchema())
        .isEqualTo(
            Map.of(
                "type",
                "object",
                "properties",
                Map.of("document", DocumentParameter.documentSchema("The document to upload.")),
                "required",
                List.of("document")));
  }

  private ZeebeTest startProcess(Function<ElementTemplate, ElementTemplate> elementTemplateModifier)
      throws Exception {
    return createProcessInstance(
        elementTemplateModifier,
        Map.of(
            "userPrompt",
            "Download the file, upload it and give it back to me.",
            "uploadUrl",
            uploadUrl(wireMock.getHttpBaseUrl())));
  }

  private io.camunda.connector.e2e.ZeebeTest startDocumentsContentProcess() throws Exception {
    return createProcessInstance(
        contentDocumentsProcess,
        e -> e,
        Map.of(
            "userPrompt",
            "Download the file, upload it and give it back to me.",
            "uploadUrl",
            uploadUrl(wireMock.getHttpBaseUrl())));
  }
}
