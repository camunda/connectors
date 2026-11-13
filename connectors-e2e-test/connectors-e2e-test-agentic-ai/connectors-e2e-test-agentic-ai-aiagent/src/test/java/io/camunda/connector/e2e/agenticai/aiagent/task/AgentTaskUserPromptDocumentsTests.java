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
import static io.camunda.connector.e2e.agenticai.aiagent.DocumentFlowFixtures.FINAL_TEXT;
import static io.camunda.connector.e2e.agenticai.aiagent.DocumentFlowFixtures.GET_CALL_ID;
import static io.camunda.connector.e2e.agenticai.aiagent.DocumentFlowFixtures.finalTurn;
import static io.camunda.connector.e2e.agenticai.aiagent.DocumentFlowFixtures.getDocumentTurn;
import static io.camunda.connector.e2e.agenticai.aiagent.ToolCallResultDocumentAssertions.assertDocumentContentBlockJson;
import static io.camunda.connector.e2e.agenticai.aiagent.ToolCallResultDocumentAssertions.assertDocumentHandlePart;
import static io.camunda.connector.e2e.agenticai.aiagent.ToolCallResultDocumentAssertions.assertHandleOfTheOnlyRegisteredDocument;
import static io.camunda.connector.e2e.agenticai.aiagent.ToolCallResultDocumentAssertions.contentAlreadyInConversationSentence;
import static io.camunda.connector.e2e.agenticai.aiagent.fixtures.AgentTestFixtures.AI_AGENT_ELEMENT_ID;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.connector.agenticai.aiagent.model.AgentMetrics;
import io.camunda.connector.agenticai.aiagent.model.document.AgentDocumentOrigin;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.openai.OpenAiCompletionsChatModelStubs;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.openai.OpenAiCompletionsChatModelStubs.Turn;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.openai.OpenAiCompletionsRecordedConversation;
import io.camunda.connector.e2e.agenticai.assertj.AgentResponseAssert;
import io.camunda.connector.test.utils.annotation.SlowTest;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * The documents of the user prompt: the model gets the handle and the content of every document.
 */
@SlowTest
public class AgentTaskUserPromptDocumentsTests extends BaseAgentTaskTest {

  @Test
  void handlesMultipleDocuments() throws Exception {
    final var initialUserPrompt = "Summarize the following documents";
    final var responseText = "TL;DR: they contain a lot of interesting information.";

    OpenAiCompletionsChatModelStubs.stubConversation(Turn.text(responseText, 10, 20));
    enqueueUserFeedback(userSatisfiedFeedback());

    final var zeebeTest =
        awaitProcessCompletion(
            createProcessInstance(
                elementTemplate ->
                    elementTemplate.property("retryCount", "3").property("retryBackoff", "PT2S"),
                Map.of(
                    "userPrompt",
                    initialUserPrompt,
                    "downloadUrls",
                    List.of(
                        wireMock.getHttpBaseUrl() + "/test.txt",
                        wireMock.getHttpBaseUrl() + "/test.pdf",
                        wireMock.getHttpBaseUrl() + "/test.jpg"))));

    final var recorded = OpenAiCompletionsRecordedConversation.recorded();
    assertNoDocumentInternalsInLlmRequests(recorded.providerNeutralRequests());
    assertThat(recorded.modelCallCount()).isEqualTo(1);
    assertThat(recorded.lastRequest().messages()).hasSize(2);

    assertThat(recorded.lastRequest().messages().get(0).role()).isEqualTo("system");
    assertThat(recorded.lastRequest().messages().get(0).content()).isEqualTo(SYSTEM_PROMPT);

    // user message: prompt text, then a handle and the content for each of the three documents
    final var userMessage = recorded.lastRequest().messages().get(1);
    assertThat(userMessage.role()).isEqualTo("user");
    assertThat(userMessage.contentParts())
        .as("prompt text + a handle and the content for each of the 3 documents")
        .hasSize(7);
    assertThat(userMessage.contentParts().get(0).path("type").asText()).isEqualTo("text");
    assertThat(userMessage.contentParts().get(0).path("text").asText())
        .isEqualTo(initialUserPrompt);
    assertDocumentHandlePart(userMessage.contentParts().get(1), "test.txt");
    assertDocumentContentBlockJson(userMessage.contentParts().get(2), "test.txt");
    assertDocumentHandlePart(userMessage.contentParts().get(3), "test.pdf");
    assertDocumentContentBlockJson(userMessage.contentParts().get(4), "test.pdf");
    assertDocumentHandlePart(userMessage.contentParts().get(5), "test.jpg");
    assertDocumentContentBlockJson(userMessage.contentParts().get(6), "test.jpg");

    assertAgentResponse(
        zeebeTest,
        agentResponse ->
            AgentResponseAssert.assertThat(agentResponse)
                .isReady()
                .hasNoToolCalls()
                .hasMetrics(new AgentMetrics(1, new AgentMetrics.TokenUsage(10, 20), 0))
                .hasResponseMessageText(responseText)
                .hasResponseText(responseText));

    assertThat(userFeedbackJobWorkerCounter.get()).isEqualTo(1);
  }

