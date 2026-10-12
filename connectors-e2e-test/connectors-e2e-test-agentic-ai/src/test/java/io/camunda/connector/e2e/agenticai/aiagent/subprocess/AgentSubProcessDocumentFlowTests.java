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

import static io.camunda.connector.e2e.agenticai.aiagent.DocumentFlowAssertions.assertLoadedOnceAndPassedByIdFlow;
import static io.camunda.connector.e2e.agenticai.aiagent.DocumentFlowAssertions.assertNoDocumentInternalsInLlmRequests;
import static io.camunda.connector.e2e.agenticai.aiagent.DocumentFlowAssertions.toolParameters;
import static io.camunda.connector.e2e.agenticai.aiagent.DocumentFlowFixtures.DOWNLOADED_FILE;
import static io.camunda.connector.e2e.agenticai.aiagent.DocumentFlowFixtures.DOWNLOAD_CALL_ID;
import static io.camunda.connector.e2e.agenticai.aiagent.DocumentFlowFixtures.FINAL_TEXT;
import static io.camunda.connector.e2e.agenticai.aiagent.DocumentFlowFixtures.GET_AGAIN_CALL_ID;
import static io.camunda.connector.e2e.agenticai.aiagent.DocumentFlowFixtures.GET_CALL_ID;
import static io.camunda.connector.e2e.agenticai.aiagent.DocumentFlowFixtures.LARGE_FILE;
import static io.camunda.connector.e2e.agenticai.aiagent.DocumentFlowFixtures.LARGE_FILE_MARKER;
import static io.camunda.connector.e2e.agenticai.aiagent.DocumentFlowFixtures.PING_CALL_ID;
import static io.camunda.connector.e2e.agenticai.aiagent.DocumentFlowFixtures.RETURN_CALL_ID;
import static io.camunda.connector.e2e.agenticai.aiagent.DocumentFlowFixtures.RETURN_KEY;
import static io.camunda.connector.e2e.agenticai.aiagent.DocumentFlowFixtures.UPLOAD_CALL_ID;
import static io.camunda.connector.e2e.agenticai.aiagent.DocumentFlowFixtures.assertReturnedDocument;
import static io.camunda.connector.e2e.agenticai.aiagent.DocumentFlowFixtures.assertUploaded;
import static io.camunda.connector.e2e.agenticai.aiagent.DocumentFlowFixtures.downloadTurn;
import static io.camunda.connector.e2e.agenticai.aiagent.DocumentFlowFixtures.finalTurn;
import static io.camunda.connector.e2e.agenticai.aiagent.DocumentFlowFixtures.getDocumentTurn;
import static io.camunda.connector.e2e.agenticai.aiagent.DocumentFlowFixtures.returnDocumentWithPingTurn;
import static io.camunda.connector.e2e.agenticai.aiagent.DocumentFlowFixtures.returnDocumentWithSlowToolTurn;
import static io.camunda.connector.e2e.agenticai.aiagent.DocumentFlowFixtures.roundTripTurns;
import static io.camunda.connector.e2e.agenticai.aiagent.DocumentFlowFixtures.servedFile;
import static io.camunda.connector.e2e.agenticai.aiagent.DocumentFlowFixtures.stubLargeTextFile;
import static io.camunda.connector.e2e.agenticai.aiagent.DocumentFlowFixtures.stubUploadEndpoint;
import static io.camunda.connector.e2e.agenticai.aiagent.DocumentFlowFixtures.unknownUploadWithPingTurn;
import static io.camunda.connector.e2e.agenticai.aiagent.DocumentFlowFixtures.uploadCount;
import static io.camunda.connector.e2e.agenticai.aiagent.DocumentFlowFixtures.uploadTurn;
import static io.camunda.connector.e2e.agenticai.aiagent.DocumentFlowFixtures.uploadUrl;
import static io.camunda.connector.e2e.agenticai.aiagent.ToolCallResultDocumentAssertions.assertDocumentContentBlockJson;
import static io.camunda.connector.e2e.agenticai.aiagent.ToolCallResultDocumentAssertions.assertLoadedDocumentsUserMessage;
import static io.camunda.connector.e2e.agenticai.aiagent.ToolCallResultDocumentAssertions.parseDocumentTag;
import static io.camunda.connector.e2e.agenticai.aiagent.wiremock.DocumentIdPlaceholderTransformer.documentId;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.connector.agenticai.aiagent.model.document.EvictedDocumentsNote;
import io.camunda.connector.agenticai.aiagent.model.tool.DocumentParameterSchema;
import io.camunda.connector.e2e.ElementTemplate;
import io.camunda.connector.e2e.agenticai.AgentCoreMemoryTestConfiguration;
import io.camunda.connector.e2e.agenticai.InMemoryBedrockAgentCoreClientFactory;
import io.camunda.connector.e2e.agenticai.aiagent.ToolCallResultDocumentAssertions.LoadedDocument;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.openai.OpenAiCompletionsChatModelStubs;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.openai.OpenAiCompletionsChatModelStubs.Turn;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.openai.OpenAiCompletionsRecordedConversation;
import io.camunda.connector.e2e.agenticai.assertj.AgentInstanceEngineVerifier;
import io.camunda.connector.e2e.agenticai.assertj.AgentSubProcessResponseAssert;
import io.camunda.connector.test.utils.annotation.SlowTest;
import io.camunda.process.test.api.CamundaAssert;
import io.camunda.process.test.api.CamundaProcessTestContext;
import io.camunda.process.test.api.assertions.JobSelectors;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.Resource;

