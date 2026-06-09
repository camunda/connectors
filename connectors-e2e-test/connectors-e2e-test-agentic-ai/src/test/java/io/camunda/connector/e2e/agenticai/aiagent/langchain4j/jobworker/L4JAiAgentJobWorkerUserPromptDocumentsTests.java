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

import static io.camunda.connector.e2e.agenticai.aiagent.AiAgentTestFixtures.AI_AGENT_TASK_ID;
import static io.camunda.connector.e2e.agenticai.aiagent.ToolCallResultDocumentAssertions.assertDocumentContentBlockJson;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import io.camunda.connector.agenticai.aiagent.model.AgentMetrics;
import io.camunda.connector.e2e.agenticai.aiagent.langchain4j.wiremock.OpenAiChatModelStubs;
import io.camunda.connector.e2e.agenticai.aiagent.langchain4j.wiremock.OpenAiChatModelStubs.Turn;
import io.camunda.connector.e2e.agenticai.aiagent.langchain4j.wiremock.RecordedLlmConversation;
import io.camunda.connector.e2e.agenticai.assertj.JobWorkerAgentResponseAssert;
import io.camunda.connector.test.utils.annotation.SlowTest;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@SlowTest
public class L4JAiAgentJobWorkerUserPromptDocumentsTests
    extends BaseWireMockL4JAiAgentJobWorkerTest {

  @ParameterizedTest
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
  void handlesDocumentType(String filename, WireMockRuntimeInfo wireMock) throws Exception {
    final var initialUserPrompt = "Summarize the following document";
    final var responseText = "TL;DR: it is pretty interesting";

    OpenAiChatModelStubs.stubConversation(Turn.text(responseText, 10, 20));
    enqueueUserFeedback(userSatisfiedFeedback());

    final var zeebeTest =
        createProcessInstance(
                elementTemplate ->
                    elementTemplate.property("retryCount", "3").property("retryBackoff", "PT2S"),
                Map.of(
                    "userPrompt",
                    initialUserPrompt,
                    "downloadUrls",
                    List.of(wireMock.getHttpBaseUrl() + "/" + filename)))
            .waitForProcessCompletion();

    final var recorded = RecordedLlmConversation.recorded();
    assertThat(recorded.modelCallCount()).isEqualTo(1);
    assertThat(recorded.lastRequest().messages()).hasSize(2);

    assertThat(recorded.lastRequest().messages().get(0).path("role").asText()).isEqualTo("system");
    assertThat(recorded.lastRequest().messages().get(0).path("content").asText())
        .isEqualTo(SYSTEM_PROMPT);

    // user message: prompt text + document block (text files produce two text blocks,
    // so assertConversationMessages cannot be used here as it would concatenate them)
    final var userMessage = recorded.lastRequest().messages().get(1);
    final var content = userMessage.path("content");
    assertThat(userMessage.path("role").asText()).isEqualTo("user");
    assertThat(content.isArray()).as("user message should have multipart content").isTrue();
    assertThat(content.size()).as("expected text + document block").isEqualTo(2);
    assertThat(content.get(0).path("type").asText()).isEqualTo("text");
    assertThat(content.get(0).path("text").asText()).isEqualTo(initialUserPrompt);
    assertDocumentContentBlockJson(content.get(1), contentBlockType(filename), mimeType(filename));

    assertAgentResponse(
        zeebeTest,
        agentResponse ->
            JobWorkerAgentResponseAssert.assertThat(agentResponse)
                .isReady()
                .hasMetrics(new AgentMetrics(1, new AgentMetrics.TokenUsage(10, 20), 0))
                .hasResponseMessageText(responseText)
                .hasResponseText(responseText));

    assertThat(userFeedbackJobWorkerCounter.get()).isEqualTo(1);
  }

  @Test
  void handlesMultipleDocuments(WireMockRuntimeInfo wireMock) throws Exception {
    final var initialUserPrompt = "Summarize the following documents";
    final var responseText = "TL;DR: they contain a lot of interesting information.";

    OpenAiChatModelStubs.stubConversation(Turn.text(responseText, 10, 20));
    enqueueUserFeedback(userSatisfiedFeedback());

    final var zeebeTest =
        createProcessInstance(
                elementTemplate ->
                    elementTemplate.property("retryCount", "3").property("retryBackoff", "PT2S"),
                Map.of(
                    "userPrompt",
                    initialUserPrompt,
                    "downloadUrls",
                    List.of(
                        wireMock.getHttpBaseUrl() + "/test.txt",
                        wireMock.getHttpBaseUrl() + "/test.jpg")))
            .waitForProcessCompletion();

    final var recorded = RecordedLlmConversation.recorded();
    assertThat(recorded.modelCallCount()).isEqualTo(1);
    assertThat(recorded.lastRequest().messages()).hasSize(2);

    assertThat(recorded.lastRequest().messages().get(0).path("role").asText()).isEqualTo("system");
    assertThat(recorded.lastRequest().messages().get(0).path("content").asText())
        .isEqualTo(SYSTEM_PROMPT);

    final var userMessage = recorded.lastRequest().messages().get(1);
    final var content = userMessage.path("content");
    assertThat(userMessage.path("role").asText()).isEqualTo("user");
    assertThat(content.isArray()).isTrue();
    assertThat(content.size()).as("expected text + 2 document blocks").isEqualTo(3);
    assertThat(content.get(0).path("type").asText()).isEqualTo("text");
    assertThat(content.get(0).path("text").asText()).isEqualTo(initialUserPrompt);
    assertDocumentContentBlockJson(content.get(1), "text", "text/plain");
    assertDocumentContentBlockJson(content.get(2), "base64", "image/jpeg");

    assertAgentResponse(
        zeebeTest,
        agentResponse ->
            JobWorkerAgentResponseAssert.assertThat(agentResponse)
                .isReady()
                .hasMetrics(new AgentMetrics(1, new AgentMetrics.TokenUsage(10, 20), 0))
                .hasResponseMessageText(responseText)
                .hasResponseText(responseText));

    assertThat(userFeedbackJobWorkerCounter.get()).isEqualTo(1);
  }

  @Test
  void raisesIncidentWhenDocumentTypeIsNotSupported(WireMockRuntimeInfo wireMock) throws Exception {
    final var zeebeTest =
        createProcessInstance(
                Map.of(
                    "userPrompt",
                    "Summarize the following document",
                    "downloadUrls",
                    List.of(wireMock.getHttpBaseUrl() + "/unsupported.zip")))
            .waitForActiveIncidents();

    assertIncident(
        zeebeTest,
        incident -> {
          assertThat(incident.getElementId()).isEqualTo(AI_AGENT_TASK_ID);
          assertThat(incident.getErrorMessage())
              .startsWith("Unsupported content type 'application/zip' for document with reference");
        });
  }

  private static String contentBlockType(String filename) {
    return switch (filename) {
      case "test.txt", "test.yaml", "test.csv", "test.json", "test.xml" -> "text";
      default -> "base64";
    };
  }

  private static String mimeType(String filename) {
    return switch (filename) {
      case "test.csv" -> "text/csv";
      case "test.gif" -> "image/gif";
      case "test.jpg" -> "image/jpeg";
      case "test.json" -> "application/json";
      case "test.pdf" -> "application/pdf";
      case "test.png" -> "image/png";
      case "test.txt" -> "text/plain";
      case "test.webp" -> "image/webp";
      case "test.xml" -> "application/xml";
      case "test.yaml" -> "application/yaml";
      default -> throw new IllegalArgumentException("Unknown file: " + filename);
    };
  }
}
