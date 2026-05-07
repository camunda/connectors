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
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.Content;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.PdfFileContent;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.UserMessage;
import io.camunda.connector.agenticai.model.message.DocumentXmlTag;
import io.camunda.connector.document.jackson.DocumentReferenceModel;
import io.camunda.connector.document.jackson.DocumentReferenceModel.CamundaDocumentReferenceModel;
import java.util.function.Consumer;

/**
 * Assertion helpers for the synthetic {@link UserMessage} that carries documents extracted from
 * tool call results.
 *
 * <p>The user message has the structure {@code preamble + (xml-tag, content-block)*}, so it can
 * carry documents from one or many tool call results. Use {@link
 * #assertExtractedDocumentsUserMessage(ChatMessage, ExtractedDocument...)} to assert against any
 * number of documents in one go.
 */
public final class ToolCallResultDocumentAssertions {

  static final String EXTRACTED_DOCUMENTS_PREAMBLE = "Documents extracted from tool call results:";

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  private ToolCallResultDocumentAssertions() {}

  /**
   * Locates the first Camunda document reference inside a serialized tool call result and
   * deserializes it into the production {@link CamundaDocumentReferenceModel}. Recursively descends
   * into objects/arrays until it finds an object carrying the {@code camunda.document.type}
   * discriminator (set by {@code DocumentSerializer}).
   *
   * <p>Throws {@link AssertionError} if the text cannot be parsed as JSON or no document reference
   * is present.
   */
  public static CamundaDocumentReferenceModel parseDocumentReference(String toolResultText) {
    final JsonNode root;
    try {
      root = OBJECT_MAPPER.readTree(toolResultText);
    } catch (JsonProcessingException e) {
      throw new AssertionError("Failed to parse tool result text as JSON: " + toolResultText, e);
    }

    final JsonNode docNode = findFirstCamundaDocumentNode(root);
    if (docNode == null) {
      throw new AssertionError(
          "No Camunda document reference found in tool result text: " + toolResultText);
    }

    try {
      return OBJECT_MAPPER.treeToValue(docNode, CamundaDocumentReferenceModel.class);
    } catch (JsonProcessingException e) {
      throw new AssertionError("Failed to deserialize Camunda document reference: " + docNode, e);
    }
  }

  /**
   * Asserts that {@code message} is a {@link UserMessage} with the standard "extracted documents"
   * structure: a preamble {@link TextContent} followed by an {@code (xml-tag, content-block)} pair
   * per expected document.
   *
   * <p>The XML tag is built using the production {@link DocumentXmlTag}, so the assertion stays in
   * sync with the production format.
   */
  public static void assertExtractedDocumentsUserMessage(
      ChatMessage message, ExtractedDocument... expectedDocuments) {
    assertThat(message).isInstanceOf(UserMessage.class);
    final var contents = ((UserMessage) message).contents();

    assertThat(contents)
        .as("expected preamble + 2 contents per document (%d documents)", expectedDocuments.length)
        .hasSize(1 + 2 * expectedDocuments.length);

    assertThat(contents.get(0))
        .as("preamble TextContent")
        .isInstanceOfSatisfying(
            TextContent.class, tc -> assertThat(tc.text()).isEqualTo(EXTRACTED_DOCUMENTS_PREAMBLE));

    for (int i = 0; i < expectedDocuments.length; i++) {
      final var expected = expectedDocuments[i];
      final var expectedXml = expected.expectedXmlTag();
      final var tagIndex = 1 + 2 * i;
      final var contentIndex = tagIndex + 1;

      assertThat(contents.get(tagIndex))
          .as("XML tag at index %d (document %d)", tagIndex, i)
          .isInstanceOfSatisfying(
              TextContent.class, tc -> assertThat(tc.text()).isEqualTo(expectedXml));

      final var contentBlock = contents.get(contentIndex);
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
   * Asserts a content block based on a coarse type/mime classification used by tool calling tests:
   *
   * <ul>
   *   <li>{@code "text"} → {@link TextContent}
   *   <li>{@code "application/pdf"} → {@link PdfFileContent} with non-blank base64
   *   <li>otherwise → {@link ImageContent} with matching mime type and non-blank base64
   * </ul>
   */
  public static void assertDocumentContentBlock(
      Content content, String expectedType, String expectedMimeType) {
    if ("text".equals(expectedType)) {
      assertThat(content).isInstanceOf(TextContent.class);
    } else if ("application/pdf".equals(expectedMimeType)) {
      assertThat(content)
          .isInstanceOfSatisfying(
              PdfFileContent.class, pdf -> assertThat(pdf.pdfFile().base64Data()).isNotBlank());
    } else {
      assertThat(content)
          .isInstanceOfSatisfying(
              ImageContent.class,
              img -> {
                assertThat(img.image().mimeType()).isEqualTo(expectedMimeType);
                assertThat(img.image().base64Data()).isNotBlank();
              });
    }
  }

  private static JsonNode findFirstCamundaDocumentNode(JsonNode node) {
    if (node == null) {
      return null;
    }
    if (node.isObject()) {
      if ("camunda".equals(node.path(DocumentReferenceModel.DISCRIMINATOR_KEY).asText(null))) {
        return node;
      }
      final var properties = node.properties();
      for (var property : properties) {
        final var found = findFirstCamundaDocumentNode(property.getValue());
        if (found != null) {
          return found;
        }
      }
    } else if (node.isArray()) {
      for (var element : node) {
        final var found = findFirstCamundaDocumentNode(element);
        if (found != null) {
          return found;
        }
      }
    }
    return null;
  }

  /**
   * Specification of one expected extracted document slot in the synthetic user message: an XML tag
   * with optional tool call correlation attributes plus a content block check.
   */
  public record ExtractedDocument(
      String toolCallId,
      String toolName,
      CamundaDocumentReferenceModel reference,
      Consumer<Content> contentBlockAssertion) {

    /** Document extracted from a tool call result. */
    public static ExtractedDocument forToolCall(
        String toolCallId,
        String toolName,
        CamundaDocumentReferenceModel reference,
        Consumer<Content> contentBlockAssertion) {
      return new ExtractedDocument(toolCallId, toolName, reference, contentBlockAssertion);
    }

    /** The XML tag string this document is expected to render to in the synthetic user message. */
    String expectedXmlTag() {
      return new DocumentXmlTag(
              toolCallId,
              toolName,
              documentShortId(),
              reference.metadata() != null ? reference.metadata().fileName() : null)
          .toXml();
    }

    private String documentShortId() {
      final var documentId = reference.documentId();
      final int dash = documentId.indexOf('-');
      return dash > 0 ? documentId.substring(0, dash) : documentId;
    }
  }
}