/**
 * Document flow of the AI Agent Sub-process against a scripted model: a document parameter of a
 * tool (a real HTTP connector that uploads the document), the registry across jobs, unknown ids and
 * returning documents.
 */
@SlowTest
@Import(AgentCoreMemoryTestConfiguration.class)
public class AgentSubProcessDocumentFlowTests extends BaseAgentSubProcessTest {

  @Value("classpath:agentic-ai-ahsp-connectors-documents.bpmn")
  private Resource documentsProcess;

  @Value("classpath:agentic-ai-ahsp-connectors-documents-inline.bpmn")
  private Resource inlineDocumentsProcess;

  @Autowired private CamundaProcessTestContext processTestContext;

  @Override
  protected Resource testProcess() {
    return documentsProcess;
  }

  @BeforeEach
  void setUpDocumentFlow() {
    InMemoryBedrockAgentCoreClientFactory.INSTANCE.clear();
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

    // the persisted tool definition keeps the format and holds the hint the model sees
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
              .contains("description=The document to upload. " + DocumentParameterSchema.HINT);
        });

    // the built-in tool is offered next to the tools of the process
    assertThat(OpenAiCompletionsRecordedConversation.recorded().lastRequest().toolNames())
        .containsExactlyInAnyOrder(
            "Download_A_File",
            "Upload_A_File",
            "Ping",
            "Slow_Tool",
            "c8__return_document",
            "c8__get_document");
    assertNoDocumentInternalsInLlmRequests();
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
   * one, so the registry has to survive the job boundaries in every memory store.
   */
  @ParameterizedTest(name = "{0} memory")
  @MethodSource("memoryStores")
  void passesADownloadedDocumentToAToolAndReturnsIt(
      String store, Function<ElementTemplate, ElementTemplate> memoryStore) throws Exception {
    OpenAiCompletionsChatModelStubs.stubConversation(
        roundTripTurns(wireMock.getHttpBaseUrl()).toArray(Turn[]::new));

    final var zeebeTest = awaitProcessCompletion(startProcess(memoryStore));

    final var recorded = OpenAiCompletionsRecordedConversation.recorded();
    assertThat(recorded.modelCallCount()).isEqualTo(4);
    assertThat(recorded.requests().getFirst().toolNames())
        .containsExactlyInAnyOrder(
            "Download_A_File",
            "Upload_A_File",
            "Ping",
            "Slow_Tool",
            "c8__return_document",
            "c8__get_document");

    // the id the model works with is the one shown with the downloaded document
    final var downloadResult = recorded.requests().get(1).messages().get(3);
    assertThat(downloadResult.toolCallId()).isEqualTo(DOWNLOAD_CALL_ID);
    final var documentId = parseDocumentTag(downloadResult.content()).id();

    // the tool received the real bytes, the model only got the outcome
    assertUploaded(servedFile(DOWNLOADED_FILE));
    final var uploadResult = recorded.requests().get(2).messages().getLast();
    assertThat(uploadResult.role()).isEqualTo("tool");
    assertThat(uploadResult.toolCallId()).isEqualTo(UPLOAD_CALL_ID);
    assertThat(uploadResult.content()).contains("200");

    final var returnResult = recorded.lastRequest().messages().getLast();
    assertThat(returnResult.toolCallId()).isEqualTo(RETURN_CALL_ID);
    assertThat(returnResult.content()).contains("under the key 'report'");

    assertAgentResponse(
        zeebeTest,
        agentResponse -> {
          AgentSubProcessResponseAssert.assertThat(agentResponse)
              .isReady()
              .hasResponseText(FINAL_TEXT);
          assertReturnedDocument(agentResponse.responseDocuments(), documentId);
          // the registry and the selection are part of the agent context
          assertThat(agentResponse.context().documents())
              .extracting(entry -> entry.id())
              .containsExactly(documentId);
          assertThat(agentResponse.context().returnDocuments()).containsOnlyKeys(RETURN_KEY);
        });

    assertNoDocumentInternalsInLlmRequests();
  }

  /**
   * A tool returns a document, the model loads it twice and passes its id to a tool: the first load
   * puts the content next to the tag, the second one is answered without loading again, and the
   * tool receives the real bytes. The content is a text for a text document and a file for a PDF.
   */
  @ParameterizedTest(name = "{0}")
  @ValueSource(strings = {"test.txt", "test.pdf"})
  void loadsAFetchedDocumentOnceAndPassesItsIdToATool(String file) throws Exception {
    OpenAiCompletionsChatModelStubs.stubConversation(
        downloadTurn(wireMock.getHttpBaseUrl(), file),
        getDocumentTurn(GET_CALL_ID, documentId(0)),
        getDocumentTurn(GET_AGAIN_CALL_ID, documentId(0)),
        uploadTurn(),
        finalTurn());

    final var zeebeTest = awaitProcessCompletion(startProcess(e -> e));

    assertLoadedOnceAndPassedByIdFlow(
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
   * A document above the limit is rejected with an answer that tells to pass the id to a tool: no
   * content reaches the model, and the tool still receives the real bytes.
   */
  @Test
  void rejectsALargeDocumentAndStillPassesItsIdToATool() throws Exception {
    final var largeFile = stubLargeTextFile();
    OpenAiCompletionsChatModelStubs.stubConversation(
        downloadTurn(wireMock.getHttpBaseUrl(), LARGE_FILE),
        getDocumentTurn(),
        uploadTurn(),
        finalTurn());

    final var zeebeTest = awaitProcessCompletion(startProcess(e -> e));

    final var recorded = OpenAiCompletionsRecordedConversation.recorded();
    assertThat(recorded.modelCallCount()).isEqualTo(4);
    final var afterRejection = recorded.requests().get(2).messages();
    assertThat(afterRejection)
        .extracting(m -> m.role())
        .containsExactly("system", "user", "assistant", "tool", "assistant", "tool");
    assertThat(afterRejection.get(5).toolCallId()).isEqualTo(GET_CALL_ID);
    assertThat(afterRejection.get(5).content())
        .endsWith(
            "too large to load into the conversation (limit 256 KB); pass its id to a tool that processes it.");
    assertThat(recorded.lastRequest().messages().getLast().content()).contains("200");
    assertThat(OpenAiCompletionsRecordedConversation.recordedRawBodies())
        .noneMatch(body -> body.contains(LARGE_FILE_MARKER));

    assertUploaded(largeFile);
    assertAgentResponse(
        zeebeTest,
        agentResponse ->
            AgentSubProcessResponseAssert.assertThat(agentResponse)
                .isReady()
                .hasResponseText(FINAL_TEXT));
  }

  /**
   * A tool whose activity requests inline delivery returns a document: the agent loads it for the
   * model in the message after the tool result, so the model reads it without a {@code
   * c8__get_document} call and still passes its id to a tool. The tool receives the real bytes.
   */
  @ParameterizedTest(name = "{0}")
  @ValueSource(strings = {"test.txt", "test.pdf"})
  void loadsTheDocumentsOfAnInlineToolForTheModel(String file) throws Exception {
    OpenAiCompletionsChatModelStubs.stubConversation(
        downloadTurn(wireMock.getHttpBaseUrl(), file), uploadTurn(), finalTurn());

    final var zeebeTest = awaitProcessCompletion(startInlineProcess());

    final var recorded = OpenAiCompletionsRecordedConversation.recorded();
    assertThat(recorded.modelCallCount()).isEqualTo(3);
    final var afterDownload = recorded.requests().get(1).messages();
    assertThat(afterDownload)
        .extracting(m -> m.role())
        .containsExactly("system", "user", "assistant", "tool", "user");
    assertThat(afterDownload.get(3).toolCallId()).isEqualTo(DOWNLOAD_CALL_ID);
    final var tag = parseDocumentTag(afterDownload.get(3).content());
    assertLoadedDocumentsUserMessage(
        afterDownload.get(4),
        LoadedDocument.forToolCall(
            DOWNLOAD_CALL_ID,
            "Download_A_File",
            tag,
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
    assertThat(afterUpload).noneMatch(m -> m.toString().contains("c8__get_document"));
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
   * A document of an inline tool that must not be loaded (a type outside the allowlist, a size
   * above the limit) stays a reference: a note tells the model that it was not loaded and why, no
   * content reaches the model, and the tool still receives the real bytes.
   */
  @ParameterizedTest(name = "{0}")
  @ValueSource(strings = {"unsupported.zip", LARGE_FILE})
  void keepsADocumentOfAnInlineToolThatCannotBeLoadedAsAReferenceWithANote(String file)
      throws Exception {
    final var expectedBytes = LARGE_FILE.equals(file) ? stubLargeTextFile() : servedFile(file);
    OpenAiCompletionsChatModelStubs.stubConversation(
        downloadTurn(wireMock.getHttpBaseUrl(), file), uploadTurn(), finalTurn());

    final var zeebeTest = awaitProcessCompletion(startInlineProcess());

    final var recorded = OpenAiCompletionsRecordedConversation.recorded();
    assertThat(recorded.modelCallCount()).isEqualTo(3);
    final var afterDownload = recorded.requests().get(1).messages();
    assertThat(afterDownload)
        .extracting(m -> m.role())
        .containsExactly("system", "user", "assistant", "tool", "user");
    final var tag = parseDocumentTag(afterDownload.get(3).content());
    assertThat(afterDownload.get(4).textContent())
        .startsWith("Document %s was not loaded automatically: it ".formatted(tag.id()))
        .endsWith("Pass its id to a tool that processes it.");
    assertThat(OpenAiCompletionsRecordedConversation.recordedRawBodies())
        .noneMatch(body -> body.contains(LARGE_FILE_MARKER));

    assertUploaded(expectedBytes);
    assertAgentResponse(
        zeebeTest,
        agentResponse ->
            AgentSubProcessResponseAssert.assertThat(agentResponse)
                .isReady()
                .hasResponseText(FINAL_TEXT));
  }

  /** The id of a document that is not known is answered by the framework, not by the tool. */
  @Test
  void answersGetDocumentOfAnUnknownIdWithTheKnownDocuments() throws Exception {
    OpenAiCompletionsChatModelStubs.stubConversation(
        downloadTurn(wireMock.getHttpBaseUrl()), getDocumentTurn("no-such-document"), finalTurn());

    final var zeebeTest = awaitProcessCompletion(startProcess(e -> e));

    final var recorded = OpenAiCompletionsRecordedConversation.recorded();
    assertThat(recorded.modelCallCount()).isEqualTo(3);
    final var documentId =
        parseDocumentTag(recorded.requests().get(1).messages().get(3).content()).id();
    final var errorResult = recorded.lastRequest().messages().getLast();
    assertThat(errorResult.role()).isEqualTo("tool");
    assertThat(errorResult.content())
        .startsWith(
            "Parameter 'id' must be the exact id of a known document (the id attribute of its <doc /> tag) or that tag itself, got 'no-such-document'.")
        .contains("<doc id=\"" + documentId + "\"");
    assertAgentResponse(
        zeebeTest,
        agentResponse ->
            AgentSubProcessResponseAssert.assertThat(agentResponse)
                .isReady()
                .hasResponseText(FINAL_TEXT));
  }

  @Test
  void unknownDocumentIdIsAnsweredInTheLoopAndTheModelRetries() throws Exception {
    OpenAiCompletionsChatModelStubs.stubConversation(
        downloadTurn(wireMock.getHttpBaseUrl()),
        uploadTurn("no-such-document"),
        uploadTurn(),
        finalTurn());

    final var zeebeTest = awaitProcessCompletion(startProcess(e -> e));

    final var recorded = OpenAiCompletionsRecordedConversation.recorded();
    assertThat(recorded.modelCallCount()).isEqualTo(4);

    // second request: after the download. third request: the bad id is answered without any tool
    // run, so no process step sits between the second and the third model call
    final var downloadResult = recorded.requests().get(1).messages().get(3);
    final var documentId = parseDocumentTag(downloadResult.content()).id();

    final var errorResult = recorded.requests().get(2).messages().getLast();
    assertThat(errorResult.role()).isEqualTo("tool");
    assertThat(errorResult.toolCallId()).isEqualTo(UPLOAD_CALL_ID);
    assertThat(errorResult.content())
        .startsWith(
            "Parameter 'document' must be the exact id of a known document (the id attribute of its <doc /> tag) or that tag itself, got 'no-such-document'.")
        .contains("Known documents:")
        .contains("<doc id=\"" + documentId + "\"");

    // the retry with the valid id is the only upload
    final var retryResult = recorded.lastRequest().messages().getLast();
    assertThat(retryResult.role()).isEqualTo("tool");
    assertThat(retryResult.content()).contains("200");
    assertThat(uploadCount()).isEqualTo(1);
    assertUploaded(servedFile(DOWNLOADED_FILE));

    // no incident, and the invalid call did not activate the tool element
    assertAgentResponse(
        zeebeTest,
        agentResponse ->
            AgentSubProcessResponseAssert.assertThat(agentResponse)
                .isReady()
                .hasResponseText(FINAL_TEXT));
    assertNoDocumentInternalsInLlmRequests();
  }

  /**
   * The valid {@code c8__return_document} call is answered by the agent, the real call runs in the
   * process, and the model sees both results in the order of its calls without an extra model call:
   * one call before the tools run and one after.
   */
  @Test
  void mixedBatchOfReturnDocumentAndARealToolCostsNoExtraModelCall() throws Exception {
    OpenAiCompletionsChatModelStubs.stubConversation(
        downloadTurn(wireMock.getHttpBaseUrl()), returnDocumentWithPingTurn(), finalTurn());

    final var zeebeTest = awaitProcessCompletion(startProcess(e -> e));

    final var recorded = OpenAiCompletionsRecordedConversation.recorded();
    assertThat(recorded.modelCallCount()).isEqualTo(3);

    final var documentId =
        parseDocumentTag(recorded.requests().get(1).messages().get(3).content()).id();
    final var messages = recorded.lastRequest().messages();
    final var results = messages.subList(messages.size() - 2, messages.size());
    assertThat(results).extracting(m -> m.role()).containsExactly("tool", "tool");
    assertThat(results)
        .extracting(m -> m.toolCallId())
        .containsExactly(RETURN_CALL_ID, PING_CALL_ID);
    assertThat(results.get(0).content()).contains("under the key 'report'");
    assertThat(results.get(1).content()).contains("pong");

    assertAgentResponse(
        zeebeTest,
        agentResponse -> {
          AgentSubProcessResponseAssert.assertThat(agentResponse)
              .isReady()
              .hasResponseText(FINAL_TEXT);
          assertReturnedDocument(agentResponse.responseDocuments(), documentId);
          assertThat(agentResponse.context().returnDocuments()).containsOnlyKeys(RETURN_KEY);
          assertThat(agentResponse.context().pendingLocalToolCallResults()).isEmpty();
        });
    assertNoDocumentInternalsInLlmRequests();
  }

  /**
   * The agent instance lists the available local tool without a BPMN element and reports its call
   * and its result next to the real call of the batch.
   */
  @Test
  void agentInstanceHistoryReportsTheLocalToolAndItsCalls() throws Exception {
    OpenAiCompletionsChatModelStubs.stubConversation(
        downloadTurn(wireMock.getHttpBaseUrl()), returnDocumentWithPingTurn(), finalTurn());

    final var zeebeTest = awaitProcessCompletion(startProcess(e -> e));

    final var agentInstanceKey = new AtomicLong();
    assertAgentResponse(
        zeebeTest,
        agentResponse ->
            agentInstanceKey.set(agentResponse.context().metadata().agentInstanceKey()));

    AgentInstanceEngineVerifier.verify(camundaClient, agentInstanceKey.get())
        .hasToolsContaining("Download_A_File", "Upload_A_File", "Ping", "c8__return_document")
        .hasToolWithoutElementId("c8__return_document")
        .hasToolResultsFor("c8__return_document", "Ping")
        .hasToolCallsWithElementId("c8__return_document", null)
        .hasToolCallsWithElementId("Ping", "Ping")
        .verify();
  }

  /**
   * The history of the agent instance shows the arguments of a call as the model sent them: the id
   * of a document in the call of the assistant item and in the arguments of the result item, for a
   * tool of the process and for the local tool.
   */
  @Test
  void agentInstanceHistoryShowsTheDocumentIdInTheToolCallArguments() throws Exception {
    OpenAiCompletionsChatModelStubs.stubConversation(
        roundTripTurns(wireMock.getHttpBaseUrl()).toArray(Turn[]::new));

    final var zeebeTest = awaitProcessCompletion(startProcess(e -> e));

    final var recorded = OpenAiCompletionsRecordedConversation.recorded();
    final var documentId =
        parseDocumentTag(recorded.requests().get(1).messages().get(3).content()).id();
    assertThat(recorded.requests().get(2).messages().toString()).contains(documentId);

    final var agentInstanceKey = new AtomicLong();
    assertAgentResponse(
        zeebeTest,
        agentResponse ->
            agentInstanceKey.set(agentResponse.context().metadata().agentInstanceKey()));

    AgentInstanceEngineVerifier.verify(camundaClient, agentInstanceKey.get())
        .hasToolCallArguments(
            "Upload_A_File",
            arguments -> assertThat(arguments.get("document")).isEqualTo(documentId))
        .hasToolCallArguments(
            "c8__return_document",
            arguments -> assertThat(arguments.get("document")).isEqualTo(documentId))
        .verify();
  }

  /**
   * The local result of a mixed batch is in the agent instance history as soon as the job that
   * answered it completes, while the real call of the batch is still running. The next job reports
   * it again with the merged results, and the history still holds it exactly once.
   */
  @Test
  void localResultOfAMixedBatchIsInTheHistoryBeforeTheRealCallCompletesAndStaysUnique()
      throws Exception {
    OpenAiCompletionsChatModelStubs.stubConversation(
        downloadTurn(wireMock.getHttpBaseUrl()), returnDocumentWithSlowToolTurn(), finalTurn());

    final var zeebeTest = startProcess(e -> e);
    final var processInstance = zeebeTest.getProcessInstanceEvent();
    CamundaAssert.assertThat(processInstance).hasActiveElements("Slow_Tool");
    final var agentInstanceKey =
        AgentInstanceEngineVerifier.awaitAgentInstanceKey(
            camundaClient, processInstance.getProcessInstanceKey());

    // the slow tool has not answered, the local result is already reported
    AgentInstanceEngineVerifier.verify(camundaClient, agentInstanceKey)
        .hasExactlyToolResultsInOrder("Download_A_File", "c8__return_document")
        .verify();
    CamundaAssert.assertThat(processInstance).hasActiveElements("Slow_Tool");

    processTestContext.completeJob(
        JobSelectors.byElementId("Slow_Tool")
            .and(JobSelectors.byProcessInstanceKey(processInstance.getProcessInstanceKey())),
        Map.of("toolCallResult", "slow tool done"));
    awaitProcessCompletion(zeebeTest);

    // the next job reported the local result again: no duplicate, call order kept
    AgentInstanceEngineVerifier.verify(camundaClient, agentInstanceKey)
        .hasExactlyToolResultsInOrder("Download_A_File", "c8__return_document", "Slow_Tool")
        .verify();
  }

  /**
   * The call with the unknown document id gets its error result, the valid call of the same batch
   * runs, and the model corrects the tag with one more call.
   */
  @Test
  void mixedBatchWithAnUnknownDocumentIdRunsTheValidCallAndAnswersTheInvalidOne() throws Exception {
    OpenAiCompletionsChatModelStubs.stubConversation(
        downloadTurn(wireMock.getHttpBaseUrl()),
        unknownUploadWithPingTurn(),
        uploadTurn(),
        finalTurn());

    final var zeebeTest = awaitProcessCompletion(startProcess(e -> e));

    final var recorded = OpenAiCompletionsRecordedConversation.recorded();
    assertThat(recorded.modelCallCount()).isEqualTo(4);

    final var documentId =
        parseDocumentTag(recorded.requests().get(1).messages().get(3).content()).id();
    final var messages = recorded.requests().get(2).messages();
    final var results = messages.subList(messages.size() - 2, messages.size());
    assertThat(results)
        .extracting(m -> m.toolCallId())
        .containsExactly(UPLOAD_CALL_ID, PING_CALL_ID);
    assertThat(results.get(0).content())
        .startsWith(
            "Parameter 'document' must be the exact id of a known document (the id attribute of its <doc /> tag) or that tag itself, got 'no-such-document'.")
        .contains("<doc id=\"" + documentId + "\"");
    assertThat(results.get(1).content()).contains("pong");

    // only the retry with the valid id uploads
    assertThat(uploadCount()).isEqualTo(1);
    assertUploaded(servedFile(DOWNLOADED_FILE));

    assertAgentResponse(
        zeebeTest,
        agentResponse -> {
          AgentSubProcessResponseAssert.assertThat(agentResponse)
              .isReady()
              .hasResponseText(FINAL_TEXT);
          assertThat(agentResponse.context().pendingLocalToolCallResults()).isEmpty();
        });
    assertNoDocumentInternalsInLlmRequests();
  }

  @Test
  void documentOfAnEvictedMessageStaysPassable() throws Exception {
    OpenAiCompletionsChatModelStubs.stubConversation(
        downloadTurn(wireMock.getHttpBaseUrl()),
        Turn.toolCalls(
            "Let me check that the tools work.",
            10,
            20,
            OpenAiCompletionsChatModelStubs.ToolCall.of("call_ping", "Ping", "{}")),
        uploadTurn(),
        finalTurn());

    awaitProcessCompletion(startProcess(e -> e.property("data.memory.contextWindowSize", "=3")));

    final var recorded = OpenAiCompletionsRecordedConversation.recorded();
    assertThat(recorded.modelCallCount()).isEqualTo(4);

    // the window of the second request is: system, assistant, tool result
    final var documentId =
        parseDocumentTag(recorded.requests().get(1).messages().get(2).content()).id();

    // the window of the third request no longer holds the download or its tool result
    final var windowed = recorded.requests().get(2).messages();
    assertThat(windowed).extracting(m -> m.toolCallId()).doesNotContain(DOWNLOAD_CALL_ID);
    assertThat(windowed)
        .noneMatch(
            m -> m.contentParts().stream().anyMatch(p -> "file".equals(p.path("type").asText())));

    // the note lists the document with its origin, still addressable by id
    final var note = windowed.getLast();
    assertThat(note.role()).isEqualTo("user");
    assertThat(note.textContent())
        .startsWith(EvictedDocumentsNote.PREAMBLE)
        .contains("<doc id=\"" + documentId + "\"")
        .contains("toolName=\"Download_A_File\"")
        .doesNotContain("storeId", "contentHash");

    // the model passed the tag and the tool still got the real bytes
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

  private io.camunda.connector.e2e.ZeebeTest startInlineProcess() throws Exception {
    return createProcessInstance(
        inlineDocumentsProcess,
        e -> e,
        Map.of(
            "userPrompt",
            "Download the file, upload it and give it back to me.",
            "uploadUrl",
            uploadUrl(wireMock.getHttpBaseUrl())));
  }
}
