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
import static io.camunda.connector.e2e.agenticai.aiagent.DocumentFlowFixtures.DOWNLOADED_FILE;
import static io.camunda.connector.e2e.agenticai.aiagent.DocumentFlowFixtures.DOWNLOAD_CALL_ID;
import static io.camunda.connector.e2e.agenticai.aiagent.DocumentFlowFixtures.FINAL_TEXT;
import static io.camunda.connector.e2e.agenticai.aiagent.DocumentFlowFixtures.GET_AGAIN_CALL_ID;
import static io.camunda.connector.e2e.agenticai.aiagent.DocumentFlowFixtures.GET_CALL_ID;
import static io.camunda.connector.e2e.agenticai.aiagent.DocumentFlowFixtures.PING_CALL_ID;
import static io.camunda.connector.e2e.agenticai.aiagent.DocumentFlowFixtures.RETURN_CALL_ID;
import static io.camunda.connector.e2e.agenticai.aiagent.DocumentFlowFixtures.RETURN_DESCRIPTION;
import static io.camunda.connector.e2e.agenticai.aiagent.DocumentFlowFixtures.RETURN_KEY;
import static io.camunda.connector.e2e.agenticai.aiagent.DocumentFlowFixtures.UPLOAD_CALL_ID;
import static io.camunda.connector.e2e.agenticai.aiagent.DocumentFlowFixtures.assertResponseDocument;
import static io.camunda.connector.e2e.agenticai.aiagent.DocumentFlowFixtures.assertUploaded;
import static io.camunda.connector.e2e.agenticai.aiagent.DocumentFlowFixtures.downloadTurn;
import static io.camunda.connector.e2e.agenticai.aiagent.DocumentFlowFixtures.finalTurn;
import static io.camunda.connector.e2e.agenticai.aiagent.DocumentFlowFixtures.getDocumentTurn;
import static io.camunda.connector.e2e.agenticai.aiagent.DocumentFlowFixtures.handle;
import static io.camunda.connector.e2e.agenticai.aiagent.DocumentFlowFixtures.returnDocumentWithPingTurn;
import static io.camunda.connector.e2e.agenticai.aiagent.DocumentFlowFixtures.returnDocumentWithSlowToolTurn;
import static io.camunda.connector.e2e.agenticai.aiagent.DocumentFlowFixtures.roundTripTurns;
import static io.camunda.connector.e2e.agenticai.aiagent.DocumentFlowFixtures.servedFile;
import static io.camunda.connector.e2e.agenticai.aiagent.DocumentFlowFixtures.stubUploadEndpoint;
import static io.camunda.connector.e2e.agenticai.aiagent.DocumentFlowFixtures.unknownUploadWithSuperfluxTurn;
import static io.camunda.connector.e2e.agenticai.aiagent.DocumentFlowFixtures.uploadCount;
import static io.camunda.connector.e2e.agenticai.aiagent.DocumentFlowFixtures.uploadTurn;
import static io.camunda.connector.e2e.agenticai.aiagent.DocumentFlowFixtures.uploadUrl;
import static io.camunda.connector.e2e.agenticai.aiagent.DocumentFlowFixtures.withUploadTool;
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
import io.camunda.connector.agenticai.aiagent.model.document.AgentDocumentHandle;
import io.camunda.connector.agenticai.aiagent.model.document.AgentDocumentId;
import io.camunda.connector.agenticai.aiagent.model.document.ResponseDocumentSelection;
import io.camunda.connector.agenticai.aiagent.model.tool.DocumentParameter;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolDefinition;
import io.camunda.connector.e2e.ElementTemplate;
import io.camunda.connector.e2e.agenticai.aiagent.ToolCallResultDocumentAssertions.DocumentWithContent;
import io.camunda.connector.e2e.agenticai.aiagent.common.AgentCoreMemoryTestConfiguration;
import io.camunda.connector.e2e.agenticai.aiagent.common.InMemoryBedrockAgentCoreClientFactory;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.openai.OpenAiCompletionsChatModelStubs;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.openai.OpenAiCompletionsChatModelStubs.ToolCall;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.openai.OpenAiCompletionsChatModelStubs.Turn;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.openai.OpenAiCompletionsRecordedConversation;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.openai.OpenAiCompletionsRecordedConversation.RecordedChatRequest;
import io.camunda.connector.e2e.agenticai.assertj.AgentSubProcessResponseAssert;
import io.camunda.connector.test.utils.annotation.SlowTest;
import io.camunda.process.test.api.CamundaAssert;
import io.camunda.process.test.api.CamundaProcessTestContext;
import io.camunda.process.test.api.assertions.JobSelectors;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.stream.Stream;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.Resource;

