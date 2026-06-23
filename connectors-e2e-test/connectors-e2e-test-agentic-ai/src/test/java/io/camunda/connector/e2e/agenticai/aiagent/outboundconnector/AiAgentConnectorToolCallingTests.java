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
package io.camunda.connector.e2e.agenticai.aiagent.outboundconnector;

import static io.camunda.connector.e2e.agenticai.aiagent.AiAgentTestFixtures.FEEDBACK_LOOP_RESPONSE_TEXT;
import static io.camunda.connector.e2e.agenticai.aiagent.ToolCallResultDocumentAssertions.EXTRACTED_DOCUMENTS_PREAMBLE;
import static io.camunda.connector.e2e.agenticai.aiagent.ToolCallResultDocumentAssertions.parseDocumentReferenceTag;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import io.camunda.connector.agenticai.aiagent.model.AgentMetrics;
import io.camunda.connector.agenticai.model.message.DocumentReferenceXmlTag;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.openai.OpenAiCompletionsChatModelStubs;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.openai.OpenAiCompletionsChatModelStubs.ToolCall;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.openai.OpenAiCompletionsChatModelStubs.Turn;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.openai.OpenAiCompletionsRecordedConversation;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.openai.OpenAiCompletionsRecordedConversation.RecordedMessage;
import io.camunda.connector.e2e.agenticai.assertj.AgentResponseAssert;
import io.camunda.connector.test.utils.annotation.SlowTest;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@SlowTest
public class AiAgentConnectorToolCallingTests extends BaseAiAgentConnectorTest {

  /** Pattern to extract a named attribute value from a {@code <doc .../>} XML tag. */
  private static final Pattern ATTR_PATTERN = Pattern.compile("(\\w+)=\"([^\"]*?)\"");

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

    OpenAiCompletionsChatModelStubs.stubConversation(
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
        awaitProcessCompletion(
            createProcessInstance(e -> e, Map.of("userPrompt", initialUserPrompt)));

    final var recorded = OpenAiCompletionsRecordedConversation.recorded();
    assertThat(recorded.modelCallCount()).isEqualTo(3);

    final var lastMessages = recorded.lastRequest().messages();
    assertThat(lastMessages).hasSize(7);

    assertRole(lastMessages.get(0), "system");
    assertRole(lastMessages.get(1), "user"); // initial prompt
    assertRole(lastMessages.get(2), "assistant"); // tool call

    // Site-1: tool result message — document serialized as a JSON-encoded "<doc .../>" tag
    // (no tool attribution). The content is a JSON string, e.g. "\"<doc id=\\\"...\\\" />\"".
    assertRole(lastMessages.get(3), "tool");
    assertThat(lastMessages.get(3).toolCallId()).isEqualTo("aaa111");
    final var site1Tag = parseDocumentReferenceTag(lastMessages.get(3).content());
    final var docId = extractAttr(site1Tag, "id");
    final var docFileName = extractAttr(site1Tag, "fileName");
    final var docContentType = extractAttr(site1Tag, "contentType");
    assertThat(docContentType).isEqualTo(mimeType);

    // Site-2: synthetic user message — same id, plus toolName and toolCallId for correlation
    final var expectedSite2Tag =
        new DocumentReferenceXmlTag(docId, docFileName, docContentType, "aaa111", "Download_A_File")
            .toXml();
    assertExtractedDocumentsUserMessage(lastMessages.get(4), expectedSite2Tag, type, mimeType);

    assertRole(lastMessages.get(5), "assistant"); // response after tool
    assertRole(lastMessages.get(6), "user"); // follow-up prompt
    assertThat(lastMessages.get(6).content()).isEqualTo("What is the content type?");

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
    final var aiFinalResponseText = "Referenced the external document.";

    OpenAiCompletionsChatModelStubs.stubConversation(
        Turn.toolCalls(
            "I will reference an externally hosted file.",
            10,
            20,
            ToolCall.of(
                "ext111",
                "External_File_Reference",
                "{\"url\": \"%s\", \"name\": \"%s\"}".formatted(docUrl, "Quarterly Report"))),
        Turn.text(aiFinalResponseText, 11, 22));

    enqueueUserFeedback(userSatisfiedFeedback());

    final var zeebeTest =
        awaitProcessCompletion(
            createProcessInstance(e -> e, Map.of("userPrompt", initialUserPrompt)));

    final var recorded = OpenAiCompletionsRecordedConversation.recorded();
    assertThat(recorded.modelCallCount()).isEqualTo(2);

    final var lastMessages = recorded.lastRequest().messages();
    assertThat(lastMessages).hasSize(5);

    assertRole(lastMessages.get(0), "system");
    assertRole(lastMessages.get(1), "user"); // initial prompt
    assertRole(lastMessages.get(2), "assistant"); // tool call

    // Site-1: tool result message — external document serialized as a JSON-encoded "<doc .../>" tag
    // (no tool attribution). The id is "ext-<sha256(url)[:12]>".
    assertRole(lastMessages.get(3), "tool");
    assertThat(lastMessages.get(3).toolCallId()).isEqualTo("ext111");
    final var site1Tag = parseDocumentReferenceTag(lastMessages.get(3).content());
    final var extDocId = extractAttr(site1Tag, "id");
    assertThat(extDocId).startsWith("ext-");

    // Site-2: synthetic user message — same id, plus the resolved fileName/contentType and the
    // toolName/toolCallId correlation attributes (the external doc's name + resolved PDF type).
    final var expectedSite2Tag =
        new DocumentReferenceXmlTag(
                extDocId,
                "Quarterly Report",
                "application/pdf",
                "ext111",
                "External_File_Reference")
            .toXml();
    assertExtractedDocumentsUserMessage(
        lastMessages.get(4), expectedSite2Tag, "base64", "application/pdf");

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

  private static void assertRole(RecordedMessage message, String expectedRole) {
    assertThat(message.role()).isEqualTo(expectedRole);
  }

  /**
   * Asserts the synthetic "extracted documents" user message in OpenAI-compatible form: a
   * multimodal {@code content} array of {@code preamble (text) + <doc/> tag (text) + content
   * block}.
   *
   * <p>The content block is classified the same way as {@link
   * io.camunda.connector.e2e.agenticai.aiagent.ToolCallResultDocumentAssertions#assertDocumentContentBlockJson}:
   * {@code text} → {@code type=text}; {@code application/pdf} → {@code type=file}; otherwise {@code
   * type=image_url} with a {@code data:<mime>;base64,} URL.
   */
  private static void assertExtractedDocumentsUserMessage(
      RecordedMessage message,
      String expectedXmlTag,
      String expectedType,
      String expectedMimeType) {
    assertRole(message, "user");

    final List<JsonNode> parts = message.contentParts();
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

  /**
   * Extracts the value of the named attribute from a {@code <doc .../> } XML tag string. Returns
   * {@code null} if the attribute is not present.
   */
  private static String extractAttr(String xmlTag, String attributeName) {
    final Matcher m = ATTR_PATTERN.matcher(xmlTag);
    while (m.find()) {
      if (attributeName.equals(m.group(1))) {
        return m.group(2);
      }
    }
    return null;
  }
}
