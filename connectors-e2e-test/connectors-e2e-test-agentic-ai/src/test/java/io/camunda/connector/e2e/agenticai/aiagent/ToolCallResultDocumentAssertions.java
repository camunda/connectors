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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.agenticai.aiagent.model.message.DocumentReferenceXmlTag;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.openai.OpenAiCompletionsRecordedConversation.RecordedMessage;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Assertion helpers for the synthetic user message that carries documents extracted from tool call
 * results.
 *
 * <p>The user message has the structure {@code preamble + (xml-tag, content-block)*}, so it can
 * carry documents from one or many tool call results. Use {@link
 * #assertExtractedDocumentsUserMessage(RecordedMessage, ExtractedDocument...)} to assert against
 * any number of documents in one go.
 *
 * <p>All assertions operate on OpenAI-compatible wire-format {@link JsonNode} objects recorded by
 * WireMock — no framework-specific types required.
 *
 * <h2>Site-1 vs Site-2 rendering</h2>
 *
 * <p><b>Site 1 — tool result message</b>: A {@link io.camunda.connector.api.document.Document} in
 * the tool call result is serialized via {@link
 * io.camunda.connector.agenticai.aiagent.framework.langchain4j.document.DocumentReferenceTagSerializer}
 * which writes the document as a JSON string containing the {@code <doc/>} XML tag (id, fileName,
 * contentType only — no tool attribution). When the result is a bare {@code Document}, the tool
 * message content is a JSON string value (e.g. {@code "\"<doc id=\\\"...\\\" />\"")}. When the
 * result is wrapped in an object (e.g. an HTTP connector returning {@code {"status":200,
 * "document":"<doc .../>"}}) the tag appears as a string value nested inside the JSON object. Use
 * {@link #parseDocumentTagAttributes(String)} to find and parse the {@code <doc/>} tag from either
 * form.
 *
 * <p><b>Site 2 — synthetic user message</b>: The extracted-documents user message uses the same
 * {@code <doc/>} tag format but also includes {@code toolName} and {@code toolCallId} attributes
 * for correlation. The tag is rendered with the same {@code id} as Site 1 so that the LLM can
 * correlate the tool result marker with the document content.
 */
public final class ToolCallResultDocumentAssertions {

  public static final String EXTRACTED_DOCUMENTS_PREAMBLE =
      "Documents extracted from tool calls (<doc /> tag + content pair):";

  /** Pattern that matches a {@code <doc ... />} self-closing XML tag. */
  private static final Pattern DOC_TAG_PATTERN = Pattern.compile("<doc[^>]*/>");

  /** Pattern that matches a named attribute in an XML tag: {@code name="value"}. */
  private static final Pattern ATTR_PATTERN = Pattern.compile("(\\w+)=\"([^\"]*?)\"");

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  private ToolCallResultDocumentAssertions() {}

  /**
   * Parses the Site-1 tool result content and returns the attributes of the first {@code <doc/>}
   * tag found.
   *
   * <p>Site-1 tool result content may be:
   *
   * <ul>
   *   <li>a JSON string containing the {@code <doc/>} tag directly (bare {@code Document} result),
   *       e.g. {@code "\"<doc id=\\\"...\\\" />\""}
   *   <li>a JSON object where one of the string values contains a {@code <doc/>} tag (e.g. an HTTP
   *       connector returning {@code {"status":200,"document":"<doc id=\\\"...\\\" />"}})
   * </ul>
   *
   * <p>Throws {@link AssertionError} if no {@code <doc/>} tag is found.
   */
  public static Map<String, String> parseDocumentTagAttributes(String toolResultContent) {
    final String tag = findDocTag(toolResultContent);
    final Map<String, String> attributes = new LinkedHashMap<>();
    final Matcher attrMatcher = ATTR_PATTERN.matcher(tag);
    while (attrMatcher.find()) {
      attributes.put(attrMatcher.group(1), attrMatcher.group(2));
    }
    return attributes;
  }

  /**
   * Extracts the raw {@code <doc/>} XML tag string from a Site-1 tool result content. Handles both
   * bare JSON string content and JSON object content where the tag is nested in a string field.
   * Throws {@link AssertionError} if no {@code <doc/>} tag is found.
   */
  public static String parseDocumentReferenceTag(String toolResultContent) {
    return findDocTag(toolResultContent);
  }

  /**
   * Finds the first {@code <doc/>} tag anywhere in the tool result content, which may be a JSON
   * string or a JSON object containing string fields.
   */
  private static String findDocTag(String toolResultContent) {
    final JsonNode root;
    try {
      root = OBJECT_MAPPER.readTree(toolResultContent);
    } catch (JsonProcessingException e) {
      throw new AssertionError(
          "Failed to parse tool result content as JSON: " + toolResultContent, e);
    }

    final String tag = findDocTagInNode(root);
    if (tag == null) {
      throw new AssertionError("No <doc/> tag found in tool result content: " + toolResultContent);
    }
    return tag;
  }

  private static String findDocTagInNode(JsonNode node) {
    if (node == null) {
      return null;
    }
    if (node.isTextual()) {
      final Matcher m = DOC_TAG_PATTERN.matcher(node.asText());
      if (m.find()) {
        return m.group();
      }
    } else if (node.isObject()) {
      for (var entry : node.properties()) {
        final String found = findDocTagInNode(entry.getValue());
        if (found != null) {
          return found;
        }
      }
    } else if (node.isArray()) {
      for (var element : node) {
        final String found = findDocTagInNode(element);
        if (found != null) {
          return found;
        }
      }
    }
    return null;
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
     * Document extracted from a tool call result. Builds the expected Site-2 {@code <doc/>} tag
     * from the attributes parsed out of the Site-1 tag (id, fileName, contentType) combined with
     * the tool call attribution (toolCallId, toolName).
     *
     * @param toolCallId tool call id for Site-2 attribution
     * @param toolName tool name for Site-2 attribution
     * @param site1Attributes attributes map from {@link
     *     ToolCallResultDocumentAssertions#parseDocumentTagAttributes(String)}
     * @param contentBlockAssertion assertion on the OpenAI content block node
     */
    public static ExtractedDocument forToolCall(
        String toolCallId,
        String toolName,
        Map<String, String> site1Attributes,
        Consumer<JsonNode> contentBlockAssertion) {
      return new ExtractedDocument(
          new DocumentReferenceXmlTag(
                  site1Attributes.get("id"),
                  site1Attributes.get("fileName"),
                  site1Attributes.get("contentType"),
                  toolCallId,
                  toolName)
              .toXml(),
          contentBlockAssertion);
    }
  }
}
