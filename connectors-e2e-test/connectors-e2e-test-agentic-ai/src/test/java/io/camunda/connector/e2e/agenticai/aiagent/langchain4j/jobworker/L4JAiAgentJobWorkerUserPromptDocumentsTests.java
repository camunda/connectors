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
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.Content;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.PdfFileContent;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.data.pdf.PdfFile;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.ChatResponseMetadata;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.TokenUsage;
import io.camunda.connector.agenticai.aiagent.model.AgentMetrics;
import io.camunda.connector.e2e.agenticai.assertj.JobWorkerAgentResponseAssert;
import io.camunda.connector.test.utils.annotation.SlowTest;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@SlowTest
public class L4JAiAgentJobWorkerUserPromptDocumentsTests extends BaseL4JAiAgentJobWorkerTest {

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
    final var expectedConversation =
        List.of(
            new SystemMessage(
                "You are a helpful AI assistant. Answer all the questions, but always be nice. Explain your thinking."),
            UserMessage.builder()
                .addContent(new TextContent(initialUserPrompt))
                .addContent(asContentBlock(filename))
                .build(),
            new AiMessage("TL;DR: it is pretty interesting"));

    mockChatInteractions(
        ChatInteraction.of(
            ChatResponse.builder()
                .metadata(
                    ChatResponseMetadata.builder()
                        .finishReason(FinishReason.STOP)
                        .tokenUsage(new TokenUsage(10, 20))
                        .build())
                .aiMessage(new AiMessage("TL;DR: it is pretty interesting"))
                .build(),
            userSatisfiedFeedback()));

    final var zeebeTest =
        createProcessInstance(
                elementTemplate ->
                    elementTemplate.property("retryCount", "3").property("retryBackoff", "PT2S"),
                Map.of(
                    "action",
                    "executeAgent",
                    "userPrompt",
                    initialUserPrompt,
                    "downloadUrls",
                    List.of(wireMock.getHttpBaseUrl() + "/" + filename)))
            .waitForProcessCompletion();

    assertLastChatRequest(expectedConversation);

    String expectedResponseText = ((AiMessage) expectedConversation.getLast()).text();
    assertAgentResponse(
        zeebeTest,
        agentResponse ->
            JobWorkerAgentResponseAssert.assertThat(agentResponse)
                .isReady()
                .hasMetrics(new AgentMetrics(1, new AgentMetrics.TokenUsage(10, 20)))
                .hasResponseMessageText(expectedResponseText)
                .hasResponseText(expectedResponseText));

    assertThat(jobWorkerCounter.get()).isEqualTo(1);
  }

  @Test
  void handlesMultipleDocuments(WireMockRuntimeInfo wireMock) throws Exception {
    final var initialUserPrompt = "Summarize the following documents";
    final var expectedConversation =
        List.of(
            new SystemMessage(
                "You are a helpful AI assistant. Answer all the questions, but always be nice. Explain your thinking."),
            UserMessage.builder()
                .addContent(new TextContent(initialUserPrompt))
                .addContent(asContentBlock("test.txt"))
                .addContent(asContentBlock("test.jpg"))
                .build(),
            new AiMessage("TL;DR: they contain a lot of interesting information."));

    mockChatInteractions(
        ChatInteraction.of(
            ChatResponse.builder()
                .metadata(
                    ChatResponseMetadata.builder()
                        .finishReason(FinishReason.STOP)
                        .tokenUsage(new TokenUsage(10, 20))
                        .build())
                .aiMessage(new AiMessage("TL;DR: they contain a lot of interesting information."))
                .build(),
            userSatisfiedFeedback()));

    final var zeebeTest =
        createProcessInstance(
                elementTemplate ->
                    elementTemplate.property("retryCount", "3").property("retryBackoff", "PT2S"),
                Map.of(
                    "action",
                    "executeAgent",
                    "userPrompt",
                    initialUserPrompt,
                    "downloadUrls",
                    List.of(
                        wireMock.getHttpBaseUrl() + "/test.txt",
                        wireMock.getHttpBaseUrl() + "/test.jpg")))
            .waitForProcessCompletion();

    assertLastChatRequest(expectedConversation);

    String expectedResponseText = ((AiMessage) expectedConversation.getLast()).text();
    assertAgentResponse(
        zeebeTest,
        agentResponse ->
            JobWorkerAgentResponseAssert.assertThat(agentResponse)
                .isReady()
                .hasMetrics(new AgentMetrics(1, new AgentMetrics.TokenUsage(10, 20)))
                .hasResponseMessageText(expectedResponseText)
                .hasResponseText(expectedResponseText));

    assertThat(jobWorkerCounter.get()).isEqualTo(1);
  }

  @Test
  void raisesIncidentWhenDocumentTypeIsNotSupported(WireMockRuntimeInfo wireMock) throws Exception {
    final var zeebeTest =
        createProcessInstance(
                Map.of(
                    "action",
                    "executeAgent",
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

  private Content asContentBlock(String filename) throws Exception {
    final Supplier<String> text = testFileContent(filename);
    final Supplier<String> b64 = testFileContentBase64(filename);

    return switch (filename) {
      case "test.txt", "test.yaml", "test.csv", "test.json", "test.xml" ->
          TextContent.from(text.get());
      case "test.pdf" -> PdfFileContent.from(PdfFile.builder().base64Data(b64.get()).build());
      case "test.gif" -> ImageContent.from(b64.get(), "image/gif", ImageContent.DetailLevel.AUTO);
      case "test.jpg" -> ImageContent.from(b64.get(), "image/jpeg", ImageContent.DetailLevel.AUTO);
      case "test.png" -> ImageContent.from(b64.get(), "image/png", ImageContent.DetailLevel.AUTO);
      case "test.webp" -> ImageContent.from(b64.get(), "image/webp", ImageContent.DetailLevel.AUTO);
      default -> throw new IllegalStateException("Unsupported file: " + filename);
    };
  }
}
