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
import static io.camunda.connector.e2e.agenticai.aiagent.DocumentFlowFixtures.FINAL_TEXT;
import static io.camunda.connector.e2e.agenticai.aiagent.DocumentFlowFixtures.UPLOAD_TOOL;
import static io.camunda.connector.e2e.agenticai.aiagent.DocumentFlowFixtures.finalTurn;
import static io.camunda.connector.e2e.agenticai.aiagent.DocumentFlowFixtures.uploadUrl;
import static io.camunda.connector.e2e.agenticai.aiagent.DocumentFlowFixtures.withUploadTool;
import static io.camunda.connector.e2e.agenticai.aiagent.ToolCallResultDocumentAssertions.assertHandleOfTheOnlyRegisteredDocument;
import static io.camunda.connector.e2e.agenticai.aiagent.ToolCallResultDocumentAssertions.parseDocumentHandle;
import static io.camunda.connector.e2e.agenticai.aiagent.fixtures.AgentTestFixtures.AI_AGENT_ELEMENT_ID;
import static io.camunda.connector.e2e.agenticai.aiagent.wiremock.DocumentIdPlaceholderTransformer.documentId;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.connector.agenticai.aiagent.api.tool.RuntimeTool;
import io.camunda.connector.agenticai.aiagent.api.tool.RuntimeToolCall;
import io.camunda.connector.agenticai.aiagent.api.tool.RuntimeToolContext;
import io.camunda.connector.agenticai.aiagent.api.tool.RuntimeToolResult;
import io.camunda.connector.agenticai.aiagent.model.document.AgentDocument;
import io.camunda.connector.agenticai.aiagent.model.document.AgentDocumentOrigin;
import io.camunda.connector.agenticai.aiagent.model.message.content.DocumentContent;
import io.camunda.connector.agenticai.aiagent.model.message.content.TextContent;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolDefinition;
import io.camunda.connector.api.document.Document;
import io.camunda.connector.api.document.DocumentCreationRequest;
import io.camunda.connector.api.document.DocumentFactory;
import io.camunda.connector.e2e.agenticai.aiagent.DocumentFlowFixtures;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.openai.OpenAiCompletionsChatModelStubs;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.openai.OpenAiCompletionsChatModelStubs.ToolCall;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.openai.OpenAiCompletionsChatModelStubs.Turn;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.openai.OpenAiCompletionsRecordedConversation;
import io.camunda.connector.e2e.agenticai.assertj.AgentSubProcessResponseAssert;
import io.camunda.connector.test.utils.annotation.SlowTest;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

/**
 * A runtime tool of a custom extension returns a document the agent did not know: in a batch of
 * tool calls the agent answers entirely itself, the model reads the handle of the document and
 * never the store id, the content hash or an URL, the document is registered, and a later call of a
 * tool of the process takes the document by its handle.
 */
@SlowTest
@Import(AgentSubProcessRuntimeToolDocumentTests.ReportRuntimeToolConfiguration.class)
public class AgentSubProcessRuntimeToolDocumentTests extends BaseAgentSubProcessTest {

  private static final String CREATE_CALL_ID = "call_create_report";
  private static final String UPLOAD_CALL_ID = "call_upload_report";
  private static final String REPORT_TOOL = "acme_create_report";
  private static final String REPORT_FILE = "report.txt";
  private static final byte[] REPORT_BYTES = "quarterly numbers".getBytes(StandardCharsets.UTF_8);

  /** Registers the runtime tool of the test next to the built-in tools. */
  @TestConfiguration
  static class ReportRuntimeToolConfiguration {

    @Bean
    ReportRuntimeTool acmeCreateReportRuntimeTool(DocumentFactory documentFactory) {
      return new ReportRuntimeTool(documentFactory);
    }
  }

  /**
   * Creates a new document on every call and returns it with a sentence. It keeps the documents it
   * created, in call order.
   */
  static class ReportRuntimeTool implements RuntimeTool {

    private final DocumentFactory documentFactory;
    private final List<Document> created = new CopyOnWriteArrayList<>();

    ReportRuntimeTool(DocumentFactory documentFactory) {
      this.documentFactory = documentFactory;
    }

    List<Document> created() {
      return List.copyOf(created);
    }

    void reset() {
      created.clear();
    }

