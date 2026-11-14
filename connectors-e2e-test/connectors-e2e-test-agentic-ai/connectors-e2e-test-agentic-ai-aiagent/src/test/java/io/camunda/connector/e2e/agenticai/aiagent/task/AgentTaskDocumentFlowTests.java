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
package io.camunda.connector.e2e.agenticai.aiagent.task;

import static io.camunda.connector.e2e.agenticai.aiagent.DocumentFlowAssertions.assertContentReturnedOnceAndPassedByIdFlow;
import static io.camunda.connector.e2e.agenticai.aiagent.DocumentFlowAssertions.assertDownloadResultInHistory;
import static io.camunda.connector.e2e.agenticai.aiagent.DocumentFlowAssertions.assertNoDocumentInternalsInLlmRequests;
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
import static io.camunda.connector.e2e.agenticai.aiagent.DocumentFlowFixtures.returnDocumentWithSuperfluxTurn;
import static io.camunda.connector.e2e.agenticai.aiagent.DocumentFlowFixtures.roundTripTurns;
import static io.camunda.connector.e2e.agenticai.aiagent.DocumentFlowFixtures.servedFile;
import static io.camunda.connector.e2e.agenticai.aiagent.DocumentFlowFixtures.stubUploadEndpoint;
import static io.camunda.connector.e2e.agenticai.aiagent.DocumentFlowFixtures.uploadTurn;
import static io.camunda.connector.e2e.agenticai.aiagent.DocumentFlowFixtures.uploadUrl;
import static io.camunda.connector.e2e.agenticai.aiagent.DocumentFlowFixtures.withUploadTool;
import static io.camunda.connector.e2e.agenticai.aiagent.ToolCallResultDocumentAssertions.assertDocumentContentBlockJson;
import static io.camunda.connector.e2e.agenticai.aiagent.ToolCallResultDocumentAssertions.parseDocumentHandle;
import static io.camunda.connector.e2e.agenticai.aiagent.wiremock.DocumentIdPlaceholderTransformer.documentId;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.connector.agenticai.aiagent.model.document.AgentDocumentHandle;
import io.camunda.connector.agenticai.aiagent.model.document.AgentDocumentId;
import io.camunda.connector.agenticai.aiagent.model.document.ResponseDocumentSelection;
import io.camunda.connector.agenticai.aiagent.model.tool.DocumentParameter;
import io.camunda.connector.e2e.ZeebeTest;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.openai.OpenAiCompletionsChatModelStubs;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.openai.OpenAiCompletionsChatModelStubs.Turn;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.openai.OpenAiCompletionsRecordedConversation;
import io.camunda.connector.e2e.agenticai.assertj.AgentInstanceEngineVerifier;
import io.camunda.connector.e2e.agenticai.assertj.AgentResponseAssert;
import io.camunda.connector.test.utils.annotation.SlowTest;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;

/**
 * Document flow of the AI Agent Task against a scripted model: a document passed from tool to tool,
 * loaded, returned and sent with a batch that holds a real call. The process is the default one
 * with the upload tool added.
 */
@SlowTest
public class AgentTaskDocumentFlowTests extends BaseAgentTaskTest {

  private static final String AD_HOC_SUB_PROCESS_ID = "Agent_Tools";
  private static final String FILE = "test.txt";

  @Override
  protected BpmnModelInstance customizeModel(BpmnModelInstance model) {
    return withUploadTool(model, AD_HOC_SUB_PROCESS_ID);
  }

  @BeforeEach
  void setUpDocumentFlow() {
    stubUploadEndpoint();
    enqueueUserFeedback(userSatisfiedFeedback());
  }

