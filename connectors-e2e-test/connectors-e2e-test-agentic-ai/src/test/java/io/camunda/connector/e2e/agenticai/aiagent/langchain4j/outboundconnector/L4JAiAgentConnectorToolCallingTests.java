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
package io.camunda.connector.e2e.agenticai.aiagent.langchain4j.outboundconnector;

import static io.camunda.connector.e2e.agenticai.aiagent.AiAgentTestFixtures.FEEDBACK_LOOP_RESPONSE_TEXT;
import static io.camunda.connector.e2e.agenticai.aiagent.AiAgentTestFixtures.readDocumentReference;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.Content;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.PdfFileContent;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.ChatResponseMetadata;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.TokenUsage;
import io.camunda.connector.agenticai.aiagent.model.AgentMetrics;
import io.camunda.connector.e2e.agenticai.assertj.AgentResponseAssert;
import io.camunda.connector.test.utils.annotation.SlowTest;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@SlowTest
public class L4JAiAgentConnectorToolCallingTests extends BaseL4JAiAgentConnectorTest {

  @Test
  void executesAgentWithToolCallingAndUserFeedback() throws Exception {
    testInteractionWithToolsAndUserFeedbackLoops(
        e -> e,
        FEEDBACK_LOOP_RESPONSE_TEXT,
        true,
        (agentResponse) ->
            AgentResponseAssert.assertThat(agentResponse)
                .hasResponseMessageText(FEEDBACK_LOOP_RESPONSE_TEXT)
                .hasResponseText(FEEDBACK_LOOP_RESPONSE_TEXT)
                .hasNoResponseJson());
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

    final var aiToolCallMessage =
        new AiMessage(
            "The user asked me to download a document. I will call the Download_A_File tool to do so.",
            List.of(
                ToolExecutionRequest.builder()
                    .id("aaa111")
                    .name("Download_A_File")
                    .arguments(
                        "{\"url\": \"%s\"}".formatted(wireMock.getHttpBaseUrl() + "/" + filename))
                    .build()));
    final var aiResponseAfterTool =
        new AiMessage(
            "I loaded a document and learned that it contains interesting data. Anything specific you want to know?");
    final var aiFinalResponse = new AiMessage("The content type is '%s'".formatted(mimeType));

    mockChatInteractions(
        ChatInteraction.of(
            ChatResponse.builder()
                .metadata(
                    ChatResponseMetadata.builder()
                        .finishReason(FinishReason.TOOL_EXECUTION)
                        .tokenUsage(new TokenUsage(10, 20))
                        .build())
                .aiMessage(aiToolCallMessage)
                .build()),
        ChatInteraction.of(
            ChatResponse.builder()
                .metadata(
                    ChatResponseMetadata.builder()
                        .finishReason(FinishReason.STOP)
                        .tokenUsage(new TokenUsage(100, 200))
                        .build())
                .aiMessage(aiResponseAfterTool)
                .build(),
            userFollowUpFeedback("What is the content type?")),
        ChatInteraction.of(
            ChatResponse.builder()
                .metadata(
                    ChatResponseMetadata.builder()
                        .finishReason(FinishReason.STOP)
                        .tokenUsage(new TokenUsage(11, 22))
                        .build())
                .aiMessage(aiFinalResponse)
                .build(),
            userSatisfiedFeedback()));

    final var zeebeTest =
        createProcessInstance(
                elementTemplate ->
                    elementTemplate.property("retryCount", "3").property("retryBackoff", "PT2S"),
                Map.of("userPrompt", initialUserPrompt))
            .waitForProcessCompletion();

    await()
        .alias("Chat request captured")
        .untilAsserted(() -> assertThat(chatRequestCaptor.getValue()).isNotNull());

    assertThat(chatRequestCaptor.getAllValues()).hasSize(3);
    final var lastMessages = chatRequestCaptor.getValue().messages();
    assertThat(lastMessages).hasSize(7);

    assertThat(lastMessages.get(0)).isInstanceOf(SystemMessage.class);
    assertThat(lastMessages.get(1)).isInstanceOf(UserMessage.class); // initial prompt
    assertThat(lastMessages.get(2)).isInstanceOf(AiMessage.class); // tool call

    // tool result: document serialized as document reference
    var toolResultText = ((ToolExecutionResultMessage) lastMessages.get(3)).text();
    var documentReference = readDocumentReference(toolResultText);

    assertThat(lastMessages.get(3))
        .isInstanceOfSatisfying(
            ToolExecutionResultMessage.class,
            msg -> {
              assertThat(msg.id()).isEqualTo("aaa111");
              assertThat(msg.toolName()).isEqualTo("Download_A_File");
            });
    assertThat(documentReference.contentType()).isEqualTo(mimeType);

    // document user message: extracted document content
    assertThat(lastMessages.get(4))
        .isInstanceOfSatisfying(
            UserMessage.class,
            msg -> {
              List<Content> contents = msg.contents();
              assertThat(contents).hasSize(3);
              assertThat(contents.get(0))
                  .isInstanceOfSatisfying(
                      TextContent.class,
                      tc ->
                          assertThat(tc.text())
                              .isEqualTo("Documents extracted from tool call results:"));
              assertThat(contents.get(1))
                  .isInstanceOfSatisfying(
                      TextContent.class,
                      tc ->
                          assertThat(tc.text())
                              .isEqualTo(
                                  "<document tool-name=\"Download_A_File\" tool-call-id=\"aaa111\" document-short-id=\"%s\" />"
                                      .formatted(documentReference.shortId())));
              assertDocumentContentBlock(contents.get(2), type, mimeType);
            });

    assertThat(lastMessages.get(5)).isInstanceOf(AiMessage.class); // response after tool

    assertThat(lastMessages.get(6))
        .isInstanceOfSatisfying(
            UserMessage.class,
            msg -> assertThat(msg.singleText()).isEqualTo("What is the content type?"));

    assertAgentResponse(
        zeebeTest,
        agentResponse ->
            AgentResponseAssert.assertThat(agentResponse)
                .isReady()
                .hasNoToolCalls()
                .hasMetrics(new AgentMetrics(3, new AgentMetrics.TokenUsage(121, 242)))
                .hasResponseMessageText(aiFinalResponse.text())
                .hasResponseText(aiFinalResponse.text()));

    assertThat(userFeedbackJobWorkerCounter.get()).isEqualTo(2);
  }

  private void assertDocumentContentBlock(
      Content content, String expectedType, String expectedMimeType) {
    if (expectedType.equals("text")) {
      assertThat(content).isInstanceOf(TextContent.class);
    } else if (expectedMimeType.equals("application/pdf")) {
      assertThat(content)
          .isInstanceOfSatisfying(
              PdfFileContent.class, pdf -> assertThat(pdf.pdfFile().base64Data()).isNotBlank());
    } else {
      // image types
      assertThat(content)
          .isInstanceOfSatisfying(
              ImageContent.class,
              img -> {
                assertThat(img.image().mimeType()).isEqualTo(expectedMimeType);
                assertThat(img.image().base64Data()).isNotBlank();
              });
    }
  }
}
