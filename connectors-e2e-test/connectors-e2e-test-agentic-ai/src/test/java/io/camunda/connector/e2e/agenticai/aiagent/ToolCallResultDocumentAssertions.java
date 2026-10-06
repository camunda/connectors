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
package io.camunda.connector.e2e.agenticai.aiagent;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import io.camunda.connector.agenticai.aiagent.model.message.DocumentReferenceXmlTag;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.openai.OpenAiCompletionsRecordedConversation.RecordedMessage;
import java.util.HashMap;
import java.util.List;
import java.util.function.Consumer;
import java.util.regex.Pattern;

/**
 * Assertion helpers for the synthetic user message that carries documents extracted from tool call
 * results.
 *
 * <p>The user message has the structure {@code preamble + (xml-tag, content-block)*}, so it can
 * carry documents from one or many tool call results. Use {@link
 * #assertExtractedDocumentsUserMessage(JsonNode, ExtractedDocument...)} to assert against any
 * number of documents in one go.
 *
 * <p>All assertions operate on OpenAI-compatible wire-format {@link JsonNode} objects recorded by
 * WireMock — no framework-specific types required.
 */
public final class ToolCallResultDocumentAssertions {

  public static final String EXTRACTED_DOCUMENTS_PREAMBLE =
      "Documents extracted from tool calls (<doc /> tag + content pair):";

  private static final Pattern DOC_TAG = Pattern.compile("<doc ([^>]*?)\\s*/>");
  private static final Pattern TAG_ATTRIBUTE = Pattern.compile("(\\w+)=\"([^\"]*)\"");

  private ToolCallResultDocumentAssertions() {}

  /**
   * Parses the first {@code <doc/>} tag out of a serialized tool call result (a bare tag or a JSON
   * value embedding one) into the production {@link DocumentReferenceXmlTag}, without tool call
   * attributes. Also asserts that the text does not carry any document reference field.
   *
   * <p>Throws {@link AssertionError} if no tag is present.
   */
  public static DocumentReferenceXmlTag parseDocumentTag(String toolResultText) {
    assertThat(toolResultText)
        .as("tool result text")
        .doesNotContain("storeId", "contentHash", "camunda.document.type");

    // a tag embedded in a JSON string has its quotes escaped
    final var matcher = DOC_TAG.matcher(toolResultText.replace("\\\"", "\""));
    if (!matcher.find()) {
      throw new AssertionError("No <doc/> tag found in tool result text: " + toolResultText);
    }
    final var attributes = new HashMap<String, String>();
    final var attributeMatcher = TAG_ATTRIBUTE.matcher(matcher.group(1));
    while (attributeMatcher.find()) {
      attributes.put(attributeMatcher.group(1), unescapeXml(attributeMatcher.group(2)));
    }
    assertThat(attributes).as("tag attributes").containsKey("id");
    assertThat(attributes.keySet())
        .as("tag attributes")
        .isSubsetOf("id", "fileName", "contentType", "source", "toolName", "toolCallId");
    return new DocumentReferenceXmlTag(
        attributes.get("id"),
        attributes.get("fileName"),
        attributes.get("contentType"),
        null,
        null,
        null);
  }

  private static String unescapeXml(String value) {
    return value
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&apos;", "'")
        .replace("&amp;", "&");
  }

