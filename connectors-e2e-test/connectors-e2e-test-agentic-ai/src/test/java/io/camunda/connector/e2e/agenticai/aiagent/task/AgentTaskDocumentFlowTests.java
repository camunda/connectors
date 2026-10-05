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

import static io.camunda.connector.e2e.agenticai.aiagent.DocumentFlowAssertions.assertNoDocumentInternalsInLlmRequests;
import static io.camunda.connector.e2e.agenticai.aiagent.DocumentFlowFixtures.DOWNLOADED_FILE;
import static io.camunda.connector.e2e.agenticai.aiagent.DocumentFlowFixtures.DOWNLOAD_CALL_ID;
import static io.camunda.connector.e2e.agenticai.aiagent.DocumentFlowFixtures.FINAL_TEXT;
import static io.camunda.connector.e2e.agenticai.aiagent.DocumentFlowFixtures.PING_CALL_ID;
import static io.camunda.connector.e2e.agenticai.aiagent.DocumentFlowFixtures.RETURN_CALL_ID;
import static io.camunda.connector.e2e.agenticai.aiagent.DocumentFlowFixtures.UPLOAD_CALL_ID;
import static io.camunda.connector.e2e.agenticai.aiagent.DocumentFlowFixtures.assertCamundaDocumentReference;
import static io.camunda.connector.e2e.agenticai.aiagent.DocumentFlowFixtures.assertReportedDocumentReference;
import static io.camunda.connector.e2e.agenticai.aiagent.DocumentFlowFixtures.assertUploaded;
import static io.camunda.connector.e2e.agenticai.aiagent.DocumentFlowFixtures.downloadTurn;
import static io.camunda.connector.e2e.agenticai.aiagent.DocumentFlowFixtures.finalTurn;
import static io.camunda.connector.e2e.agenticai.aiagent.DocumentFlowFixtures.returnDocumentsWithPingTurn;
import static io.camunda.connector.e2e.agenticai.aiagent.DocumentFlowFixtures.returnDocumentsWithSlowToolTurn;
import static io.camunda.connector.e2e.agenticai.aiagent.DocumentFlowFixtures.roundTripTurns;
import static io.camunda.connector.e2e.agenticai.aiagent.DocumentFlowFixtures.servedFile;
import static io.camunda.connector.e2e.agenticai.aiagent.DocumentFlowFixtures.stubUploadEndpoint;
import static io.camunda.connector.e2e.agenticai.aiagent.DocumentFlowFixtures.unknownUploadWithPingTurn;
import static io.camunda.connector.e2e.agenticai.aiagent.DocumentFlowFixtures.uploadCount;
import static io.camunda.connector.e2e.agenticai.aiagent.DocumentFlowFixtures.uploadTurn;
import static io.camunda.connector.e2e.agenticai.aiagent.DocumentFlowFixtures.uploadUrl;
import static io.camunda.connector.e2e.agenticai.aiagent.ToolCallResultDocumentAssertions.parseDocumentTag;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.connector.e2e.ZeebeTest;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.openai.OpenAiCompletionsChatModelStubs;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.openai.OpenAiCompletionsChatModelStubs.Turn;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.openai.OpenAiCompletionsRecordedConversation;
import io.camunda.connector.e2e.agenticai.assertj.AgentInstanceEngineVerifier;
import io.camunda.connector.e2e.agenticai.assertj.AgentResponseAssert;
import io.camunda.connector.test.utils.annotation.SlowTest;
import io.camunda.process.test.api.CamundaAssert;
import io.camunda.process.test.api.CamundaProcessTestContext;
import io.camunda.process.test.api.assertions.JobSelectors;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;

/**
 * Document flow of the AI Agent Task against a scripted model: a document parameter of a tool (a
 * real HTTP connector that uploads the document), unknown ids and returning documents.
 */
@SlowTest
public class AgentTaskDocumentFlowTests extends BaseAgentTaskTest {

  @Value("classpath:agentic-ai-connectors-documents.bpmn")
  private Resource documentsProcess;

  @Autowired private CamundaProcessTestContext processTestContext;

  @Override
  protected Resource testProcess() {
    return documentsProcess;
  }

  @BeforeEach
  void setUpDocumentFlow() {
    stubUploadEndpoint();
  }

