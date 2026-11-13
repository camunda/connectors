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
import static io.camunda.connector.e2e.agenticai.aiagent.DocumentFlowFixtures.unsupportedZipIncidentMessage;
import static io.camunda.connector.e2e.agenticai.aiagent.ToolCallResultDocumentAssertions.assertDocumentContentBlockJson;
import static io.camunda.connector.e2e.agenticai.aiagent.ToolCallResultDocumentAssertions.assertDocumentContentUserMessage;
import static io.camunda.connector.e2e.agenticai.aiagent.ToolCallResultDocumentAssertions.assertDocumentHandlePart;
import static io.camunda.connector.e2e.agenticai.aiagent.ToolCallResultDocumentAssertions.assertHandleOfTheOnlyRegisteredDocument;
import static io.camunda.connector.e2e.agenticai.aiagent.ToolCallResultDocumentAssertions.contentBlockType;
import static io.camunda.connector.e2e.agenticai.aiagent.ToolCallResultDocumentAssertions.contentFollowsSentence;
import static io.camunda.connector.e2e.agenticai.aiagent.ToolCallResultDocumentAssertions.mimeType;
import static io.camunda.connector.e2e.agenticai.aiagent.ToolCallResultDocumentAssertions.parseDocumentHandle;
import static io.camunda.connector.e2e.agenticai.aiagent.fixtures.AgentTestFixtures.AI_AGENT_ELEMENT_ID;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.connector.agenticai.aiagent.model.AgentMetrics;
import io.camunda.connector.agenticai.aiagent.model.document.AgentDocumentOrigin;
import io.camunda.connector.e2e.agenticai.aiagent.ToolCallResultDocumentAssertions.DocumentWithContent;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.openai.OpenAiCompletionsChatModelStubs;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.openai.OpenAiCompletionsChatModelStubs.Turn;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.openai.OpenAiCompletionsRecordedConversation;
import io.camunda.connector.e2e.agenticai.assertj.AgentResponseAssert;
import io.camunda.connector.test.utils.annotation.SlowTest;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

@SlowTest
public class AgentTaskUserPromptDocumentsTests extends BaseAgentTaskTest {