/**
 * Document flow of the AI Agent Sub-process against a scripted model: a document parameter of a
 * tool (a real HTTP connector that uploads the document), loading a document, unknown document
 * handles, the window and returning documents.
 */
@SlowTest
@Import(AgentCoreMemoryTestConfiguration.class)
public class AgentSubProcessDocumentFlowTests extends BaseAgentSubProcessTest {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  @Value("classpath:agentic-ai-ahsp-connectors-documents.bpmn")
  private Resource documentsProcess;

  @Autowired private CamundaProcessTestContext processTestContext;

  @Override
  protected BpmnModelInstance customizeModel(BpmnModelInstance model) {
    return withUploadTool(model, AI_AGENT_ELEMENT_ID);
  }

  @BeforeEach
  void setUpDocumentFlow() {
    InMemoryBedrockAgentCoreClientFactory.INSTANCE.clear();
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
            "Download_A_File",
            "Upload_A_File",
            "Fetch_A_File",
            "Describe_A_File",
            "Ping",
            "Slow_Tool",
            "c8__return_document",
            "c8__load_document");
    assertNoDocumentInternalsInLlmRequests(recorded.providerNeutralRequests());
  }

  static Stream<Arguments> memoryStores() {
    return Stream.of(
        Arguments.of("in-process", (Function<ElementTemplate, ElementTemplate>) e -> e),
        Arguments.of(
            "camunda-document",
            (Function<ElementTemplate, ElementTemplate>)
                e ->
                    e.property("data.memory.storage.type", "camunda-document")
                        .property("data.memory.storage.timeToLive", "PT1H")),
        Arguments.of(
            "aws-agentcore",
            (Function<ElementTemplate, ElementTemplate>)
                e ->
                    e.property("data.memory.storage.type", "aws-agentcore")
                        .property("data.memory.storage.region", "us-east-1")
                        .property("data.memory.storage.authentication.type", "credentials")
                        .property("data.memory.storage.authentication.accessKey", "test-access-key")
                        .property("data.memory.storage.authentication.secretKey", "test-secret-key")
                        .property("data.memory.storage.memoryId", "test-memory-id")
                        .property("data.memory.storage.actorId", "test-actor-id")));
  }

  /**
   * The document is downloaded in one job, passed to a tool in a later one and returned in the last
   * one, so the agent documents have to survive the job boundaries in every memory store. The
   * history of the agent instance shows the handle of the document in the arguments of both calls.
   */
  @ParameterizedTest(name = "{0} memory")
  @MethodSource("memoryStores")
  void passesADownloadedDocumentToAToolAndReturnsIt(
      String store, Function<ElementTemplate, ElementTemplate> memoryStore) throws Exception {
    final var turns = roundTripTurns(wireMock.getHttpBaseUrl());
    OpenAiCompletionsChatModelStubs.stubConversation(turns.toArray(Turn[]::new));

    final var zeebeTest = awaitProcessCompletion(startProcess(memoryStore));

    final var recorded = OpenAiCompletionsRecordedConversation.recorded();
    assertThat(recorded.modelCallCount()).isEqualTo(turns.size());
    assertThat(recorded.requests().getFirst().toolNames())
        .containsExactlyInAnyOrder(
            "Download_A_File",
            "Upload_A_File",
            "Fetch_A_File",
            "Describe_A_File",
            "Ping",
            "Slow_Tool",
            "c8__return_document",
            "c8__load_document");

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
   * The valid {@code c8__return_document} call is answered by the agent, the real call runs in the
   * process, and the model sees both results in the order of its calls without an extra model call:
   * one call before the tools run and one after. The agent instance lists the runtime tool without
   * a BPMN element and reports its call and its result next to the real call.
   */
  @Test
  void mixedBatchOfReturnDocumentAndARealToolCostsNoExtraModelCall() throws Exception {
    final var turns =
        List.of(downloadTurn(wireMock.getHttpBaseUrl()), returnDocumentWithPingTurn(), finalTurn());
    OpenAiCompletionsChatModelStubs.stubConversation(turns.toArray(Turn[]::new));

    final var zeebeTest = awaitProcessCompletion(startProcess(e -> e));

    final var recorded = OpenAiCompletionsRecordedConversation.recorded();
    assertThat(recorded.modelCallCount()).isEqualTo(turns.size());

    final var documentId =
        parseDocumentHandle(recorded.requests().get(1).messages().get(3).content()).id().value();
    final var messages = recorded.lastRequest().messages();
    final var results = messages.subList(messages.size() - 2, messages.size());
    assertThat(results).extracting(m -> m.role()).containsExactly("tool", "tool");
    assertThat(results)
        .extracting(m -> m.toolCallId())
        .containsExactly(RETURN_CALL_ID, PING_CALL_ID);
    assertThat(results.get(0).content()).isEqualTo("Returned the document under the key 'report'.");
    assertThat(results.get(1).content()).isEqualTo("pong");

    final var agentInstanceKey = new AtomicLong();
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
          agentInstanceKey.set(agentResponse.context().metadata().agentInstanceKey().value());
        });

    // the agent instance lists the runtime tool without a BPMN element and reports its call and its
    // result next to the real call of the batch
    AgentInstanceEngineVerifier.verify(camundaClient, agentInstanceKey.get())
        .hasToolsContaining("Download_A_File", "Upload_A_File", "Ping", "c8__return_document")
        .hasToolWithoutElementId("c8__return_document")
        .hasToolResultsFor("c8__return_document", "Ping")
        .hasToolCallsWithElementId("c8__return_document", null)
        .hasToolCallsWithElementId("Ping", "Ping")
        .verify();
    assertNoDocumentInternalsInLlmRequests(recorded.providerNeutralRequests());
  }

  /**
   * The runtime result of a mixed batch is in the agent instance history when the turn that follows
   * the results of the process is composed: while the real call of the batch is still running, the
   * history holds no result of the batch, and then it holds the results in the order of the calls,
   * each once.
   */
  @Test
  void runtimeResultOfAMixedBatchIsInTheHistoryWithTheTurnThatFollowsTheRealCall()
      throws Exception {
    OpenAiCompletionsChatModelStubs.stubConversation(
        downloadTurn(wireMock.getHttpBaseUrl()), returnDocumentWithSlowToolTurn(), finalTurn());

    final var zeebeTest = startProcess(e -> e);
    final var processInstance = zeebeTest.getProcessInstanceEvent();
    CamundaAssert.assertThat(processInstance).hasActiveElements("Slow_Tool");
    final var agentInstanceKey =
        AgentInstanceEngineVerifier.awaitAgentInstanceKey(
            camundaClient, processInstance.getProcessInstanceKey());

    // the slow tool has not answered, the runtime call is not answered yet
    AgentInstanceEngineVerifier.verify(camundaClient, agentInstanceKey)
        .hasExactlyToolResultsInOrder("Download_A_File")
        .verify();
    CamundaAssert.assertThat(processInstance).hasActiveElements("Slow_Tool");

    processTestContext.completeJob(
        JobSelectors.byElementId("Slow_Tool")
            .and(JobSelectors.byProcessInstanceKey(processInstance.getProcessInstanceKey())),
        Map.of("toolCallResult", "slow tool done"));
    awaitProcessCompletion(zeebeTest);
    assertThat(OpenAiCompletionsRecordedConversation.recorded().modelCallCount()).isEqualTo(3);

    // the next job reports the results of the batch together: call order kept
    AgentInstanceEngineVerifier.verify(camundaClient, agentInstanceKey)
        .hasExactlyToolResultsInOrder("Download_A_File", "c8__return_document", "Slow_Tool")
        .verify();
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
}
