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
import static io.camunda.connector.e2e.agenticai.aiagent.DocumentFlowFixtures.FINAL_TEXT;
import static io.camunda.connector.e2e.agenticai.aiagent.DocumentFlowFixtures.GET_AGAIN_CALL_ID;
import static io.camunda.connector.e2e.agenticai.aiagent.DocumentFlowFixtures.GET_CALL_ID;
import static io.camunda.connector.e2e.agenticai.aiagent.DocumentFlowFixtures.UPLOAD_TOOL;
import static io.camunda.connector.e2e.agenticai.aiagent.DocumentFlowFixtures.assertUploaded;
import static io.camunda.connector.e2e.agenticai.aiagent.DocumentFlowFixtures.downloadTurn;
import static io.camunda.connector.e2e.agenticai.aiagent.DocumentFlowFixtures.finalTurn;
import static io.camunda.connector.e2e.agenticai.aiagent.DocumentFlowFixtures.getDocumentTurn;
import static io.camunda.connector.e2e.agenticai.aiagent.DocumentFlowFixtures.servedFile;
import static io.camunda.connector.e2e.agenticai.aiagent.DocumentFlowFixtures.stubUploadEndpoint;
import static io.camunda.connector.e2e.agenticai.aiagent.DocumentFlowFixtures.uploadTurn;
import static io.camunda.connector.e2e.agenticai.aiagent.DocumentFlowFixtures.uploadUrl;
import static io.camunda.connector.e2e.agenticai.aiagent.DocumentFlowFixtures.withUploadTool;
import static io.camunda.connector.e2e.agenticai.aiagent.ToolCallResultDocumentAssertions.assertDocumentContentBlockJson;
import static io.camunda.connector.e2e.agenticai.aiagent.wiremock.DocumentIdPlaceholderTransformer.documentId;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.connector.agenticai.aiagent.model.tool.DocumentParameter;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.openai.OpenAiCompletionsChatModelStubs;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.openai.OpenAiCompletionsChatModelStubs.Turn;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.openai.OpenAiCompletionsRecordedConversation;
import io.camunda.connector.e2e.agenticai.assertj.AgentResponseAssert;
import io.camunda.connector.test.utils.annotation.SlowTest;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Document flow of the AI Agent Task against a scripted model: a tool returns a document, the model
 * loads it and passes its handle to a tool that uploads it. The process is the default one with the
 * upload tool added.
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

    final var zeebeTest =
        awaitProcessCompletion(
            createProcessInstance(
                Map.of(
                    "userPrompt",
                    "Download the file, upload it and give it back to me.",
                    "uploadUrl",
                    uploadUrl(wireMock.getHttpBaseUrl()))));

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
}
