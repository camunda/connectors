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
import io.camunda.connector.agenticai.aiagent.document.ObjectHandleRenderer;
import io.camunda.connector.agenticai.aiagent.model.document.AgentDocument;
import io.camunda.connector.agenticai.aiagent.model.document.AgentDocumentHandle;
import io.camunda.connector.agenticai.aiagent.model.document.AgentDocumentId;
import io.camunda.connector.agenticai.aiagent.model.document.AgentDocuments;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.openai.OpenAiCompletionsRecordedConversation.RecordedMessage;
import java.util.HashSet;
import java.util.List;
import java.util.function.Consumer;
import org.jspecify.annotations.Nullable;

/**
 * Assertion helpers for the user message that carries the documents the model loaded with {@code
 * c8__load_document}, and for the document handles of the other messages.
 *
 * <p>The user message has the structure {@code preamble + (handle, content-block)*}, so it can
 * carry one or many loaded documents. Use {@link #assertDocumentContentUserMessage(RecordedMessage,
 * DocumentWithContent...)} to assert against any number of documents in one go.
 *
 * <p>All assertions operate on OpenAI-compatible wire-format {@link JsonNode} objects recorded by
 * WireMock — no framework-specific types required.
 */
public final class ToolCallResultDocumentAssertions {

  /**
   * The preamble of the tag and content pairs of an agent instance without the documents feature.
   */
  public static final String EXTRACTED_DOCUMENTS_PREAMBLE =
      "Documents extracted from tool calls (<doc /> tag + content pair):";

  public static final String DOCUMENT_CONTENT_PREAMBLE =
      "Document content from tool calls (added by the system):";

  /** The file name and the id of a document as the sentences about its content name it. */
  private static String refOf(ParsedDocumentHandle handle) {
    return handle.fileName() == null
        ? handle.id().value()
        : "%s (%s)".formatted(handle.fileName(), handle.id().value());
  }

  /** What the agent writes into the tool result when the content follows in the next message. */
  public static String contentFollowsSentence(ParsedDocumentHandle handle) {
    return "The content of %s follows in the next message.".formatted(refOf(handle));
  }

  /** What the agent writes into the tool result when the content is in the conversation. */
  public static String contentAlreadyInConversationSentence(ParsedDocumentHandle handle) {
    return "The content of %s is already in the conversation.".formatted(refOf(handle));
  }

  private static final String HANDLE_START = "{\"type\":\"" + AgentDocumentHandle.TYPE + "\"";

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  private static final ObjectHandleRenderer HANDLE_RENDERER =
      new ObjectHandleRenderer(OBJECT_MAPPER);

  private ToolCallResultDocumentAssertions() {}

  /**
   * Asserts that the handle the model saw is the whole handle of the one document the agent
   * registered: the agent documents hold exactly this document, the handle built from it is the
   * expected one (every field), and the handle the model read is this handle as compact JSON, so
   * that no store id or content hash reaches the model. The id is not a parameter: it is the hash
   * of the identity of the document and comes from the registered document.
   *
   * @param fileName the expected file name, or {@code null} if the handle has none
   * @param size the expected size, or {@code null} if the handle has none: the document store of
   *     the e2e tests keeps no size in the metadata of a document
   * @return the registered agent document, for assertions on its origin and reference
   */
  public static AgentDocument assertHandleOfTheOnlyRegisteredDocument(
      ParsedDocumentHandle seen,
      AgentDocuments documents,
      @Nullable String fileName,
      String contentType,
      @Nullable Long size) {
    assertThat(documents.all()).as("the registered agent documents").hasSize(1);
    final var registered = documents.all().getFirst();
    final var expected =
        new AgentDocumentHandle(
            AgentDocumentHandle.TYPE, registered.id().value(), fileName, contentType, size);
    assertThat(AgentDocumentHandle.of(registered))
        .as("the handle of the registered document")
        .isEqualTo(expected);
    assertThat(seen.text())
        .as("the handle the model read")
        .isEqualTo(HANDLE_RENDERER.text(expected));
    return registered;
  }

  /**
   * Parses the first document handle out of a serialized tool call result (a bare handle, a handle
   * followed by a sentence or a JSON value embedding one) into a {@link ParsedDocumentHandle}. Also
   * asserts that the text does not carry any document reference field.
   *
   * <p>Throws {@link AssertionError} if no handle is present.
   */
  public static ParsedDocumentHandle parseDocumentHandle(String toolResultText) {
    assertThat(toolResultText)
        .as("tool result text")
        .doesNotContain("storeId", "contentHash", "camunda.document.type");

    final var start = toolResultText.indexOf(HANDLE_START);
    if (start < 0) {
      throw new AssertionError("No document handle found in tool result text: " + toolResultText);
    }
    final JsonNode node;
    try {
      // reads the first JSON value and ignores what follows it
      node = OBJECT_MAPPER.readTree(toolResultText.substring(start));
    } catch (JsonProcessingException e) {
      throw new AssertionError("Unparseable document handle in: " + toolResultText, e);
    }

    final var fieldNames = new HashSet<String>();
    node.fieldNames().forEachRemaining(fieldNames::add);
    assertThat(fieldNames)
        .as("handle fields")
        .isSubsetOf("type", "id", "fileName", "contentType", "size", "note")
        .contains("type", "id");
    return new ParsedDocumentHandle(
        AgentDocumentId.of(node.path("id").asText()),
        textOrNull(node, "fileName"),
        textOrNull(node, "contentType"),
        node.has("size") ? node.path("size").asLong() : null,
        textOrNull(node, "note"),
        node.toString());
  }

