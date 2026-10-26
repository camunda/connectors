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
import static io.camunda.connector.e2e.agenticai.aiagent.DocumentFlowFixtures.finalTurn;
import static io.camunda.connector.e2e.agenticai.aiagent.DocumentFlowFixtures.uploadUrl;
import static io.camunda.connector.e2e.agenticai.aiagent.ToolCallResultDocumentAssertions.parseDocumentTag;
import static io.camunda.connector.e2e.agenticai.aiagent.wiremock.DocumentIdPlaceholderTransformer.documentId;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.connector.agenticai.aiagent.api.tool.RuntimeTool;
import io.camunda.connector.agenticai.aiagent.api.tool.RuntimeToolCall;
import io.camunda.connector.agenticai.aiagent.api.tool.RuntimeToolContext;
import io.camunda.connector.agenticai.aiagent.api.tool.RuntimeToolResult;
import io.camunda.connector.agenticai.aiagent.model.document.DocumentSource;
import io.camunda.connector.agenticai.aiagent.model.message.content.DocumentContent;
import io.camunda.connector.agenticai.aiagent.model.message.content.TextContent;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolDefinition;
import io.camunda.connector.api.document.DocumentCreationRequest;
import io.camunda.connector.api.document.DocumentFactory;
import io.camunda.connector.e2e.agenticai.aiagent.DocumentFlowFixtures;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.openai.OpenAiCompletionsChatModelStubs;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.openai.OpenAiCompletionsChatModelStubs.ToolCall;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.openai.OpenAiCompletionsChatModelStubs.Turn;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.openai.OpenAiCompletionsRecordedConversation;
import io.camunda.connector.e2e.agenticai.assertj.AgentSubProcessResponseAssert;
import io.camunda.connector.test.utils.annotation.SlowTest;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.Resource;

/**
 * A runtime tool of a custom extension returns a document the agent did not know: in a batch of
 * tool calls the agent answers entirely itself, the model reads the tag of the document and never
 * the store id, the content hash or an URL, the document is registered, and a later call of a tool
 * of the process takes the document by its tag.
 */
@SlowTest
@Import(AgentSubProcessRuntimeToolDocumentTests.ReportRuntimeToolConfiguration.class)
public class AgentSubProcessRuntimeToolDocumentTests extends BaseAgentSubProcessTest {

  private static final String CREATE_CALL_ID = "call_create_report";
  private static final String UPLOAD_CALL_ID = "call_upload_report";
  private static final String REPORT_TOOL = "acme_create_report";
  private static final String REPORT_FILE = "report.txt";

  /** Registers the runtime tool of the test next to the built-in tools. */
  @TestConfiguration
  static class ReportRuntimeToolConfiguration {

    @Bean
    RuntimeTool acmeCreateReportRuntimeTool(DocumentFactory documentFactory) {
      return new ReportRuntimeTool(documentFactory);
    }
  }

  /** Creates a new document on every call and returns it with a sentence. */
  private record ReportRuntimeTool(DocumentFactory documentFactory) implements RuntimeTool {

    @Override
    public ToolDefinition definition() {
      final var schema = new LinkedHashMap<String, Object>();
      schema.put("type", "object");
      schema.put("properties", new LinkedHashMap<String, Object>());
      return new ToolDefinition(REPORT_TOOL, "Creates a report document.", schema);
    }

    @Override
    public RuntimeToolResult call(RuntimeToolCall call, RuntimeToolContext context) {
      final var report =
          documentFactory.create(
              DocumentCreationRequest.from("quarterly numbers".getBytes(StandardCharsets.UTF_8))
                  .contentType("text/plain")
                  .fileName(REPORT_FILE)
                  .build());
      return RuntimeToolResult.success(
          List.of(
              TextContent.textContent("Created the report."),
              DocumentContent.documentContent(report)));
    }
  }

  @Value("classpath:agentic-ai-ahsp-connectors-documents.bpmn")
  private Resource documentsProcess;

  @Override
  protected Resource testProcess() {
    return documentsProcess;
  }

  @BeforeEach
  void setUpUploadEndpoint() {
    DocumentFlowFixtures.stubUploadEndpoint();
  }

  @Test
  void aDocumentOfARuntimeToolReachesTheModelAsItsTagOnlyAndIsRegistered() throws Exception {
    OpenAiCompletionsChatModelStubs.stubConversation(
        Turn.toolCalls(
            "I will create a report.", 10, 20, ToolCall.of(CREATE_CALL_ID, REPORT_TOOL, "{}")),
        Turn.toolCalls(
            "I will upload the report.",
            10,
            20,
            ToolCall.of(
                UPLOAD_CALL_ID,
                "Upload_A_File",
                "{\"document\": \"%s\"}".formatted(DocumentFlowFixtures.tag(documentId(0))))),
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
    // the tag of the document
    final var afterCreate = recorded.requests().get(1).messages();
    assertThat(afterCreate)
        .extracting(m -> m.role())
        .containsExactly("system", "user", "assistant", "tool");
    assertThat(afterCreate.get(3).toolCallId()).isEqualTo(CREATE_CALL_ID);
    final var resultLines = afterCreate.get(3).content().split("\n");
    assertThat(resultLines).hasSize(2);
    assertThat(resultLines[0]).isEqualTo("Created the report.");
    final var tag = parseDocumentTag(resultLines[1]);
    assertThat(tag.fileName()).isEqualTo(REPORT_FILE);
    assertThat(tag.contentType()).isEqualTo("text/plain");
    // no store id, content hash or URL, in no request
    assertNoDocumentInternalsInLlmRequests();

    // the document is known to the agent: the process tool takes it by its tag
    assertThat(recorded.requests().get(2).messages().getLast().toolCallId())
        .isEqualTo(UPLOAD_CALL_ID);
    assertThat(DocumentFlowFixtures.uploadCount()).isEqualTo(1);

    assertAgentResponse(
        zeebeTest,
        agentResponse -> {
          AgentSubProcessResponseAssert.assertThat(agentResponse)
              .isReady()
              .hasResponseText(FINAL_TEXT);
          assertThat(agentResponse.context().documents())
              .singleElement()
              .satisfies(
                  entry -> {
                    assertThat(entry.id()).isEqualTo(tag.id());
                    assertThat(entry.source().type()).isEqualTo(DocumentSource.Type.TOOL_CALL);
                    assertThat(entry.source().toolName()).isEqualTo(REPORT_TOOL);
                    assertThat(entry.source().toolCallId()).isEqualTo(CREATE_CALL_ID);
                    // the reference stays in the registry, the model never reads it
                    assertThat(entry.reference()).containsKey("storeId");
                  });
        });
  }
}