  /**
   * The model loads the document of the user prompt with {@code c8__load_document}: the call is the
   * only one of its message and a runtime tool, so the agent answers it in the same job and calls
   * the model again. The content of the document is in the conversation already, so the result says
   * so and no message with the content follows.
   */
  @Test
  void loadsADocumentOfTheUserPromptWithTheLoadDocumentTool() throws Exception {
    final var initialUserPrompt = "Summarize the following document";

    OpenAiCompletionsChatModelStubs.stubConversation(getDocumentTurn(), finalTurn());
    enqueueUserFeedback(userSatisfiedFeedback());

    final var zeebeTest =
        awaitProcessCompletion(
            createProcessInstance(
                Map.of(
                    "userPrompt",
                    initialUserPrompt,
                    "downloadUrls",
                    List.of(wireMock.getHttpBaseUrl() + "/test.txt"))));

    final var recorded = OpenAiCompletionsRecordedConversation.recorded();
    assertNoDocumentInternalsInLlmRequests(recorded.providerNeutralRequests());
    assertThat(recorded.modelCallCount()).isEqualTo(2);

    // the first request: the prompt, the handle and the content of the document
    final var firstMessages = recorded.requests().get(0).messages();
    assertThat(firstMessages).extracting(m -> m.role()).containsExactly("system", "user");
    final var promptParts = firstMessages.get(1).contentParts();
    assertThat(promptParts).as("prompt text + handle + content").hasSize(3);
    assertThat(promptParts.get(0).path("text").asText()).isEqualTo(initialUserPrompt);
    final var handle = assertDocumentHandlePart(promptParts.get(1), "test.txt");
    assertDocumentContentBlockJson(promptParts.get(2), "test.txt");

    // the second request: the user message as before, the call and its result, no further message
    final var secondMessages = recorded.requests().get(1).messages();
    assertThat(secondMessages)
        .extracting(m -> m.role())
        .containsExactly("system", "user", "assistant", "tool");
    assertThat(secondMessages.get(0).content()).isEqualTo(SYSTEM_PROMPT);
    assertThat(secondMessages.get(1).contentParts()).isEqualTo(promptParts);
    assertThat(secondMessages.get(3).toolCallId()).isEqualTo(GET_CALL_ID);
    assertThat(secondMessages.get(3).content())
        .isEqualTo(contentAlreadyInConversationSentence(handle));

    assertAgentResponse(
        zeebeTest,
        agentResponse -> {
          AgentResponseAssert.assertThat(agentResponse)
              .isReady()
              .hasNoToolCalls()
              .hasMetrics(new AgentMetrics(2, new AgentMetrics.TokenUsage(20, 40), 1))
              .hasResponseMessageText(FINAL_TEXT)
              .hasResponseText(FINAL_TEXT);
          final var registered =
              assertHandleOfTheOnlyRegisteredDocument(
                  handle, agentResponse.context().documents(), null, "text/plain", null);
          assertThat(registered.origin()).isInstanceOf(AgentDocumentOrigin.UserMessage.class);
        });

    assertThat(userFeedbackJobWorkerCounter.get()).isEqualTo(1);
  }

  @Test
  void raisesIncidentWhenDocumentTypeIsNotSupported() throws Exception {
    final var zeebeTest =
        awaitActiveIncidents(
            createProcessInstance(
                Map.of(
                    "userPrompt",
                    "Summarize the following document",
                    "downloadUrls",
                    List.of(wireMock.getHttpBaseUrl() + "/unsupported.zip"))));

    assertIncident(
        zeebeTest,
        incident -> {
          assertThat(incident.getElementId()).isEqualTo(AI_AGENT_ELEMENT_ID);
          assertThat(incident.getErrorMessage())
              .startsWith("Unsupported content type 'application/zip' for document with reference");
        });
  }
}
