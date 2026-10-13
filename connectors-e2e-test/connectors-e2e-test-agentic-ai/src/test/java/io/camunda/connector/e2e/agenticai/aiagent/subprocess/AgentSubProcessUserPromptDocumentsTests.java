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
import static io.camunda.connector.e2e.agenticai.aiagent.ToolCallResultDocumentAssertions.assertDocumentTagPart;
import static io.camunda.connector.e2e.agenticai.aiagent.ToolCallResultDocumentAssertions.parseDocumentTag;
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@SlowTest
public class AgentSubProcessUserPromptDocumentsTests extends BaseAgentSubProcessTest {

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
    assertNoDocumentInternalsInLlmRequests();
    assertThat(recorded.modelCallCount()).isEqualTo(1);
    assertThat(recorded.lastRequest().messages()).hasSize(2);

    assertThat(recorded.lastRequest().messages().get(0).role()).isEqualTo("system");
    assertThat(recorded.lastRequest().messages().get(0).content()).isEqualTo(SYSTEM_PROMPT);

    // user message: the prompt text and the <doc/> tag of the document, not its content
    final var userMessage = recorded.lastRequest().messages().get(1);
    assertThat(userMessage.role()).isEqualTo("user");
    assertThat(userMessage.contentParts()).as("prompt text + <doc/> tag").hasSize(2);
    assertThat(userMessage.contentParts().get(0).path("type").asText()).isEqualTo("text");
    assertThat(userMessage.contentParts().get(0).path("text").asText())
        .isEqualTo(initialUserPrompt);
    assertDocumentTagPart(userMessage.contentParts().get(1));
    assertThat(
            parseDocumentTag(userMessage.contentParts().get(1).path("text").asText()).contentType())
        .isEqualTo(mimeType(filename));

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
   * The model loads the document with {@code c8__get_document}: the next request carries the tag
   * and the content of the document in the form its content type has on the wire.
   */
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
  void loadsDocumentOfTypeOnRequest(String filename) throws Exception {
    final var initialUserPrompt = "Summarize the following document";
    final var responseText = "TL;DR: it is pretty interesting";

    OpenAiCompletionsChatModelStubs.stubConversation(
        getDocumentTurn(), Turn.text(responseText, 10, 20));
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
    assertNoDocumentInternalsInLlmRequests();
    assertThat(recorded.modelCallCount()).isEqualTo(2);

    // the first request shows the tag only
    assertThat(recorded.requests().getFirst().messages()).hasSize(2);

    final var messages = recorded.lastRequest().messages();
    assertThat(messages).hasSize(5);
    assertThat(messages)
        .extracting(m -> m.role())
        .containsExactly("system", "user", "assistant", "tool", "user");
    final var promptTag =
        parseDocumentTag(messages.get(1).contentParts().get(1).path("text").asText());
    assertThat(messages.get(3).toolCallId()).isEqualTo(GET_CALL_ID);
    assertThat(messages.get(3).content())
        .startsWith("Loaded the document.\n<doc id=\"" + promptTag.id() + "\"")
        .endsWith(" />\nIts content follows in the next message.");

    assertDocumentContentUserMessage(
        messages.get(4),
        DocumentWithContent.forUserPrompt(
            promptTag,
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
                        wireMock.getHttpBaseUrl() + "/test.jpg"))));

    final var recorded = OpenAiCompletionsRecordedConversation.recorded();
    assertThat(recorded.modelCallCount()).isEqualTo(1);
    assertThat(recorded.lastRequest().messages()).hasSize(2);

    assertThat(recorded.lastRequest().messages().get(0).role()).isEqualTo("system");
    assertThat(recorded.lastRequest().messages().get(0).content()).isEqualTo(SYSTEM_PROMPT);

    final var userMessage = recorded.lastRequest().messages().get(1);
    assertThat(userMessage.role()).isEqualTo("user");
    assertThat(userMessage.contentParts()).isNotEmpty();
    assertThat(userMessage.contentParts()).as("prompt text + 2 <doc/> tags").hasSize(3);
    assertThat(userMessage.contentParts().get(0).path("type").asText()).isEqualTo("text");
    assertThat(userMessage.contentParts().get(0).path("text").asText())
        .isEqualTo(initialUserPrompt);
    assertDocumentTagPart(userMessage.contentParts().get(1));
    assertDocumentTagPart(userMessage.contentParts().get(2));

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
   * The model asks to load a document of a type outside the allowlist: the tool answers with an
   * error before loading it, no incident is raised and the conversation continues.
   */
  @Test
  void rejectsLoadingADocumentOfATypeThatIsNotSupported() throws Exception {
    final var responseText = "I cannot read it, so I will not summarize it.";

    OpenAiCompletionsChatModelStubs.stubConversation(
        getDocumentTurn(), Turn.text(responseText, 10, 20));
    enqueueUserFeedback(userSatisfiedFeedback());

    final var zeebeTest =
        awaitProcessCompletion(
            createProcessInstance(
                elementTemplate ->
                    elementTemplate.property("retryCount", "3").property("retryBackoff", "PT2S"),
                Map.of(
                    "userPrompt",
                    "Summarize the following document",
                    "downloadUrls",
                    List.of(wireMock.getHttpBaseUrl() + "/unsupported.zip"))));

    final var recorded = OpenAiCompletionsRecordedConversation.recorded();
    assertNoDocumentInternalsInLlmRequests();
    assertThat(recorded.modelCallCount()).isEqualTo(2);

    // the error is the tool result; no message with the content of the document follows
    final var messages = recorded.lastRequest().messages();
    assertThat(messages)
        .extracting(m -> m.role())
        .containsExactly("system", "user", "assistant", "tool");
    assertThat(messages.get(3).toolCallId()).isEqualTo(GET_CALL_ID);
    assertThat(messages.get(3).content())
        .isEqualTo(
            "The document is of type application/zip which cannot be loaded into the conversation; pass its id to a tool that processes it.");

    assertAgentResponse(
        zeebeTest,
        agentResponse ->
            AgentSubProcessResponseAssert.assertThat(agentResponse)
                .isReady()
                .hasResponseText(responseText));

    assertThat(userFeedbackJobWorkerCounter.get()).isEqualTo(1);
  }

  /**
   * With the document delivery {@code content} of the user prompt the content of the document is
   * part of the user message of the first request, next to its tag: no {@code c8__get_document}
   * call is needed.
   */
  @ParameterizedTest
  @ValueSource(strings = {"test.csv", "test.jpg", "test.pdf", "test.txt"})
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
    assertNoDocumentInternalsInLlmRequests();
    assertThat(recorded.modelCallCount()).isEqualTo(1);
    assertThat(recorded.lastRequest().toolNames()).contains("c8__get_document");

    final var userMessage = recorded.lastRequest().messages().get(1);
    assertThat(userMessage.role()).isEqualTo("user");
    assertThat(userMessage.contentParts()).as("prompt text + <doc/> tag + content").hasSize(3);
    assertThat(userMessage.contentParts().get(0).path("text").asText())
        .isEqualTo(initialUserPrompt);
    assertDocumentTagPart(userMessage.contentParts().get(1));
    assertThat(
            parseDocumentTag(userMessage.contentParts().get(1).path("text").asText()).contentType())
        .isEqualTo(mimeType(filename));
    assertDocumentContentBlockJson(
        userMessage.contentParts().get(2), contentBlockType(filename), mimeType(filename));

    assertAgentResponse(
        zeebeTest,
        agentResponse ->
            AgentSubProcessResponseAssert.assertThat(agentResponse)
                .isReady()
                .hasResponseText(responseText));
  }

  /** With the document delivery {@code reference}, the documents stay references. */
  @Test
  void keepsPromptDocumentsAsReferencesWithTheDeliveryReference() throws Exception {
    OpenAiCompletionsChatModelStubs.stubConversation(Turn.text("TL;DR", 10, 20));
    enqueueUserFeedback(userSatisfiedFeedback());

    awaitProcessCompletion(
        createProcessInstance(
            elementTemplate ->
                elementTemplate
                    .property("retryCount", "3")
                    .property("retryBackoff", "PT2S")
                    .property("data.userPrompt.documentDelivery", "reference"),
            Map.of(
                "userPrompt",
                "Summarize the following document",
                "downloadUrls",
                List.of(wireMock.getHttpBaseUrl() + "/test.txt"))));

    final var userMessage =
        OpenAiCompletionsRecordedConversation.recorded().lastRequest().messages().get(1);
    assertThat(userMessage.contentParts()).as("prompt text + <doc/> tag").hasSize(2);
    assertDocumentTagPart(userMessage.contentParts().get(1));
  }

  /**
   * A document that fails the content guard stays a reference in the user message, followed by a
   * note that tells the model why its content was not added; the conversation goes on without an
   * incident.
   */
  @Test
  void keepsAPromptDocumentThatFailsTheGuardAsAReferenceWithANote() throws Exception {
    OpenAiCompletionsChatModelStubs.stubConversation(Turn.text("I cannot read it.", 10, 20));
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
                    "Summarize the following document",
                    "downloadUrls",
                    List.of(wireMock.getHttpBaseUrl() + "/unsupported.zip"))));

    final var userMessage =
        OpenAiCompletionsRecordedConversation.recorded().lastRequest().messages().get(1);
    assertThat(userMessage.contentParts()).as("prompt text + <doc/> tag + note").hasSize(3);
    assertDocumentTagPart(userMessage.contentParts().get(1));
    final var tag = parseDocumentTag(userMessage.contentParts().get(1).path("text").asText());
    assertThat(userMessage.contentParts().get(2).path("text").asText())
        .isEqualTo(
            "Document %s was not loaded automatically: it is of type application/zip which cannot be loaded into the conversation. Pass its id to a tool that processes it."
                .formatted(tag.id()));

    assertAgentResponse(
        zeebeTest,
        agentResponse ->
            AgentSubProcessResponseAssert.assertThat(agentResponse)
                .isReady()
                .hasResponseText("I cannot read it."));
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
