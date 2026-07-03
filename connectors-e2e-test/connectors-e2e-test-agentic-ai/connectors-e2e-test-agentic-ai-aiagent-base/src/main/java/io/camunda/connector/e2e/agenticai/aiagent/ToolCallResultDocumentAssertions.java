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
import io.camunda.connector.agenticai.aiagent.model.message.DocumentReferenceXmlTag.CamundaDocumentReferenceXmlTag;
import io.camunda.connector.agenticai.aiagent.model.message.DocumentReferenceXmlTag.ExternalDocumentReferenceXmlTag;
import io.camunda.connector.document.jackson.DocumentReferenceModel;
import io.camunda.connector.document.jackson.DocumentReferenceModel.CamundaDocumentReferenceModel;
import io.camunda.connector.document.jackson.DocumentReferenceModel.ExternalDocumentReferenceModel;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.openai.OpenAiCompletionsRecordedConversation.RecordedMessage;
import java.util.List;
import java.util.function.Consumer;

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
 */
public final class ToolCallResultDocumentAssertions {

  public static final String EXTRACTED_DOCUMENTS_PREAMBLE =
      "Documents extracted from tool calls (<doc /> tag + content pair):";

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  private ToolCallResultDocumentAssertions() {}

  /**
   * Locates the first Camunda document reference inside a serialized tool call result and
   * deserializes it into the production {@link CamundaDocumentReferenceModel}. Recursively descends
   * into objects/arrays until it finds an object carrying the {@code camunda.document.type=camunda}
   * discriminator.
   *
   * <p>Throws {@link AssertionError} if the text cannot be parsed as JSON or no document reference
   * is present.
   */
  public static CamundaDocumentReferenceModel parseDocumentReference(String toolResultText) {
    return parseFirstDocumentNode(toolResultText, "camunda", CamundaDocumentReferenceModel.class);
  }

  /** Same as {@link #parseDocumentReference} but for external references. */
  public static ExternalDocumentReferenceModel parseExternalDocumentReference(
      String toolResultText) {
    return parseFirstDocumentNode(toolResultText, "external", ExternalDocumentReferenceModel.class);
  }

  private static <T> T parseFirstDocumentNode(
      String toolResultText, String discriminator, Class<T> referenceType) {
    final JsonNode root;
    try {
      root = OBJECT_MAPPER.readTree(toolResultText);
    } catch (JsonProcessingException e) {
      throw new AssertionError("Failed to parse tool result text as JSON: " + toolResultText, e);
    }

    final JsonNode docNode = findFirstDocumentNode(root, discriminator);
    if (docNode == null) {
      throw new AssertionError(
          "No '%s' document reference found in tool result text: %s"
              .formatted(discriminator, toolResultText));
    }

    try {
      return OBJECT_MAPPER.treeToValue(docNode, referenceType);
    } catch (JsonProcessingException e) {
      throw new AssertionError(
          "Failed to deserialize '%s' document reference: %s".formatted(discriminator, docNode), e);
    }
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

  private static JsonNode findFirstDocumentNode(JsonNode node, String discriminator) {
    if (node == null) {
      return null;
    }
    if (node.isObject()) {
      if (discriminator.equals(node.path(DocumentReferenceModel.DISCRIMINATOR_KEY).asText(null))) {
        return node;
      }
      for (var property : node.properties()) {
        final var found = findFirstDocumentNode(property.getValue(), discriminator);
        if (found != null) {
          return found;
        }
      }
    } else if (node.isArray()) {
      for (var element : node) {
        final var found = findFirstDocumentNode(element, discriminator);
        if (found != null) {
          return found;
        }
      }
    }
    return null;
  }

  /**
   * Specification of one expected extracted document slot in the synthetic user message: an XML tag
   * with optional tool call correlation attributes plus a content block check operating on the raw
   * OpenAI wire-format {@link JsonNode}.
   */
  public record ExtractedDocument(String expectedXmlTag, Consumer<JsonNode> contentBlockAssertion) {

    /** Camunda document extracted from a tool call result. */
    public static ExtractedDocument forToolCall(
        String toolCallId,
        String toolName,
        CamundaDocumentReferenceModel reference,
        Consumer<JsonNode> contentBlockAssertion) {
      final var metadata = reference.metadata();
      return new ExtractedDocument(
          new CamundaDocumentReferenceXmlTag(
                  toolCallId,
                  toolName,
                  reference.documentId(),
                  reference.storeId(),
                  metadata != null ? metadata.getContentType() : null,
                  metadata != null ? metadata.getFileName() : null)
              .toXml(),
          contentBlockAssertion);
    }

    /** External document extracted from a tool call result. */
    public static ExtractedDocument forExternalToolCall(
        String toolCallId,
        String toolName,
        ExternalDocumentReferenceModel reference,
        Consumer<JsonNode> contentBlockAssertion) {
      return new ExtractedDocument(
          new ExternalDocumentReferenceXmlTag(
                  toolCallId, toolName, reference.url(), reference.name())
              .toXml(),
          contentBlockAssertion);
    }
  }
}