  private static @Nullable String textOrNull(JsonNode node, String field) {
    return node.has(field) ? node.path(field).asText() : null;
  }

  /**
   * Asserts that {@code message} is a wire-format user message (OpenAI-compatible JSON) with the
   * "loaded documents" structure: the preamble text block followed by a {@code (handle,
   * content-block)} pair per expected document.
   *
   * <p>The handle is the one the earlier message showed. The content-block {@link JsonNode} is
   * passed directly to each {@link DocumentWithContent#contentBlockAssertion()}.
   */
  public static void assertDocumentContentUserMessage(
      RecordedMessage message, DocumentWithContent... expectedDocuments) {
    assertThat(message.role()).as("message role").isEqualTo("user");
    final List<JsonNode> contentParts = message.contentParts();
    assertThat(contentParts).as("content should be an array").isNotEmpty();
    assertThat(contentParts.size())
        .as("expected preamble + 2 contents per document (%d documents)", expectedDocuments.length)
        .isEqualTo(1 + 2 * expectedDocuments.length);

    assertThat(contentParts.get(0).path("type").asText()).as("preamble type").isEqualTo("text");
    assertThat(contentParts.get(0).path("text").asText())
        .as("preamble text")
        .isEqualTo(DOCUMENT_CONTENT_PREAMBLE);

    for (int i = 0; i < expectedDocuments.length; i++) {
      final var expected = expectedDocuments[i];
      final var handleIndex = 1 + 2 * i;
      final var contentIndex = handleIndex + 1;

      assertThat(contentParts.get(handleIndex).path("type").asText())
          .as("handle type at index %d", handleIndex)
          .isEqualTo("text");
      assertThat(contentParts.get(handleIndex).path("text").asText())
          .as("handle text at index %d", handleIndex)
          .isEqualTo(expected.expectedHandle());

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
   * Asserts that a wire-format content part (OpenAI JSON) is a text part holding the handle of a
   * user prompt document that exposes the document id and its file information only: no store id,
   * content hash, URL or origin.
   */
  public static void assertDocumentHandlePart(JsonNode part) {
    assertThat(part.path("type").asText()).as("document handle part type").isEqualTo("text");
    assertThat(part.path("text").asText())
        .as("document handle text")
        .startsWith(HANDLE_START + ",\"id\":\"doc_")
        .endsWith("}")
        .doesNotContain("storeId", "contentHash", "documentId", "http", "source", "toolName");
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
   * The type of the content block that carries the content of the test file ({@code test.<ext>} of
   * the served files): {@code text} for text formats, {@code base64} for images and PDF.
   */
  public static String contentBlockType(String filename) {
    return switch (filename) {
      case "test.txt", "test.yaml", "test.csv", "test.json", "test.xml" -> "text";
      default -> "base64";
    };
  }

  /** The content type of the test file ({@code test.<ext>} of the served files). */
  public static String mimeType(String filename) {
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

  /**
   * Specification of one expected loaded document slot in the user message: the handle the earlier
   * message showed plus a content block check operating on the raw OpenAI wire-format {@link
   * JsonNode}.
   */
  public record DocumentWithContent(
      String expectedHandle, Consumer<JsonNode> contentBlockAssertion) {

    /** The document with the given handle, as an earlier message showed it. */
    public static DocumentWithContent of(
        ParsedDocumentHandle handle, Consumer<JsonNode> contentBlockAssertion) {
      return new DocumentWithContent(handle.text(), contentBlockAssertion);
    }
  }

  /**
   * The fields of a document handle the model saw.
   *
   * @param id the id of the document
   * @param fileName the file name, or {@code null} if the handle has none
   * @param contentType the content type, or {@code null} if the handle has none
   * @param size the size, or {@code null} if the handle has none
   * @param note the note, or {@code null} if the handle has none
   * @param text the handle as it was rendered
   */
  public record ParsedDocumentHandle(
      AgentDocumentId id,
      @Nullable String fileName,
      @Nullable String contentType,
      @Nullable Long size,
      @Nullable String note,
      String text) {}
}