  /**
   * A tool returns a document, the model loads it twice and passes its handle to a tool: the first
   * load puts the content next to the handle, the second one is answered without loading again, and
   * the tool receives the real bytes. The model provider sees the object schema of a document
   * handle for the document parameter of the tool, and the history shows the result of the tool
   * with the handle nested in it.
   */
  @Test
  void loadsAFetchedDocumentOnceAndPassesItsHandleToATool() throws Exception {
    final var turns =
        List.of(
            downloadTurn(wireMock.getHttpBaseUrl(), FILE),
            getDocumentTurn(GET_CALL_ID, documentId(0)),
            getDocumentTurn(GET_AGAIN_CALL_ID, documentId(0)),
            uploadTurn(),
            finalTurn());
    OpenAiCompletionsChatModelStubs.stubConversation(turns.toArray(Turn[]::new));

    final var zeebeTest = awaitProcessCompletion(startProcess());

    final var recorded = OpenAiCompletionsRecordedConversation.recorded();
    assertThat(recorded.modelCallCount()).isEqualTo(turns.size());
    assertContentReturnedOnceAndPassedByIdFlow(
        recorded.requests(), block -> assertDocumentContentBlockJson(block, FILE));
    assertUploaded(servedFile(FILE));

    // the model provider sees the object schema of a document handle
    final var upload =
        recorded.lastRequest().toolDefinitions().stream()
            .filter(tool -> UPLOAD_TOOL.equals(tool.name()))
            .toList();
    assertThat(upload).hasSize(1);
    assertThat(upload.getFirst().inputSchema())
        .extractingByKey("properties")
        .asInstanceOf(InstanceOfAssertFactories.MAP)
        .containsEntry("document", DocumentParameter.documentSchema("The document to upload."));
    assertThat(recorded.lastRequest().toolNames()).contains("c8__load_document");
    assertNoDocumentInternalsInLlmRequests(recorded.providerNeutralRequests());

    final var agentInstanceKey = new AtomicLong();
    assertAgentResponse(
        zeebeTest,
        agentResponse -> {
          AgentResponseAssert.assertThat(agentResponse)
              .isReady()
              .hasNoToolCalls()
              .hasResponseText(FINAL_TEXT);
          agentInstanceKey.set(agentResponse.context().metadata().agentInstanceKey().value());
        });

    assertDownloadResultInHistory(camundaClient, agentInstanceKey.get(), recorded.requests());
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

    final var zeebeTest = awaitProcessCompletion(startProcess());

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
          AgentResponseAssert.assertThat(agentResponse)
              .isReady()
              .hasNoToolCalls()
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
            UPLOAD_TOOL,
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
   * The valid {@code c8__return_document} call is answered by the agent and the real call of the
   * same message runs in the process. The model sees both results in the order of its calls and the
   * batch costs no extra model call: one call before the tools run and one after.
   */
  @Test
  void mixedBatchOfReturnDocumentAndARealToolCostsNoExtraModelCall() throws Exception {
    final var turns =
        List.of(
            downloadTurn(wireMock.getHttpBaseUrl()),
            returnDocumentWithSuperfluxTurn(),
            finalTurn());
    OpenAiCompletionsChatModelStubs.stubConversation(turns.toArray(Turn[]::new));

    final var zeebeTest = awaitProcessCompletion(startProcess());

    final var recorded = OpenAiCompletionsRecordedConversation.recorded();
    assertThat(recorded.modelCallCount()).isEqualTo(turns.size());

    final var documentId =
        parseDocumentHandle(recorded.requests().get(1).messages().get(3).content()).id().value();
    final var messages = recorded.lastRequest().messages();
    final var results = messages.subList(messages.size() - 2, messages.size());
    assertThat(results).extracting(m -> m.role()).containsExactly("tool", "tool");
    assertThat(results)
        .extracting(m -> m.toolCallId())
        .containsExactly(RETURN_CALL_ID, SUPERFLUX_CALL_ID);
    assertThat(results.get(0).content()).isEqualTo("Returned the document under the key 'report'.");
    assertThat(results.get(1).content()).isEqualTo(SUPERFLUX_RESULT);

    assertAgentResponse(
        zeebeTest,
        agentResponse -> {
          AgentResponseAssert.assertThat(agentResponse)
              .isReady()
              .hasNoToolCalls()
              .hasResponseText(FINAL_TEXT);
          assertResponseDocument(
              agentResponse.context(), agentResponse.responseDocuments(), documentId);
          assertThat(agentResponse.context().responseDocuments())
              .containsExactly(
                  new ResponseDocumentSelection(
                      RETURN_KEY, AgentDocumentId.of(documentId), RETURN_DESCRIPTION));
        });
    assertNoDocumentInternalsInLlmRequests(recorded.providerNeutralRequests());
  }

  private ZeebeTest startProcess() throws Exception {
    return createProcessInstance(
        Map.of(
            "userPrompt",
            "Download the file, upload it and give it back to me.",
            "uploadUrl",
            uploadUrl(wireMock.getHttpBaseUrl())));
  }
}
