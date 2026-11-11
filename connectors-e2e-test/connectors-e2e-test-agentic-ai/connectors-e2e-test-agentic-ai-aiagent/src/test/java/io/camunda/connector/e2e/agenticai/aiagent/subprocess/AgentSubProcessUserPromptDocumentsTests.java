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
import static io.camunda.connector.e2e.agenticai.aiagent.DocumentFlowFixtures.GET_CALL_ID;
import static io.camunda.connector.e2e.agenticai.aiagent.DocumentFlowFixtures.getDocumentTurn;
import static io.camunda.connector.e2e.agenticai.aiagent.ToolCallResultDocumentAssertions.assertDocumentContentBlockJson;
import static io.camunda.connector.e2e.agenticai.aiagent.ToolCallResultDocumentAssertions.assertDocumentContentUserMessage;
import static io.camunda.connector.e2e.agenticai.aiagent.ToolCallResultDocumentAssertions.assertDocumentHandlePart;
import static io.camunda.connector.e2e.agenticai.aiagent.ToolCallResultDocumentAssertions.contentBlockType;
import static io.camunda.connector.e2e.agenticai.aiagent.ToolCallResultDocumentAssertions.contentFollowsSentence;
import static io.camunda.connector.e2e.agenticai.aiagent.ToolCallResultDocumentAssertions.mimeType;
import static io.camunda.connector.e2e.agenticai.aiagent.fixtures.AgentTestFixtures.AI_AGENT_ELEMENT_ID;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.connector.agenticai.aiagent.model.AgentMetrics;
import io.camunda.connector.e2e.agenticai.aiagent.ToolCallResultDocumentAssertions.DocumentWithContent;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.openai.OpenAiCompletionsChatModelStubs;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.openai.OpenAiCompletionsChatModelStubs.Turn;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.openai.OpenAiCompletionsRecordedConversation;
import io.camunda.connector.e2e.agenticai.assertj.AgentSubProcessResponseAssert;
import io.camunda.connector.test.utils.annotation.SlowTest;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

@SlowTest
public class AgentSubProcessUserPromptDocumentsTests extends BaseAgentSubProcessTest {

  private static final String UNSUPPORTED_ZIP_MESSAGE =
      "Unsupported content type 'application/zip' for document with reference"
          + " 'CamundaDocumentReferenceModel\\[storeId=in-memory,"
          + " documentId=[0-9a-f-]{36}, contentHash=\\d+,"
          + " metadata=CamundaDocumentMetadataModel\\[contentType=application/zip,"
          + " expiresAt=null, size=null, fileName=null, processDefinitionId=null,"
          + " processInstanceKey=null, customProperties=null\\]\\]'";

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
            AgentSubProcessResponseAssert.assertThat(agentResponse)
                .isReady()
                .hasMetrics(new AgentMetrics(1, new AgentMetrics.TokenUsage(10, 20), 0))
                .hasResponseMessageText(responseText)
                .hasResponseText(responseText));

    assertThat(userFeedbackJobWorkerCounter.get()).isEqualTo(1);
  }

  /**
   * The model loads the document with {@code c8__load_document}: the next request carries the
   * handle and the content of the document in the form its content type has on the wire.
   */
  @ParameterizedTest
  @ValueSource(strings = {"test.pdf", "test.txt"})
  void loadsDocumentOfTypeOnRequest(String filename) throws Exception {
    final var initialUserPrompt = "Summarize the following document";
    final var responseText = "TL;DR: it is pretty interesting";

    final var turns = List.of(getDocumentTurn(), Turn.text(responseText, 10, 20));
    OpenAiCompletionsChatModelStubs.stubConversation(turns.toArray(Turn[]::new));
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
    assertThat(recorded.modelCallCount()).isEqualTo(turns.size());

    // the first request shows the handle only
    assertThat(recorded.requests().getFirst().messages()).hasSize(2);

    final var messages = recorded.lastRequest().messages();
    assertThat(messages).hasSize(5);
    assertThat(messages)
        .extracting(m -> m.role())
        .containsExactly("system", "user", "assistant", "tool", "user");
    final var promptHandle =
        assertDocumentHandlePart(messages.get(1).contentParts().get(1), mimeType(filename));
    assertThat(messages.get(3).toolCallId()).isEqualTo(GET_CALL_ID);
    assertThat(messages.get(3).content()).isEqualTo(contentFollowsSentence(promptHandle));

    assertDocumentContentUserMessage(
        messages.get(4),
        DocumentWithContent.of(
            promptHandle,
            block ->
                assertDocumentContentBlockJson(
                    block, contentBlockType(filename), mimeType(filename))));

    assertAgentResponse(
        zeebeTest,
        agentResponse ->
            AgentSubProcessResponseAssert.assertThat(agentResponse)
                .isReady()
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
            AgentSubProcessResponseAssert.assertThat(agentResponse)
                .isReady()
                .hasMetrics(new AgentMetrics(1, new AgentMetrics.TokenUsage(10, 20), 0))
                .hasResponseMessageText(responseText)
                .hasResponseText(responseText));

    assertThat(userFeedbackJobWorkerCounter.get()).isEqualTo(1);
  }

  /**
   * With the document delivery {@code content} of the user prompt the content of the document is
   * part of the user message of the first request, next to its handle: no {@code c8__load_document}
   * call is needed.
   */
  @ParameterizedTest
  @ValueSource(strings = {"test.pdf", "test.txt"})
  void addsTheContentOfPromptDocumentsWithTheDeliveryContent(String filename) throws Exception {
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
            AgentSubProcessResponseAssert.assertThat(agentResponse)
                .isReady()
                .hasResponseText(responseText));
  }

  /**
   * Without a document delivery, as in a process modeled before the option existed, the content of
   * every prompt document is part of the user message of the first request, next to its handle.
   */
  @ParameterizedTest
  @ValueSource(strings = {"test.pdf", "test.txt"})
  void addsTheContentOfPromptDocumentsWithoutADelivery(String filename) throws Exception {
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
            AgentSubProcessResponseAssert.assertThat(agentResponse)
                .isReady()
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

  /** The error variables of the incident message are a map, whose fields come in any order. */
  private static String unsupportedZipIncidentMessage() {
    final var fields =
        List.of(
            Pattern.quote("message=") + UNSUPPORTED_ZIP_MESSAGE,
            Pattern.quote("type=io.camunda.connector.api.error.ConnectorException"),
            Pattern.quote("code=FAILED_MODEL_CALL"));
    final var orders =
        List.of(
            List.of(0, 1, 2),
            List.of(0, 2, 1),
            List.of(1, 0, 2),
            List.of(1, 2, 0),
            List.of(2, 0, 1),
            List.of(2, 1, 0));
    final var alternatives =
        orders.stream()
            .map(order -> order.stream().map(fields::get).collect(Collectors.joining(", ")))
            .collect(Collectors.joining("|"));
    return UNSUPPORTED_ZIP_MESSAGE
        + Pattern.quote(" | Error variables: {error={")
        + "(?:"
        + alternatives
        + ")"
        + Pattern.quote("}}");
  }
}
