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

import static io.camunda.connector.e2e.agenticai.aiagent.ToolCallResultDocumentAssertions.EXTRACTED_DOCUMENTS_PREAMBLE;
import static io.camunda.connector.e2e.agenticai.aiagent.ToolCallResultDocumentAssertions.assertDocumentContentBlockJson;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.agenticai.aiagent.document.LegacyDocumentReferenceXmlTag.CamundaDocumentReferenceXmlTag;
import io.camunda.connector.agenticai.aiagent.document.LegacyDocumentReferenceXmlTag.ExternalDocumentReferenceXmlTag;
import io.camunda.connector.document.jackson.DocumentReferenceModel;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.openai.OpenAiCompletionsChatModelStubs;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.openai.OpenAiCompletionsChatModelStubs.ToolCall;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.openai.OpenAiCompletionsChatModelStubs.Turn;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.openai.OpenAiCompletionsRecordedConversation;
import io.camunda.connector.e2e.agenticai.assertj.AgentResponseAssert;
import io.camunda.connector.test.utils.annotation.SlowTest;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * An agent instance that was created before the documents feature (its agent context carries no
 * features) keeps the document handling it was created with: documents as bare content, the tool
 * result documents as the reference JSON followed by a tag in the older attribute format and the
 * content, no {@code <doc id/>} tags, no registry and no built-in tools. The expectations are the
 * wire format of Camunda 8.10.
 */
@SlowTest
public class AgentTaskLegacyInstanceDocumentsTests extends BaseAgentTaskTest {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  /** The context of an agent instance that exists without features. */
  private static final Map<String, Object> LEGACY_AGENT_CONTEXT =
      Map.of("schemaVersion", 1, "state", "READY");

  private static Map<String, Object> variables(String userPrompt, Object... more) {
    final var variables = new java.util.HashMap<String, Object>();
    variables.put("userPrompt", userPrompt);
    variables.put("agent", Map.of("context", LEGACY_AGENT_CONTEXT));
    for (int i = 0; i < more.length; i += 2) {
      variables.put((String) more[i], more[i + 1]);
    }
    return variables;
  }

  @Test
  void keepsUserPromptDocumentsAsBareContentAndOffersNoBuiltInTool() throws Exception {
    final var initialUserPrompt = "Summarize the following documents";
    final var responseText = "TL;DR: they contain a lot of interesting information.";

    OpenAiCompletionsChatModelStubs.stubConversation(Turn.text(responseText, 10, 20));
    enqueueUserFeedback(userSatisfiedFeedback());

    final var zeebeTest =
        awaitProcessCompletion(
            createProcessInstance(
                elementTemplate ->
                    elementTemplate.property("retryCount", "3").property("retryBackoff", "PT2S"),
                variables(
                    initialUserPrompt,
                    "downloadUrls",
                    List.of(
                        wireMock.getHttpBaseUrl() + "/test.txt",
                        wireMock.getHttpBaseUrl() + "/test.jpg"))));

    final var recorded = OpenAiCompletionsRecordedConversation.recorded();
    assertThat(recorded.modelCallCount()).isEqualTo(1);
    assertThat(recorded.lastRequest().messages()).hasSize(2);

    // the tools of the process only, no built-in tool
    assertThat(recorded.lastRequest().toolDefinitions())
        .containsExactlyInAnyOrderElementsOf(expectedTools());

    // user message: prompt text + two document blocks, no tags
    final var userMessage = recorded.lastRequest().messages().get(1);
    assertThat(userMessage.role()).isEqualTo("user");
    assertThat(userMessage.contentParts()).hasSize(3);
    assertThat(userMessage.contentParts().get(0).path("type").asText()).isEqualTo("text");
    assertThat(userMessage.contentParts().get(0).path("text").asText())
        .isEqualTo(initialUserPrompt);
    assertDocumentContentBlockJson(userMessage.contentParts().get(1), "text", "text/plain");
    assertDocumentContentBlockJson(userMessage.contentParts().get(2), "base64", "image/jpeg");

    assertAgentResponse(
        zeebeTest,
        agentResponse -> {
          AgentResponseAssert.assertThat(agentResponse)
              .isReady()
              .hasNoToolCalls()
              .hasResponseText(responseText);
          // the instance never gets features, a registry or response documents
          assertThat(agentResponse.context().features()).isEmpty();
          assertThat(agentResponse.context().documents()).isEmpty();
          assertThat(agentResponse.context().responseDocuments()).isEmpty();
          assertThat(agentResponse.responseDocuments()).isEmpty();
        });
  }