  @Test
  void passesADownloadedDocumentToAToolAndReturnsIt() throws Exception {
    OpenAiCompletionsChatModelStubs.stubConversation(
        roundTripTurns(wireMock.getHttpBaseUrl()).toArray(Turn[]::new));

    final var zeebeTest = awaitProcessCompletion(startProcess());

    final var recorded = OpenAiCompletionsRecordedConversation.recorded();
    assertThat(recorded.modelCallCount()).isEqualTo(4);
    assertThat(recorded.requests().getFirst().toolNames())
        .containsExactlyInAnyOrder(
            "Download_A_File", "Upload_A_File", "Ping", "Slow_Tool", "return_documents");

    final var downloadResult = recorded.requests().get(1).messages().get(3);
    assertThat(downloadResult.toolCallId()).isEqualTo(DOWNLOAD_CALL_ID);
    final var documentId = parseDocumentTag(downloadResult.content()).id();

    assertUploaded(servedFile(DOWNLOADED_FILE));
    assertThat(recorded.requests().get(2).messages().getLast().toolCallId())
        .isEqualTo(UPLOAD_CALL_ID);

    assertAgentResponse(
        zeebeTest,
        agentResponse -> {
          AgentResponseAssert.assertThat(agentResponse).isReady().hasNoToolCalls();
          assertThat(agentResponse.documents()).hasSize(1);
          assertCamundaDocumentReference(agentResponse.documents().getFirst(), documentId);
          assertThat(agentResponse.context().returnDocumentIds()).isEmpty();
        });

    assertNoDocumentInternalsInLlmRequests();
  }

  @Test
  void unknownDocumentIdIsAnsweredInTheLoopAndTheModelRetries() throws Exception {
    OpenAiCompletionsChatModelStubs.stubConversation(
        downloadTurn(wireMock.getHttpBaseUrl()),
        uploadTurn("no-such-document"),
        uploadTurn(),
        finalTurn());

    final var zeebeTest = awaitProcessCompletion(startProcess());

    final var recorded = OpenAiCompletionsRecordedConversation.recorded();
    assertThat(recorded.modelCallCount()).isEqualTo(4);

    final var errorResult = recorded.requests().get(2).messages().getLast();
    assertThat(errorResult.toolCallId()).isEqualTo(UPLOAD_CALL_ID);
    assertThat(errorResult.content())
        .startsWith("Unknown document id 'no-such-document' for parameter 'document'.")
        .contains("Known documents:");

    assertThat(uploadCount()).isEqualTo(1);
    assertUploaded(servedFile(DOWNLOADED_FILE));

    assertAgentResponse(
        zeebeTest,
        agentResponse ->
            AgentResponseAssert.assertThat(agentResponse).isReady().hasResponseText(FINAL_TEXT));
    assertNoDocumentInternalsInLlmRequests();
  }

  @Test
  void mixedBatchOfReturnDocumentsAndARealToolCostsNoExtraModelCall() throws Exception {
    OpenAiCompletionsChatModelStubs.stubConversation(
        downloadTurn(wireMock.getHttpBaseUrl()), returnDocumentsWithPingTurn(), finalTurn());

    final var zeebeTest = awaitProcessCompletion(startProcess());

    final var recorded = OpenAiCompletionsRecordedConversation.recorded();
    assertThat(recorded.modelCallCount()).isEqualTo(3);

    final var documentId =
        parseDocumentTag(recorded.requests().get(1).messages().get(3).content()).id();
    final var messages = recorded.lastRequest().messages();
    final var results = messages.subList(messages.size() - 2, messages.size());
    assertThat(results)
        .extracting(m -> m.toolCallId())
        .containsExactly(RETURN_CALL_ID, PING_CALL_ID);
    assertThat(results.get(0).content()).contains(documentId);
    assertThat(results.get(1).content()).contains("pong");

    assertAgentResponse(
        zeebeTest,
        agentResponse -> {
          AgentResponseAssert.assertThat(agentResponse)
              .isReady()
              .hasNoToolCalls()
              .hasResponseText(FINAL_TEXT);
          assertThat(agentResponse.documents()).hasSize(1);
          assertCamundaDocumentReference(agentResponse.documents().getFirst(), documentId);
          assertThat(agentResponse.context().pendingLocalToolCallResults()).isEmpty();
        });
    assertNoDocumentInternalsInLlmRequests();
  }

