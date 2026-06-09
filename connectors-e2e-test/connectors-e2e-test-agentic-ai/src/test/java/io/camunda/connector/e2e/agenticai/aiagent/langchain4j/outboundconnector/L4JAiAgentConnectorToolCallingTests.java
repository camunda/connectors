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
import static io.camunda.connector.e2e.agenticai.aiagent.ToolCallResultDocumentAssertions.EXTRACTED_DOCUMENTS_PREAMBLE;
import static io.camunda.connector.e2e.agenticai.aiagent.ToolCallResultDocumentAssertions.parseDocumentReference;
import static io.camunda.connector.e2e.agenticai.aiagent.ToolCallResultDocumentAssertions.parseExternalDocumentReference;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import io.camunda.connector.agenticai.aiagent.model.AgentMetrics;
import io.camunda.connector.agenticai.model.message.DocumentReferenceXmlTag.CamundaDocumentReferenceXmlTag;
import io.camunda.connector.agenticai.model.message.DocumentReferenceXmlTag.ExternalDocumentReferenceXmlTag;
import io.camunda.connector.e2e.agenticai.aiagent.langchain4j.wiremock.OpenAiChatModelStubs;
import io.camunda.connector.e2e.agenticai.aiagent.langchain4j.wiremock.OpenAiChatModelStubs.ToolCall;
import io.camunda.connector.e2e.agenticai.aiagent.langchain4j.wiremock.OpenAiChatModelStubs.Turn;
import io.camunda.connector.e2e.agenticai.aiagent.langchain4j.wiremock.RecordedLlmConversation;
import io.camunda.connector.e2e.agenticai.assertj.AgentResponseAssert;
import io.camunda.connector.test.utils.annotation.SlowTest;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@SlowTest
public class L4JAiAgentConnectorToolCallingTests extends BaseWireMockL4JAiAgentConnectorTest {

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
  void supportsDocumentResponsesFromToolCalls(String filename, String type, String mimeType)
      throws Exception {
    final var initialUserPrompt = "Go and download a document!";
    final var fileUrl = wireMock.getHttpBaseUrl() + "/" + filename;
    final var aiFinalResponseText = "The content type is '%s'".formatted(mimeType);

    OpenAiChatModelStubs.stubConversation(
        Turn.toolCalls(
            "The user asked me to download a document. I will call the Download_A_File tool to do so.",
            10,
            20,
            ToolCall.of("aaa111", "Download_A_File", "{\"url\": \"%s\"}".formatted(fileUrl))),
        Turn.text(
            "I loaded a document and learned that it contains interesting data. Anything specific you want to know?",
            100,
            200),
        Turn.text(aiFinalResponseText, 11, 22));

    enqueueUserFeedback(userFollowUpFeedback("What is the content type?"), userSatisfiedFeedback());

    final var zeebeTest =
        createProcessInstance(e -> e, Map.of("userPrompt", initialUserPrompt))
            .waitForProcessCompletion();

    final var recorded = RecordedLlmConversation.recorded();
    assertThat(recorded.modelCallCount()).isEqualTo(3);

    final var lastMessages = recorded.lastRequest().messages();
    assertThat(lastMessages).hasSize(7);

    assertRole(lastMessages.get(0), "system");
    assertRole(lastMessages.get(1), "user"); // initial prompt
    assertRole(lastMessages.get(2), "assistant"); // tool call

    // tool result: document serialized as a document reference (parsed from the tool message text)
    assertRole(lastMessages.get(3), "tool");
    assertThat(lastMessages.get(3).path("tool_call_id").asText()).isEqualTo("aaa111");
    final var documentReference =
        parseDocumentReference(lastMessages.get(3).path("content").asText());
    assertThat(documentReference.metadata().contentType()).isEqualTo(mimeType);

    // synthetic user message carrying the extracted document content
    final var expectedXmlTag =
        new CamundaDocumentReferenceXmlTag(
                "aaa111",
                "Download_A_File",
                documentReference.documentId(),
                documentReference.storeId(),
                documentReference.metadata().getContentType(),
                documentReference.metadata().getFileName())
            .toXml();
    assertExtractedDocumentsUserMessage(lastMessages.get(4), expectedXmlTag, type, mimeType);

    assertRole(lastMessages.get(5), "assistant"); // response after tool
    assertRole(lastMessages.get(6), "user"); // follow-up prompt
    assertThat(textContent(lastMessages.get(6))).isEqualTo("What is the content type?");

    assertAgentResponse(
        zeebeTest,
        agentResponse ->
            AgentResponseAssert.assertThat(agentResponse)
                .isReady()
                .hasNoToolCalls()
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

    OpenAiChatModelStubs.stubConversation(
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
        createProcessInstance(e -> e, Map.of("userPrompt", initialUserPrompt))
            .waitForProcessCompletion();

    final var recorded = RecordedLlmConversation.recorded();
    assertThat(recorded.modelCallCount()).isEqualTo(2);

    final var lastMessages = recorded.lastRequest().messages();
    assertThat(lastMessages).hasSize(5);

    assertRole(lastMessages.get(0), "system");
    assertRole(lastMessages.get(1), "user"); // initial prompt
    assertRole(lastMessages.get(2), "assistant"); // tool call

    // tool result: external document reference serialized as { url, name }
    assertRole(lastMessages.get(3), "tool");
    final var externalRef =
        parseExternalDocumentReference(lastMessages.get(3).path("content").asText());
    assertThat(externalRef.url()).isEqualTo(docUrl);
    assertThat(externalRef.name()).isEqualTo(docName);

    // synthetic user message: external doc rendered as <doc url="…" name="…" /> + content block
    final var expectedXmlTag =
        new ExternalDocumentReferenceXmlTag(
                "ext111", "External_File_Reference", externalRef.url(), externalRef.name())
            .toXml();
    assertExtractedDocumentsUserMessage(
        lastMessages.get(4), expectedXmlTag, "base64", "application/pdf");

    assertAgentResponse(
        zeebeTest,
        agentResponse ->
            AgentResponseAssert.assertThat(agentResponse)
                .isReady()
                .hasNoToolCalls()
                .hasMetrics(new AgentMetrics(2, new AgentMetrics.TokenUsage(21, 42), 1))
                .hasResponseMessageText(aiFinalResponseText)
                .hasResponseText(aiFinalResponseText));

    assertThat(userFeedbackJobWorkerCounter.get()).isEqualTo(1);
  }

  // ---------------------------------------------------------------------------
  // OpenAI-compatible multimodal message assertions
  // ---------------------------------------------------------------------------

  private static void assertRole(JsonNode message, String expectedRole) {
    assertThat(message.path("role").asText()).isEqualTo(expectedRole);
  }

  private static String textContent(JsonNode message) {
    final JsonNode content = message.get("content");
    return content != null && content.isTextual() ? content.asText() : null;
  }

  /**
   * Asserts the synthetic "extracted documents" user message in OpenAI-compatible form: a
   * multimodal {@code content} array of {@code preamble (text) + <doc/> tag (text) + content
   * block}.
   *
   * <p>The content block is classified the same way as {@link
   * io.camunda.connector.e2e.agenticai.aiagent.ToolCallResultDocumentAssertions#assertDocumentContentBlock}:
   * {@code text} → {@code type=text}; {@code application/pdf} → {@code type=file}; otherwise {@code
   * type=image_url} with a {@code data:<mime>;base64,} URL.
   */
  private static void assertExtractedDocumentsUserMessage(
      JsonNode message, String expectedXmlTag, String expectedType, String expectedMimeType) {
    assertRole(message, "user");

    final JsonNode content = message.get("content");
    assertThat(content).as("multimodal content array").isNotNull();
    assertThat(content.isArray()).as("content is an array").isTrue();
    final List<JsonNode> parts = new java.util.ArrayList<>();
    content.forEach(parts::add);
    assertThat(parts).as("preamble + <doc/> tag + content block").hasSize(3);

    assertThat(parts.get(0).path("type").asText()).isEqualTo("text");
    assertThat(parts.get(0).path("text").asText()).isEqualTo(EXTRACTED_DOCUMENTS_PREAMBLE);

    assertThat(parts.get(1).path("type").asText()).isEqualTo("text");
    assertThat(parts.get(1).path("text").asText()).isEqualTo(expectedXmlTag);

    final JsonNode block = parts.get(2);
    if ("text".equals(expectedType)) {
      assertThat(block.path("type").asText()).isEqualTo("text");
      assertThat(block.path("text").asText()).isNotBlank();
    } else if ("application/pdf".equals(expectedMimeType)) {
      assertThat(block.path("type").asText()).isEqualTo("file");
      assertThat(block.path("file").path("file_data").asText()).isNotBlank();
    } else {
      assertThat(block.path("type").asText()).isEqualTo("image_url");
      assertThat(block.path("image_url").path("url").asText())
          .startsWith("data:" + expectedMimeType + ";base64,");
    }
  }
}
