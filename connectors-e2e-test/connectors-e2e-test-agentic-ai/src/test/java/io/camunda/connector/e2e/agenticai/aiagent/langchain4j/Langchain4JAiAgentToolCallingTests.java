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
package io.camunda.connector.e2e.agenticai.aiagent.langchain4j;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.ChatResponseMetadata;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.TokenUsage;
import io.camunda.connector.agenticai.aiagent.framework.langchain4j.document.DocumentToContentResponseModel;
import io.camunda.connector.agenticai.aiagent.model.AgentMetrics;
import io.camunda.connector.e2e.agenticai.assertj.AgentResponseAssert;
import io.camunda.connector.test.SlowTest;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@SlowTest
public class Langchain4JAiAgentToolCallingTests extends BaseLangchain4JAiAgentTests {

  @Test
  void executesAgentWithToolCallingAndUserFeedback() throws Exception {
    final var initialUserPrompt = "Explore some of your tools!";
    final var expectedConversation =
        List.of(
            new SystemMessage(
                "You are a helpful AI assistant. Answer all the questions, but always be nice. Explain your thinking."),
            new UserMessage(initialUserPrompt),
            new AiMessage(
                "The user asked me to call some of my tools. I will call the superflux calculation and the task with a text input schema as they look interesting to me.",
                List.of(
                    ToolExecutionRequest.builder()
                        .id("aaa111")
                        .name("SuperfluxProduct")
                        .arguments("{\"a\": 5, \"b\": 3}")
                        .build(),
                    ToolExecutionRequest.builder()
                        .id("bbb222")
                        .name("Search_The_Web")
                        .arguments("{\"searchQuery\": \"Where does this data come from?\"}")
                        .build())),
            new ToolExecutionResultMessage("aaa111", "SuperfluxProduct", "24"),
            new ToolExecutionResultMessage(
                "bbb222", "Search_The_Web", "No results for 'Where does this data come from?'"),
            new AiMessage(
                "I played with the tools and learned that the data comes from the follow-up task and that a superflux calculation of 5 and 3 results in 24."),
            new UserMessage("So what is a superflux calculation anyway?"),
            new AiMessage(
                "A very complex calculation only the superflux calculation tool can do."));

    mockChatInteractions(
        ChatInteraction.of(
            ChatResponse.builder()
                .metadata(
                    ChatResponseMetadata.builder()
                        .finishReason(FinishReason.STOP)
                        .tokenUsage(new TokenUsage(10, 20))
                        .build())
                .aiMessage((AiMessage) expectedConversation.get(2))
                .build()),
        ChatInteraction.of(
            ChatResponse.builder()
                .metadata(
                    ChatResponseMetadata.builder()
                        .finishReason(FinishReason.STOP)
                        .tokenUsage(new TokenUsage(100, 200))
                        .build())
                .aiMessage((AiMessage) expectedConversation.get(5))
                .build(),
            userFollowUpFeedback("So what is a superflux calculation anyway?")),
        ChatInteraction.of(
            ChatResponse.builder()
                .metadata(
                    ChatResponseMetadata.builder()
                        .finishReason(FinishReason.STOP)
                        .tokenUsage(new TokenUsage(11, 22))
                        .build())
                .aiMessage((AiMessage) expectedConversation.get(7))
                .build(),
            userSatisfiedFeedback()));

    final var zeebeTest =
        createProcessInstance(Map.of("action", "executeAgent", "userPrompt", initialUserPrompt))
            .waitForProcessCompletion();

    assertLastChatRequest(3, expectedConversation);

    final var agentResponse = getAgentResponse(zeebeTest);
    String expectedResponseText = ((AiMessage) expectedConversation.getLast()).text();
    AgentResponseAssert.assertThat(agentResponse)
        .isReady()
        .hasNoToolCalls()
        .hasMetrics(new AgentMetrics(3, new AgentMetrics.TokenUsage(121, 242)))
        .hasResponseMessageText(expectedResponseText)
        .hasResponseText(expectedResponseText);

    assertThat(jobWorkerCounter.get()).isEqualTo(2);
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
    DownloadFileToolResult expectedDownloadFileResult;
    if (type.equals("text")) {
      expectedDownloadFileResult =
          new DownloadFileToolResult(
              200,
              new DocumentToContentResponseModel(type, mimeType, testFileContent(filename).get()));
    } else {
      expectedDownloadFileResult =
          new DownloadFileToolResult(
              200,
              new DocumentToContentResponseModel(
                  type, mimeType, testFileContentBase64(filename).get()));
    }

    final var initialUserPrompt = "Go and download a document!";
    final var expectedConversation =
        List.of(
            new SystemMessage(
                "You are a helpful AI assistant. Answer all the questions, but always be nice. Explain your thinking."),
            new UserMessage(initialUserPrompt),
            new AiMessage(
                "The user asked me to download a document. I will call the Download_A_File tool to do so.",
                List.of(
                    ToolExecutionRequest.builder()
                        .id("aaa111")
                        .name("Download_A_File")
                        .arguments("{\"url\": \"%s\"}".formatted(wm.baseUrl() + "/" + filename))
                        .build())),
            new ToolExecutionResultMessage(
                "aaa111",
                "Download_A_File",
                objectMapper.writeValueAsString(expectedDownloadFileResult)),
            new AiMessage(
                "I loaded a document and learned that it contains interesting data. Anything specific you want to know?"),
            new UserMessage("What is the content type?"),
            new AiMessage("The content type is '%s'".formatted(mimeType)));

    mockChatInteractions(
        ChatInteraction.of(
            ChatResponse.builder()
                .metadata(
                    ChatResponseMetadata.builder()
                        .finishReason(FinishReason.STOP)
                        .tokenUsage(new TokenUsage(10, 20))
                        .build())
                .aiMessage((AiMessage) expectedConversation.get(2))
                .build()),
        ChatInteraction.of(
            ChatResponse.builder()
                .metadata(
                    ChatResponseMetadata.builder()
                        .finishReason(FinishReason.STOP)
                        .tokenUsage(new TokenUsage(100, 200))
                        .build())
                .aiMessage((AiMessage) expectedConversation.get(4))
                .build(),
            userFollowUpFeedback("What is the content type?")),
        ChatInteraction.of(
            ChatResponse.builder()
                .metadata(
                    ChatResponseMetadata.builder()
                        .finishReason(FinishReason.STOP)
                        .tokenUsage(new TokenUsage(11, 22))
                        .build())
                .aiMessage((AiMessage) expectedConversation.get(6))
                .build(),
            userSatisfiedFeedback()));

    final var zeebeTest =
        createProcessInstance(
                elementTemplate ->
                    elementTemplate.property("retryCount", "3").property("retryBackoff", "PT2S"),
                Map.of("action", "executeAgent", "userPrompt", initialUserPrompt))
            .waitForProcessCompletion();

    assertLastChatRequest(3, expectedConversation);

    final var agentResponse = getAgentResponse(zeebeTest);
    String expectedResponseText = ((AiMessage) expectedConversation.getLast()).text();
    AgentResponseAssert.assertThat(agentResponse)
        .isReady()
        .hasNoToolCalls()
        .hasMetrics(new AgentMetrics(3, new AgentMetrics.TokenUsage(121, 242)))
        .hasResponseMessageText(expectedResponseText)
        .hasResponseText(expectedResponseText);

    assertThat(jobWorkerCounter.get()).isEqualTo(2);
  }
}