    @Override
    public ToolDefinition definition() {
      final var schema = new LinkedHashMap<String, Object>();
      schema.put("type", "object");
      schema.put("properties", new LinkedHashMap<String, Object>());
      return ToolDefinition.runtime(REPORT_TOOL, "Creates a report document.", schema);
    }

    @Override
    public RuntimeToolResult call(RuntimeToolContext context, RuntimeToolCall call) {
      final var report =
          documentFactory.create(
              DocumentCreationRequest.from(REPORT_BYTES)
                  .contentType("text/plain")
                  .fileName(REPORT_FILE)
                  .build());
      created.add(report);
      return RuntimeToolResult.success(
          List.of(
              TextContent.textContent("Created the report."),
              DocumentContent.documentContent(report)));
    }
  }

  @Override
  protected BpmnModelInstance customizeModel(BpmnModelInstance model) {
    return withUploadTool(model, AI_AGENT_ELEMENT_ID);
  }

  @Autowired private ReportRuntimeTool reportRuntimeTool;

  @BeforeEach
  void setUpUploadEndpoint() {
    DocumentFlowFixtures.stubUploadEndpoint();
    reportRuntimeTool.reset();
    enqueueUserFeedback(userSatisfiedFeedback());
  }

  @Test
  void aDocumentOfARuntimeToolReachesTheModelAsItsHandleOnlyAndIsRegistered() throws Exception {
    OpenAiCompletionsChatModelStubs.stubConversation(
        Turn.toolCalls(
            "I will create a report.", 10, 20, ToolCall.of(CREATE_CALL_ID, REPORT_TOOL, "{}")),
        Turn.toolCalls(
            "I will upload the report.",
            10,
            20,
            ToolCall.of(
                UPLOAD_CALL_ID,
                UPLOAD_TOOL,
                "{\"document\": %s}".formatted(DocumentFlowFixtures.handle(documentId(0))))),
        finalTurn());

    final var zeebeTest =
        awaitProcessCompletion(
            createProcessInstance(
                e -> e,
                Map.of(
                    "userPrompt",
                    "Create the report and upload it.",
                    "uploadUrl",
                    uploadUrl(wireMock.getHttpBaseUrl()))));

    final var recorded = OpenAiCompletionsRecordedConversation.recorded();
    assertThat(recorded.modelCallCount()).isEqualTo(3);

    // the call of the runtime tool is answered in the same job: the model reads the sentence and
    // the handle of the document
    final var afterCreate = recorded.requests().get(1).messages();
    assertThat(afterCreate)
        .extracting(m -> m.role())
        .containsExactly("system", "user", "assistant", "tool");
    assertThat(afterCreate.get(3).toolCallId()).isEqualTo(CREATE_CALL_ID);
    final var resultLines = afterCreate.get(3).content().split("\n");
    assertThat(resultLines).hasSize(2);
    assertThat(resultLines[0]).isEqualTo("Created the report.");
    final var handle = parseDocumentHandle(resultLines[1]);
    // no store id, content hash or URL, in no request
    assertNoDocumentInternalsInLlmRequests(recorded.providerNeutralRequests());

    // the document is known to the agent: the process tool takes it by its handle
    assertThat(recorded.requests().get(2).messages().getLast().toolCallId())
        .isEqualTo(UPLOAD_CALL_ID);
    assertThat(DocumentFlowFixtures.uploadCount()).isEqualTo(1);

    assertAgentResponse(
        zeebeTest,
        agentResponse -> {
          AgentSubProcessResponseAssert.assertThat(agentResponse)
              .isReady()
              .hasResponseText(FINAL_TEXT);
          // the registry holds the document the tool created, with the tool call as its origin,
          // and the model read the handle of that document, nothing else
          assertThat(reportRuntimeTool.created()).singleElement();
          final var expectedEntry =
              AgentDocument.of(
                  reportRuntimeTool.created().getFirst(),
                  AgentDocumentOrigin.toolCall(REPORT_TOOL, CREATE_CALL_ID));
          final var registered =
              assertHandleOfTheOnlyRegisteredDocument(
                  handle, agentResponse.context().documents(), REPORT_FILE, "text/plain", null);
          assertThat(registered).isEqualTo(expectedEntry);
        });
  }
}