  @Test
  void keepsToolResultDocumentsAsTheReferenceFollowedByTheOlderTagAndTheContent() throws Exception {
    final var fileUrl = wireMock.getHttpBaseUrl() + "/test.pdf";
    final var aiFinalResponseText = "The content type is 'application/pdf'";

    OpenAiCompletionsChatModelStubs.stubConversation(
        Turn.toolCalls(
            "The user asked me to download a document. I will call the Download_A_File tool to do so.",
            10,
            20,
            ToolCall.of("aaa111", "Download_A_File", "{\"url\": \"%s\"}".formatted(fileUrl))),
        Turn.text(aiFinalResponseText, 11, 22));
    enqueueUserFeedback(userSatisfiedFeedback());

    final var zeebeTest =
        awaitProcessCompletion(
            createProcessInstance(e -> e, variables("Go and download a document!")));

    final var recorded = OpenAiCompletionsRecordedConversation.recorded();
    assertThat(recorded.modelCallCount()).isEqualTo(2);
    assertThat(recorded.lastRequest().toolDefinitions())
        .containsExactlyInAnyOrderElementsOf(expectedTools());

    final var lastMessages = recorded.lastRequest().messages();
    assertThat(lastMessages).hasSize(5);
    assertThat(lastMessages.get(3).role()).isEqualTo("tool");
    assertThat(lastMessages.get(3).toolCallId()).isEqualTo("aaa111");

    // tool result: the document serialized as its Camunda document reference
    final var reference = firstDocumentReference(lastMessages.get(3).content(), "camunda");
    assertThat(reference.path("metadata").path("contentType").asText())
        .isEqualTo("application/pdf");
    assertThat(lastMessages.get(3).content()).doesNotContain("<doc");

    // synthetic user message: preamble, the tag in the older attribute format, the content
    final var expectedXmlTag =
        new CamundaDocumentReferenceXmlTag(
                "aaa111",
                "Download_A_File",
                reference.path("documentId").asText(),
                reference.path("storeId").asText(),
                reference.path("metadata").path("contentType").asText(),
                reference.path("metadata").path("fileName").asText(null))
            .toXml();
    final var parts = lastMessages.get(4).contentParts();
    assertThat(lastMessages.get(4).role()).isEqualTo("user");
    assertThat(parts).hasSize(3);
    assertThat(parts.get(0).path("text").asText()).isEqualTo(EXTRACTED_DOCUMENTS_PREAMBLE);
    assertThat(parts.get(1).path("text").asText()).isEqualTo(expectedXmlTag);
    assertThat(parts.get(2).path("type").asText()).isEqualTo("file");

    assertAgentResponse(
        zeebeTest,
        agentResponse -> {
          AgentResponseAssert.assertThat(agentResponse)
              .isReady()
              .hasNoToolCalls()
              .hasResponseText(aiFinalResponseText);
          assertThat(agentResponse.context().features()).isEmpty();
          assertThat(agentResponse.context().documents()).isEmpty();
        });
  }

  @Test
  void keepsExternalToolResultDocumentsAsTheirReferenceWithTheUrl() throws Exception {
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

    awaitProcessCompletion(
        createProcessInstance(e -> e, variables("Reference an external document!")));

    final var lastMessages =
        OpenAiCompletionsRecordedConversation.recorded().lastRequest().messages();
    assertThat(lastMessages).hasSize(5);

    // tool result: the external reference serialized as { url, name }
    final var reference = firstDocumentReference(lastMessages.get(3).content(), "external");
    assertThat(reference.path("url").asText()).isEqualTo(docUrl);
    assertThat(reference.path("name").asText()).isEqualTo(docName);

    // synthetic user message: <doc url="..." name="..." /> followed by the content
    final var expectedXmlTag =
        new ExternalDocumentReferenceXmlTag("ext111", "External_File_Reference", docUrl, docName)
            .toXml();
    final var parts = lastMessages.get(4).contentParts();
    assertThat(parts).hasSize(3);
    assertThat(parts.get(1).path("text").asText()).isEqualTo(expectedXmlTag);
  }

  /** The first document reference of the given kind in a serialized tool result. */
  private static JsonNode firstDocumentReference(String toolResultText, String discriminator) {
    final JsonNode root;
    try {
      root = OBJECT_MAPPER.readTree(toolResultText);
    } catch (JsonProcessingException e) {
      throw new AssertionError("Failed to parse tool result text as JSON: " + toolResultText, e);
    }
    return findDocumentReference(root, discriminator)
        .orElseThrow(
            () ->
                new AssertionError(
                    "No '%s' document reference found in tool result text: %s"
                        .formatted(discriminator, toolResultText)));
  }

  private static java.util.Optional<JsonNode> findDocumentReference(
      JsonNode node, String discriminator) {
    if (node.isObject()
        && discriminator.equals(node.path(DocumentReferenceModel.DISCRIMINATOR_KEY).asText(null))) {
      return java.util.Optional.of(node);
    }
    for (var child : node) {
      final var found = findDocumentReference(child, discriminator);
      if (found.isPresent()) {
        return found;
      }
    }
    return java.util.Optional.empty();
  }
}
