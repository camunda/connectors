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
package io.camunda.connector.e2e.agenticai.aiagent.langchain4j.jobworker;

import static io.camunda.connector.e2e.agenticai.aiagent.AiAgentTestFixtures.FEEDBACK_LOOP_RESPONSE_TEXT;
import static io.camunda.connector.e2e.agenticai.aiagent.ToolCallResultDocumentAssertions.assertDocumentContentBlock;
import static io.camunda.connector.e2e.agenticai.aiagent.ToolCallResultDocumentAssertions.assertExtractedDocumentsUserMessage;
import static io.camunda.connector.e2e.agenticai.aiagent.ToolCallResultDocumentAssertions.parseDocumentReference;
import static io.camunda.connector.e2e.agenticai.aiagent.ToolCallResultDocumentAssertions.parseExternalDocumentReference;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import io.camunda.connector.agenticai.aiagent.model.AgentMetrics;
import io.camunda.connector.e2e.agenticai.aiagent.ToolCallResultDocumentAssertions.ExtractedDocument;
import io.camunda.connector.e2e.agenticai.aiagent.langchain4j.wiremock.OpenAiChatModelStubs;
import io.camunda.connector.e2e.agenticai.aiagent.langchain4j.wiremock.OpenAiChatModelStubs.ToolCall;
import io.camunda.connector.e2e.agenticai.aiagent.langchain4j.wiremock.OpenAiChatModelStubs.Turn;
import io.camunda.connector.e2e.agenticai.aiagent.langchain4j.wiremock.RecordedLlmConversation;
import io.camunda.connector.e2e.agenticai.assertj.JobWorkerAgentResponseAssert;
import io.camunda.connector.test.utils.annotation.SlowTest;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@SlowTest
public class L4JAiAgentJobWorkerToolCallingTests extends BaseWireMockL4JAiAgentJobWorkerTest {

  @Test
  void executesAgentWithToolCallingAndUserFeedback() throws Exception {
    final var zeebeTest =
        testInteractionWithToolsAndUserFeedbackLoops(
            e -> e,
            FEEDBACK_LOOP_RESPONSE_TEXT,
            true,
            (agentResponse) ->
                JobWorkerAgentResponseAssert.assertThat(agentResponse)
                    .hasResponseMessageText(FEEDBACK_LOOP_RESPONSE_TEXT)
                    .hasResponseText(FEEDBACK_LOOP_RESPONSE_TEXT)
                    .hasNoResponseJson());

    // Inner-instance variables must not leak to the process-instance root scope when a tool with
    // a <zeebe:output> mapping is executed (regression camunda/camunda#51939). The connectors
    // BPMN's SuperfluxProduct uses such an output mapping, so this is a real regression detector.
    assertNoToolCallVariableLeakToProcessScope(zeebeTest);
  }