  /**
   * The history of the agent instance shows the reference of a document where the model passed its
   * id: in the call of the assistant item and in the arguments of the result item, for a tool of
   * the process and for the local tool.
   */
  @Test
  void agentInstanceHistoryShowsTheDocumentReferenceInTheToolCallArguments() throws Exception {
    OpenAiCompletionsChatModelStubs.stubConversation(
        roundTripTurns(wireMock.getHttpBaseUrl()).toArray(Turn[]::new));

    final var zeebeTest = awaitProcessCompletion(startProcess());

    final var recorded = OpenAiCompletionsRecordedConversation.recorded();
    final var documentId =
        parseDocumentTag(recorded.requests().get(1).messages().get(3).content()).id();
    // the model and the persisted conversation keep the id
    assertThat(recorded.requests().get(2).messages().toString()).contains(documentId);

    final var agentInstanceKey = new AtomicLong();
    assertAgentResponse(
        zeebeTest,
        agentResponse ->
            agentInstanceKey.set(agentResponse.context().metadata().agentInstanceKey()));

    AgentInstanceEngineVerifier.verify(camundaClient, agentInstanceKey.get())
        .hasToolCallArguments(
            "Upload_A_File",
            arguments -> assertReportedDocumentReference(arguments.get("document"), documentId))
        .hasToolCallArguments(
            "return_documents",
            arguments ->
                assertThat((List<?>) arguments.get("ids"))
                    .singleElement()
                    .satisfies(reference -> assertReportedDocumentReference(reference, documentId)))
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
        downloadTurn(wireMock.getHttpBaseUrl()), returnDocumentsWithSlowToolTurn(), finalTurn());

    final var zeebeTest = startProcess();
    final var processInstance = zeebeTest.getProcessInstanceEvent();
    CamundaAssert.assertThat(processInstance).hasActiveElements("Slow_Tool");
    final var agentInstanceKey =
        AgentInstanceEngineVerifier.awaitAgentInstanceKey(
            camundaClient, processInstance.getProcessInstanceKey());

    // the slow tool has not answered, the local result is already reported
    AgentInstanceEngineVerifier.verify(camundaClient, agentInstanceKey)
        .hasExactlyToolResultsInOrder("Download_A_File", "return_documents")
        .verify();
    CamundaAssert.assertThat(processInstance).hasActiveElements("Slow_Tool");

    processTestContext.completeJob(
        JobSelectors.byElementId("Slow_Tool")
            .and(JobSelectors.byProcessInstanceKey(processInstance.getProcessInstanceKey())),
        Map.of("toolCallResult", "slow tool done"));
    awaitProcessCompletion(zeebeTest);

    // the next job reported the local result again: no duplicate, call order kept
    AgentInstanceEngineVerifier.verify(camundaClient, agentInstanceKey)
        .hasExactlyToolResultsInOrder("Download_A_File", "return_documents", "Slow_Tool")
        .verify();
  }

  @Test
  void mixedBatchWithAnUnknownDocumentIdRunsTheValidCallAndAnswersTheInvalidOne() throws Exception {
    OpenAiCompletionsChatModelStubs.stubConversation(
        downloadTurn(wireMock.getHttpBaseUrl()),
        unknownUploadWithPingTurn(),
        uploadTurn(),
        finalTurn());

    final var zeebeTest = awaitProcessCompletion(startProcess());

    final var recorded = OpenAiCompletionsRecordedConversation.recorded();
    assertThat(recorded.modelCallCount()).isEqualTo(4);

    final var messages = recorded.requests().get(2).messages();
    final var results = messages.subList(messages.size() - 2, messages.size());
    assertThat(results)
        .extracting(m -> m.toolCallId())
        .containsExactly(UPLOAD_CALL_ID, PING_CALL_ID);
    assertThat(results.get(0).content())
        .startsWith("Unknown document id 'no-such-document' for parameter 'document'.");
    assertThat(results.get(1).content()).contains("pong");

    assertThat(uploadCount()).isEqualTo(1);
    assertUploaded(servedFile(DOWNLOADED_FILE));

    assertAgentResponse(
        zeebeTest,
        agentResponse -> {
          AgentResponseAssert.assertThat(agentResponse).isReady().hasResponseText(FINAL_TEXT);
          assertThat(agentResponse.context().pendingLocalToolCallResults()).isEmpty();
        });
    assertNoDocumentInternalsInLlmRequests();
  }

  private ZeebeTest startProcess() throws Exception {
    return createProcessInstance(
        elementTemplate -> elementTemplate.property("data.response.includeDocuments", "=true"),
        Map.of(
            "userPrompt",
            "Download the file, upload it and give it back to me.",
            "uploadUrl",
            uploadUrl(wireMock.getHttpBaseUrl())));
  }
}