  @ParameterizedTest
  @ValueSource(strings = {"test.pdf", "test.txt"})
  void handlesDocumentType(String filename) throws Exception {
    final var initialUserPrompt = "Summarize the following document";
    final var responseText = "TL;DR: it is pretty interesting";

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
                    List.of(wireMock.getHttpBaseUrl() + "/" + filename))));

    final var recorded = OpenAiCompletionsRecordedConversation.recorded();
    assertNoDocumentInternalsInLlmRequests(recorded.providerNeutralRequests());
    assertThat(recorded.modelCallCount()).isEqualTo(1);
    assertThat(recorded.lastRequest().messages()).hasSize(2);

    assertThat(recorded.lastRequest().messages().get(0).role()).isEqualTo("system");
    assertThat(recorded.lastRequest().messages().get(0).content()).isEqualTo(SYSTEM_PROMPT);

    // user message: the prompt text and the handle of the document, not its content
    final var userMessage = recorded.lastRequest().messages().get(1);
    assertThat(userMessage.role()).isEqualTo("user");
    assertThat(userMessage.contentParts()).as("prompt text + handle").hasSize(2);
    assertThat(userMessage.contentParts().get(0).path("type").asText()).isEqualTo("text");
    assertThat(userMessage.contentParts().get(0).path("text").asText())
        .isEqualTo(initialUserPrompt);
    assertDocumentHandlePart(userMessage.contentParts().get(1), mimeType(filename));

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

    // user message: prompt text + the handles of the two documents
    final var userMessage = recorded.lastRequest().messages().get(1);
    assertThat(userMessage.role()).isEqualTo("user");
    assertThat(userMessage.contentParts()).as("prompt text + 2 handles").hasSize(3);
    assertThat(userMessage.contentParts().get(0).path("type").asText()).isEqualTo("text");
    assertThat(userMessage.contentParts().get(0).path("text").asText())
        .isEqualTo(initialUserPrompt);
    assertDocumentHandlePart(userMessage.contentParts().get(1), "text/plain");
    assertDocumentHandlePart(userMessage.contentParts().get(2), "image/jpeg");

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
   * the model again. With the default document delivery {@code reference} the first request shows
   * the handle only, so the result says that the content follows, and the message after the tool
   * result holds it.
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

    // the first request: the prompt and the handle of the document
    final var firstMessages = recorded.requests().get(0).messages();
    assertThat(firstMessages).extracting(m -> m.role()).containsExactly("system", "user");
    final var promptParts = firstMessages.get(1).contentParts();
    assertThat(promptParts).as("prompt text + handle").hasSize(2);
    assertThat(promptParts.get(0).path("text").asText()).isEqualTo(initialUserPrompt);
    assertDocumentHandlePart(promptParts.get(1));
    final var handle = parseDocumentHandle(promptParts.get(1).path("text").asText());

    // the second request: the user message as before, the call, its result and the content
    final var secondMessages = recorded.requests().get(1).messages();
    assertThat(secondMessages)
        .extracting(m -> m.role())
        .containsExactly("system", "user", "assistant", "tool", "user");
    assertThat(secondMessages.get(0).content()).isEqualTo(SYSTEM_PROMPT);
    assertThat(secondMessages.get(1).contentParts()).isEqualTo(promptParts);
    assertThat(secondMessages.get(3).toolCallId()).isEqualTo(GET_CALL_ID);
    assertThat(secondMessages.get(3).content()).isEqualTo(contentFollowsSentence(handle));
    assertDocumentContentUserMessage(
        secondMessages.get(4),
        DocumentWithContent.of(
            handle, block -> assertDocumentContentBlockJson(block, "text", "text/plain")));

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

  /**
   * With the document delivery {@code content} of the user prompt the content of the document is
   * part of the user message of the first request, next to its handle: no {@code c8__load_document}
   * call is needed.
   */
  @Test
  void addsTheContentOfPromptDocumentsWithTheDeliveryContent() throws Exception {
    final var filename = "test.pdf";
    final var initialUserPrompt = "Summarize the following document";
    final var responseText = "TL;DR: it is pretty interesting";

    OpenAiCompletionsChatModelStubs.stubConversation(Turn.text(responseText, 10, 20));
    enqueueUserFeedback(userSatisfiedFeedback());

    final var zeebeTest =
        awaitProcessCompletion(
            createProcessInstance(
                elementTemplate ->
                    elementTemplate
                        .property("retryCount", "3")
                        .property("retryBackoff", "PT2S")
                        .property("data.userPrompt.documentDelivery", "content"),
                Map.of(
                    "userPrompt",
                    initialUserPrompt,
                    "downloadUrls",
                    List.of(wireMock.getHttpBaseUrl() + "/" + filename))));

    final var recorded = OpenAiCompletionsRecordedConversation.recorded();
    assertNoDocumentInternalsInLlmRequests(recorded.providerNeutralRequests());
    assertThat(recorded.modelCallCount()).isEqualTo(1);
    assertToolSpecifications(recorded.lastRequest());

    final var userMessage = recorded.lastRequest().messages().get(1);
    assertThat(userMessage.role()).isEqualTo("user");
    assertThat(userMessage.contentParts()).as("prompt text + handle + content").hasSize(3);
    assertThat(userMessage.contentParts().get(0).path("text").asText())
        .isEqualTo(initialUserPrompt);
    assertDocumentHandlePart(userMessage.contentParts().get(1), mimeType(filename));
    assertDocumentContentBlockJson(
        userMessage.contentParts().get(2), contentBlockType(filename), mimeType(filename));

    assertAgentResponse(
        zeebeTest,
        agentResponse ->
            AgentResponseAssert.assertThat(agentResponse)
                .isReady()
                .hasNoToolCalls()
                .hasResponseText(responseText));
  }

  /**
   * Without a document delivery, as in a process modeled before the option existed, the content of
   * every prompt document is part of the user message of the first request, next to its handle.
   */
  @Test
  void addsTheContentOfPromptDocumentsWithoutADelivery() throws Exception {
    final var filename = "test.txt";
    final var initialUserPrompt = "Summarize the following document";
    final var responseText = "TL;DR: it is pretty interesting";

    OpenAiCompletionsChatModelStubs.stubConversation(Turn.text(responseText, 10, 20));
    enqueueUserFeedback(userSatisfiedFeedback());

    final var zeebeTest =
        awaitProcessCompletion(
            createProcessInstance(
                elementTemplate ->
                    elementTemplate
                        .property("retryCount", "3")
                        .property("retryBackoff", "PT2S")
                        .withoutProperty("data.userPrompt.documentDelivery"),
                Map.of(
                    "userPrompt",
                    initialUserPrompt,
                    "downloadUrls",
                    List.of(wireMock.getHttpBaseUrl() + "/" + filename))));

    final var recorded = OpenAiCompletionsRecordedConversation.recorded();
    assertNoDocumentInternalsInLlmRequests(recorded.providerNeutralRequests());
    assertThat(recorded.modelCallCount()).isEqualTo(1);

    final var userMessage = recorded.lastRequest().messages().get(1);
    assertThat(userMessage.role()).isEqualTo("user");
    assertThat(userMessage.contentParts()).as("prompt text + handle + content").hasSize(3);
    assertThat(userMessage.contentParts().get(0).path("text").asText())
        .isEqualTo(initialUserPrompt);
    assertDocumentHandlePart(userMessage.contentParts().get(1), mimeType(filename));
    assertDocumentContentBlockJson(
        userMessage.contentParts().get(2), contentBlockType(filename), mimeType(filename));

    assertAgentResponse(
        zeebeTest,
        agentResponse ->
            AgentResponseAssert.assertThat(agentResponse)
                .isReady()
                .hasNoToolCalls()
                .hasResponseText(responseText));
  }

  /**
   * Without a document delivery the content of every prompt document goes out with the prompt, so a
   * document of a type the model provider cannot read fails the model call and raises an incident.
   */
  @Test
  void raisesIncidentWhenDocumentTypeIsNotSupportedWithoutADelivery() throws Exception {
    final var zeebeTest =
        awaitActiveIncidents(
            createProcessInstance(
                elementTemplate ->
                    elementTemplate.withoutProperty("data.userPrompt.documentDelivery"),
                Map.of(
                    "userPrompt",
                    "Summarize the following document",
                    "downloadUrls",
                    List.of(wireMock.getHttpBaseUrl() + "/unsupported.zip"))));

    assertIncident(
        zeebeTest,
        incident -> {
          assertThat(incident.getElementId()).isEqualTo(AI_AGENT_ELEMENT_ID);
          assertThat(incident.getErrorMessage()).matches(unsupportedZipIncidentMessage());
        });
  }
}
