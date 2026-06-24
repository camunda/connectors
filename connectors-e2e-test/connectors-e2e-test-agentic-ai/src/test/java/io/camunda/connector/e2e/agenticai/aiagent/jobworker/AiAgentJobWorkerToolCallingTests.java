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
package io.camunda.connector.e2e.agenticai.aiagent.jobworker;

import static io.camunda.connector.e2e.agenticai.aiagent.AiAgentTestFixtures.FEEDBACK_LOOP_RESPONSE_TEXT;
import static io.camunda.connector.e2e.agenticai.aiagent.ToolCallResultDocumentAssertions.assertDocumentContentBlockJson;
import static io.camunda.connector.e2e.agenticai.aiagent.ToolCallResultDocumentAssertions.assertExtractedDocumentsUserMessage;
import static io.camunda.connector.e2e.agenticai.aiagent.ToolCallResultDocumentAssertions.parseDocumentTagAttributes;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.connector.agenticai.aiagent.model.AgentMetrics;
import io.camunda.connector.e2e.agenticai.aiagent.ToolCallResultDocumentAssertions.ExtractedDocument;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.openai.OpenAiCompletionsChatModelStubs;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.openai.OpenAiCompletionsChatModelStubs.ToolCall;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.openai.OpenAiCompletionsChatModelStubs.Turn;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.openai.OpenAiCompletionsRecordedConversation;
import io.camunda.connector.e2e.agenticai.assertj.JobWorkerAgentResponseAssert;
import io.camunda.connector.test.utils.annotation.SlowTest;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@SlowTest
public class AiAgentJobWorkerToolCallingTests extends BaseAiAgentJobWorkerTest {

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
  void supportsDocumentResponsesFromToolCalls(String filename, String type, String mimeType)
      throws Exception {
    final var initialUserPrompt = "Go and download a document!";
    final var aiToolCallText =
        "The user asked me to download a document. I will call the Download_A_File tool to do so.";
    final var aiResponseAfterToolText =
        "I loaded a document and learned that it contains interesting data. Anything specific you want to know?";
    final var aiFinalResponseText = "The content type is '%s'".formatted(mimeType);

    OpenAiCompletionsChatModelStubs.stubConversation(
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
        awaitProcessCompletion(
            createProcessInstance(
                elementTemplate ->
                    elementTemplate.property("retryCount", "3").property("retryBackoff", "PT2S"),
                Map.of("userPrompt", initialUserPrompt)));

    final var recorded = OpenAiCompletionsRecordedConversation.recorded();
    assertThat(recorded.modelCallCount()).isEqualTo(3);

    final var lastMessages = recorded.lastRequest().messages();
    assertThat(lastMessages).hasSize(7);

    assertThat(lastMessages.get(0).role()).isEqualTo("system");
    assertThat(lastMessages.get(1).role()).isEqualTo("user");
    assertThat(lastMessages.get(2).role()).isEqualTo("assistant");

    // Site-1: tool result message — document serialized as a JSON-encoded "<doc .../>" tag
    // (no tool attribution). The content is a JSON string value containing the XML tag.
    final var toolResultText = lastMessages.get(3).content();
    assertThat(lastMessages.get(3).role()).isEqualTo("tool");
    assertThat(lastMessages.get(3).toolCallId()).isEqualTo("aaa111");
    final var site1Attrs = parseDocumentTagAttributes(toolResultText);
    assertThat(site1Attrs.get("contentType")).isEqualTo(mimeType);

    // Site-2: synthetic user message — same id, plus toolName and toolCallId for correlation
    assertExtractedDocumentsUserMessage(
        lastMessages.get(4),
        ExtractedDocument.forToolCall(
            "aaa111",
            "Download_A_File",
            site1Attrs,
            block -> assertDocumentContentBlockJson(block, type, mimeType)));

    assertThat(lastMessages.get(5).role()).isEqualTo("assistant");
    assertThat(lastMessages.get(6).role()).isEqualTo("user");
    assertThat(lastMessages.get(6).content()).isEqualTo("What is the content type?");

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
  void supportsExternalDocumentReferenceResponsesFromToolCalls() throws Exception {
    final var initialUserPrompt = "Reference an external document!";
    final var docUrl = wireMock.getHttpBaseUrl() + "/test.pdf";
    final var docName = "Quarterly Report";
    final var aiFinalResponseText = "Referenced the external document.";

    OpenAiCompletionsChatModelStubs.stubConversation(
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
        awaitProcessCompletion(
            createProcessInstance(
                elementTemplate ->
                    elementTemplate.property("retryCount", "3").property("retryBackoff", "PT2S"),
                Map.of("userPrompt", initialUserPrompt)));

    final var recorded = OpenAiCompletionsRecordedConversation.recorded();
    assertThat(recorded.modelCallCount()).isEqualTo(2);

    final var lastMessages = recorded.lastRequest().messages();
    assertThat(lastMessages).hasSize(5);

    assertThat(lastMessages.get(0).role()).isEqualTo("system");
    assertThat(lastMessages.get(1).role()).isEqualTo("user");
    assertThat(lastMessages.get(2).role()).isEqualTo("assistant");

    // Site-1: tool result message — external document serialized as a JSON-encoded "<doc .../>"
    // tag (id is "ext-<sha256(url)[:12]>", no fileName/contentType for external refs).
    final var toolResultText = lastMessages.get(3).content();
    assertThat(lastMessages.get(3).role()).isEqualTo("tool");
    assertThat(lastMessages.get(3).toolCallId()).isEqualTo("ext111");
    final var site1Attrs = parseDocumentTagAttributes(toolResultText);
    assertThat(site1Attrs.get("id")).startsWith("ext-");

    // Site-2: synthetic user message — same id, plus toolName and toolCallId for correlation
    assertExtractedDocumentsUserMessage(
        lastMessages.get(4),
        ExtractedDocument.forToolCall(
            "ext111",
            "External_File_Reference",
            site1Attrs,
            block -> assertDocumentContentBlockJson(block, "base64", "application/pdf")));

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