  @ParameterizedTest
  @CsvSource({
    "test.csv,text,text/csv",
    "test.gif,base64,image/gif",
    "test.jpg,base64,image/jpeg",
    "test.json,text,application/json",
    "test.pdf,base64,application/pdf",
    "test.png,base64,image/png",
    "test.txt,text,text/plain",
    "test.webp,base64,image/webp",
    "test.xml,text,application/xml",
    "test.yaml,text,application/yaml"
  })
  void supportsDocumentResponsesFromToolCalls(
      String filename, String type, String mimeType, WireMockRuntimeInfo wireMock)
      throws Exception {
    final var initialUserPrompt = "Go and download a document!";
    final var aiToolCallText =
        "The user asked me to download a document. I will call the Download_A_File tool to do so.";
    final var aiResponseAfterToolText =
        "I loaded a document and learned that it contains interesting data. Anything specific you want to know?";
    final var aiFinalResponseText = "The content type is '%s'".formatted(mimeType);

    OpenAiChatModelStubs.stubConversation(
        Turn.toolCalls(
            aiToolCallText,
            10,
            20,
            ToolCall.of(
                "aaa111",
                "Download_A_File",
                "{\"url\": \"%s\"}".formatted(wireMock.getHttpBaseUrl() + "/" + filename))),
        Turn.text(aiResponseAfterToolText, 100, 200),
        Turn.text(aiFinalResponseText, 11, 22));

    enqueueUserFeedback(userFollowUpFeedback("What is the content type?"), userSatisfiedFeedback());

    final var zeebeTest =
        createProcessInstance(
                elementTemplate ->
                    elementTemplate.property("retryCount", "3").property("retryBackoff", "PT2S"),
                Map.of("userPrompt", initialUserPrompt))
            .waitForProcessCompletion();

    final var recorded = RecordedLlmConversation.recorded();
    assertThat(recorded.modelCallCount()).isEqualTo(3);

    final var lastMessages = recorded.lastRequest().messages();
    assertThat(lastMessages).hasSize(7);

    assertThat(lastMessages.get(0).path("role").asText()).isEqualTo("system");
    assertThat(lastMessages.get(1).path("role").asText()).isEqualTo("user");
    assertThat(lastMessages.get(2).path("role").asText()).isEqualTo("assistant");

    // tool result: document reference serialized as JSON
    final var toolResultText = lastMessages.get(3).path("content").asText();
    final var documentReference = parseDocumentReference(toolResultText);
    assertThat(lastMessages.get(3).path("role").asText()).isEqualTo("tool");
    assertThat(lastMessages.get(3).path("tool_call_id").asText()).isEqualTo("aaa111");
    assertThat(documentReference.metadata().contentType()).isEqualTo(mimeType);

    // document user message: extracted document content (decoded from wire format)
    assertExtractedDocumentsUserMessage(
        lastMessages.get(4),
        ExtractedDocument.forToolCall(
            "aaa111",
            "Download_A_File",
            documentReference,
            content -> assertDocumentContentBlock(content, type, mimeType)));

    assertThat(lastMessages.get(5).path("role").asText()).isEqualTo("assistant");
    assertThat(lastMessages.get(6).path("role").asText()).isEqualTo("user");
    assertThat(lastMessages.get(6).path("content").asText()).isEqualTo("What is the content type?");

    assertAgentResponse(
        zeebeTest,
        agentResponse ->
            JobWorkerAgentResponseAssert.assertThat(agentResponse)
                .isReady()
                .hasMetrics(new AgentMetrics(3, new AgentMetrics.TokenUsage(121, 242), 1))
                .hasResponseMessageText(aiFinalResponseText)
                .hasResponseText(aiFinalResponseText));

    assertThat(userFeedbackJobWorkerCounter.get()).isEqualTo(2);
  }

  @Test
  void supportsExternalDocumentReferenceResponsesFromToolCalls(WireMockRuntimeInfo wireMock)
      throws Exception {
    final var initialUserPrompt = "Reference an external document!";
    final var docUrl = wireMock.getHttpBaseUrl() + "/test.pdf";
    final var docName = "Quarterly Report";
    final var aiFinalResponseText = "Referenced the external document.";

    OpenAiChatModelStubs.stubConversation(
        Turn.toolCalls(
            "I will reference an externally hosted file.",
            10,
            20,
            ToolCall.of(
                "ext111",
                "External_File_Reference",
                "{\"url\": \"%s\", \"name\": \"%s\"}".formatted(docUrl, docName))),
        Turn.text(aiFinalResponseText, 11, 22));

    enqueueUserFeedback(userSatisfiedFeedback());

    final var zeebeTest =
        createProcessInstance(
                elementTemplate ->
                    elementTemplate.property("retryCount", "3").property("retryBackoff", "PT2S"),
                Map.of("userPrompt", initialUserPrompt))
            .waitForProcessCompletion();

    final var recorded = RecordedLlmConversation.recorded();
    assertThat(recorded.modelCallCount()).isEqualTo(2);

    final var lastMessages = recorded.lastRequest().messages();
    assertThat(lastMessages).hasSize(5);

    assertThat(lastMessages.get(0).path("role").asText()).isEqualTo("system");
    assertThat(lastMessages.get(1).path("role").asText()).isEqualTo("user");
    assertThat(lastMessages.get(2).path("role").asText()).isEqualTo("assistant");

    // tool result: external document reference serialized as { url, name }
    final var toolResultText = lastMessages.get(3).path("content").asText();
    final var externalRef = parseExternalDocumentReference(toolResultText);
    assertThat(externalRef.url()).isEqualTo(docUrl);
    assertThat(externalRef.name()).isEqualTo(docName);

    // document user message: external doc rendered with content block
    assertExtractedDocumentsUserMessage(
        lastMessages.get(4),
        ExtractedDocument.forExternalToolCall(
            "ext111",
            "External_File_Reference",
            externalRef,
            content -> assertDocumentContentBlock(content, "base64", "application/pdf")));

    assertAgentResponse(
        zeebeTest,
        agentResponse ->
            JobWorkerAgentResponseAssert.assertThat(agentResponse)
                .isReady()
                .hasMetrics(new AgentMetrics(2, new AgentMetrics.TokenUsage(21, 42), 1))
                .hasResponseMessageText(aiFinalResponseText)
                .hasResponseText(aiFinalResponseText));

    assertThat(userFeedbackJobWorkerCounter.get()).isEqualTo(1);
  }
}