  /**
   * Asserts that {@code message} is a wire-format user message (OpenAI-compatible JSON) with the
   * standard "extracted documents" structure: a preamble text block followed by an {@code (xml-tag,
   * content-block)} pair per expected document.
   *
   * <p>The XML tag is built using the production {@link DocumentReferenceXmlTag}, so the assertion
   * stays in sync with the production format. The content-block {@link JsonNode} is passed directly
   * to each {@link ExtractedDocument#contentBlockAssertion()}.
   */
  public static void assertExtractedDocumentsUserMessage(
      RecordedMessage message, ExtractedDocument... expectedDocuments) {
    assertThat(message.role()).as("message role").isEqualTo("user");
    final List<JsonNode> contentParts = message.contentParts();
    assertThat(contentParts).as("content should be an array").isNotEmpty();
    assertThat(contentParts.size())
        .as("expected preamble + 2 contents per document (%d documents)", expectedDocuments.length)
        .isEqualTo(1 + 2 * expectedDocuments.length);

    assertThat(contentParts.get(0).path("type").asText()).as("preamble type").isEqualTo("text");
    assertThat(contentParts.get(0).path("text").asText())
        .as("preamble text")
        .isEqualTo(EXTRACTED_DOCUMENTS_PREAMBLE);

    for (int i = 0; i < expectedDocuments.length; i++) {
      final var expected = expectedDocuments[i];
      final var tagIndex = 1 + 2 * i;
      final var contentIndex = tagIndex + 1;

      assertThat(contentParts.get(tagIndex).path("type").asText())
          .as("XML tag type at index %d", tagIndex)
          .isEqualTo("text");
      assertThat(contentParts.get(tagIndex).path("text").asText())
          .as("XML tag text at index %d", tagIndex)
          .isEqualTo(expected.expectedXmlTag());

      final var contentBlock = contentParts.get(contentIndex);
      try {
        expected.contentBlockAssertion().accept(contentBlock);
      } catch (AssertionError e) {
        throw new AssertionError(
            "Content block at index %d (document %d) failed: %s"
                .formatted(contentIndex, i, e.getMessage()),
            e);
      }
    }
  }

  /**
   * Asserts that a wire-format content part (OpenAI JSON) is a text part holding the {@code <doc/>}
   * tag of a user prompt document that exposes the document id and its source only: no store id,
   * content hash or URL.
   */
  public static void assertDocumentTagPart(JsonNode part) {
    assertThat(part.path("type").asText()).as("document tag part type").isEqualTo("text");
    assertThat(part.path("text").asText())
        .as("document tag text")
        .startsWith("<doc id=\"")
        .contains(" source=\"user_message\"")
        .endsWith(" />")
        .doesNotContain("storeId", "contentHash", "documentId", "http");
  }

  /**
   * Asserts a wire-format content block node (OpenAI JSON) based on a coarse type/mime
   * classification used by user-prompt document tests.
   */
  public static void assertDocumentContentBlockJson(
      JsonNode block, String expectedType, String expectedMimeType) {
    if ("text".equals(expectedType)) {
      assertThat(block.path("type").asText()).as("content block type for text").isEqualTo("text");
      assertThat(block.path("text").asText()).as("text content").isNotBlank();
    } else if ("application/pdf".equals(expectedMimeType)) {
      assertThat(block.path("type").asText()).as("content block type for PDF").isEqualTo("file");
      assertThat(block.path("file").path("file_data").asText())
          .as("PDF file_data")
          .startsWith("data:application/pdf;base64,");
    } else {
      assertThat(block.path("type").asText())
          .as("content block type for image")
          .isEqualTo("image_url");
      assertThat(block.path("image_url").path("url").asText())
          .as("image data URL")
          .startsWith("data:" + expectedMimeType + ";base64,");
    }
  }

  /**
   * Specification of one expected extracted document slot in the synthetic user message: an XML tag
   * with optional tool call correlation attributes plus a content block check operating on the raw
   * OpenAI wire-format {@link JsonNode}.
   */
  public record ExtractedDocument(String expectedXmlTag, Consumer<JsonNode> contentBlockAssertion) {

    /**
     * Document extracted from a tool call result, identified by the tag the tool result itself
     * showed (see {@link #parseDocumentTag}).
     */
    public static ExtractedDocument forToolCall(
        String toolCallId,
        String toolName,
        DocumentReferenceXmlTag resultTag,
        Consumer<JsonNode> contentBlockAssertion) {
      return new ExtractedDocument(
          new DocumentReferenceXmlTag(
                  resultTag.id(),
                  resultTag.fileName(),
                  resultTag.contentType(),
                  null,
                  toolName,
                  toolCallId)
              .toXml(),
          contentBlockAssertion);
    }
  }
}
