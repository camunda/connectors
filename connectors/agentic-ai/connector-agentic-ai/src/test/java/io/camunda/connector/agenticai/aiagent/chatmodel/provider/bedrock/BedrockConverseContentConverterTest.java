/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.chatmodel.provider.bedrock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.agenticai.aiagent.model.message.content.Content;
import io.camunda.connector.agenticai.aiagent.model.message.content.DocumentContent;
import io.camunda.connector.agenticai.aiagent.model.message.content.ObjectContent;
import io.camunda.connector.agenticai.aiagent.model.message.content.ProviderContent;
import io.camunda.connector.agenticai.aiagent.model.message.content.ReasoningContent;
import io.camunda.connector.agenticai.aiagent.model.message.content.TextContent;
import io.camunda.connector.agenticai.testutil.TestObjectMapperSupplier;
import io.camunda.connector.api.document.Document;
import io.camunda.connector.api.document.DocumentCreationRequest;
import io.camunda.connector.api.error.ConnectorException;
import io.camunda.connector.runtime.core.document.DocumentFactoryImpl;
import io.camunda.connector.runtime.core.document.InlineDocument;
import io.camunda.connector.runtime.core.document.store.InMemoryDocumentStore;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import software.amazon.awssdk.services.bedrockruntime.model.CachePointBlock;
import software.amazon.awssdk.services.bedrockruntime.model.CachePointType;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.DocumentFormat;
import software.amazon.awssdk.services.bedrockruntime.model.ImageFormat;

class BedrockConverseContentConverterTest {

  private final ObjectMapper objectMapper = TestObjectMapperSupplier.INSTANCE;
  private final BedrockConverseContentConverter converter =
      new BedrockConverseContentConverter(objectMapper);

  private static Document inlineDocument(String content, String name, String contentType) {
    return new InlineDocument(content, name, contentType);
  }

  /**
   * Asserts the given JSON value is the standard serialized reference of an inline document - the
   * exact shape the runtime's {@code DocumentSerializer} writes, and therefore the shape the {@code
   * <doc/>} tag of the composer's synthetic message correlates against.
   */
  private static void assertInlineDocumentReference(
      software.amazon.awssdk.core.document.Document json,
      String content,
      String fileName,
      String contentType) {
    assertThat(json.isMap()).isTrue();
    assertThat(json.asMap())
        .extractingByKeys("camunda.document.type", "content", "name", "contentType")
        .extracting(software.amazon.awssdk.core.document.Document::asString)
        .containsExactly("inline", content, fileName, contentType);
  }

  @Nested
  class ToContentBlocks {

    @Test
    void mapsTextContentToTextBlock() {
      final var blocks = converter.toContentBlocks(List.of(new TextContent("hello world", null)));

      assertThat(blocks).hasSize(1);
      assertThat(blocks.get(0).text()).isEqualTo("hello world");
    }

    @Test
    void mapsImageDocumentToImageBlock() {
      final var doc = inlineDocument("fake-png-bytes", "photo.png", "image/png");

      final var blocks = converter.toContentBlocks(List.of(new DocumentContent(doc, null)));

      assertThat(blocks).hasSize(1);
      final var image = blocks.get(0).image();
      assertThat(image).isNotNull();
      assertThat(image.format()).isEqualTo(ImageFormat.PNG);
      assertThat(image.source().bytes().asByteArray())
          .isEqualTo("fake-png-bytes".getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void mapsNativeDocumentFormatToDocumentBlockWithBytesSource() {
      final var doc = inlineDocument("fake-pdf-bytes", "report.pdf", "application/pdf");

      final var blocks = converter.toContentBlocks(List.of(new DocumentContent(doc, null)));

      assertThat(blocks).hasSize(1);
      final var document = blocks.get(0).document();
      assertThat(document).isNotNull();
      assertThat(document.format()).isEqualTo(DocumentFormat.PDF);
      assertThat(document.source().bytes().asByteArray())
          .isEqualTo("fake-pdf-bytes".getBytes(StandardCharsets.UTF_8));
      assertThat(document.name()).matches("doc-[0-9a-f]{40}");
    }

    @Test
    void mapsAnotherNativeDocumentFormatToDocumentBlock() {
      final var doc =
          inlineDocument(
              "fake-docx-bytes",
              "report.docx",
              "application/vnd.openxmlformats-officedocument.wordprocessingml.document");

      final var blocks = converter.toContentBlocks(List.of(new DocumentContent(doc, null)));

      assertThat(blocks).hasSize(1);
      assertThat(blocks.get(0).document().format()).isEqualTo(DocumentFormat.DOCX);
    }

    @Test
    void mapsTextIshDocumentToTxtFormatDocumentBlockWithTextSource() {
      final var doc = inlineDocument("{\"key\":\"value\"}", "data.json", "application/json");

      final var blocks = converter.toContentBlocks(List.of(new DocumentContent(doc, null)));

      assertThat(blocks).hasSize(1);
      final var document = blocks.get(0).document();
      assertThat(document).isNotNull();
      assertThat(document.format()).isEqualTo(DocumentFormat.TXT);
      assertThat(document.source().text()).isEqualTo("{\"key\":\"value\"}");
      assertThat(document.name()).matches("doc-[0-9a-f]{40}");
    }

    @Test
    void documentBlockNameIsDeterministicHashOfTheHandleId() {
      final var doc = inlineDocument("plain text content", "notes.txt", "text/plain");

      final var name1 =
          converter
              .toContentBlocks(List.of(new DocumentContent(doc, null)))
              .get(0)
              .document()
              .name();
      final var name2 =
          converter
              .toContentBlocks(List.of(new DocumentContent(doc, null)))
              .get(0)
              .document()
              .name();

      assertThat(name1).matches("[A-Za-z0-9 ()\\[\\]-]{1,200}").isEqualTo(name2);
    }

    @Test
    void documentBlockNameDiffersForDifferentDocuments() {
      final var docA = inlineDocument("content A", "a.txt", "text/plain");
      final var docB = inlineDocument("content B", "b.txt", "text/plain");

      final var nameA =
          converter
              .toContentBlocks(List.of(new DocumentContent(docA, null)))
              .get(0)
              .document()
              .name();
      final var nameB =
          converter
              .toContentBlocks(List.of(new DocumentContent(docB, null)))
              .get(0)
              .document()
              .name();

      assertThat(nameA).isNotEqualTo(nameB);
    }

    @Test
    void documentBlockNameStaysWithinCharsetEvenForADisallowedCamundaDocumentId() {
      // A CamundaDocumentReference's real documentId is carried verbatim by DocumentHandle - other
      // code cross-references documents by it - and DocumentCreationRequest.documentId(...)
      // accepts arbitrary strings, so it isn't guaranteed to fit Bedrock's charset on its own.
      final var documentFactory = new DocumentFactoryImpl(InMemoryDocumentStore.INSTANCE);
      final var doc =
          documentFactory.create(
              DocumentCreationRequest.from("hello".getBytes(StandardCharsets.UTF_8))
                  .documentId("custom:doc_id/with spaces")
                  .contentType("text/plain")
                  .fileName("hello.txt")
                  .build());

      final var blocks = converter.toContentBlocks(List.of(new DocumentContent(doc, null)));

      assertThat(blocks.get(0).document().name()).matches("doc-[0-9a-f]{40}");
    }

    @Test
    void mapsObjectContentToJsonTextBlock() {
      final var blocks =
          converter.toContentBlocks(List.of(new ObjectContent(Map.of("key", "value"), null)));

      assertThat(blocks).hasSize(1);
      assertThat(blocks.get(0).text()).isEqualTo("{\"key\":\"value\"}");
    }

    @Test
    void throwsForUnsupportedDocumentContentType() {
      final var doc = inlineDocument("zip-bytes", "archive.zip", "application/zip");

      assertThatThrownBy(() -> converter.toContentBlocks(List.of(new DocumentContent(doc, null))))
          .isInstanceOf(ConnectorException.class)
          .hasFieldOrPropertyWithValue("errorCode", "FAILED_MODEL_CALL")
          .hasMessageContaining("application/zip");
    }

    @Test
    void mapsReasoningContentWithTextMergedBackIntoPayload() {
      final var payload = Map.<String, Object>of("reasoningText", Map.of("signature", "sig-123"));

      final var blocks =
          converter.toContentBlocks(
              List.of(
                  new ReasoningContent("bedrock", payload, "Let me think it through", Map.of())));

      assertThat(blocks).hasSize(1);
      final var reasoningContent = blocks.get(0).reasoningContent();
      assertThat(reasoningContent).isNotNull();
      assertThat(reasoningContent.reasoningText().text()).isEqualTo("Let me think it through");
      assertThat(reasoningContent.reasoningText().signature()).isEqualTo("sig-123");
    }

    @Test
    void mapsReasoningContentWithRedactedContentAndNoText() {
      final var redacted =
          java.util.Base64.getEncoder()
              .encodeToString("encrypted-blob".getBytes(StandardCharsets.UTF_8));
      final var payload = Map.<String, Object>of("redactedContent", redacted);

      final var blocks =
          converter.toContentBlocks(List.of(new ReasoningContent("bedrock", payload, null, null)));

      assertThat(blocks).hasSize(1);
      final var reasoningContent = blocks.get(0).reasoningContent();
      assertThat(reasoningContent).isNotNull();
      assertThat(reasoningContent.redactedContent().asUtf8String()).isEqualTo("encrypted-blob");
    }

    @Test
    void throwsWhenReasoningContentPayloadIsNotAMap() {
      assertThatThrownBy(
              () ->
                  converter.toContentBlocks(
                      List.of(new ReasoningContent("bedrock", "not-a-map", null, null))))
          .isInstanceOf(ConnectorException.class)
          .hasFieldOrPropertyWithValue("errorCode", "FAILED_MODEL_CALL");
    }

    @Test
    void mapsProviderContentPayloadToNativeBlockRoundTrip() {
      // Any ContentBlock member beyond text/toolUse/reasoningContent round-trips byte-identically
      // through ProviderContent via the generic codec; cachePoint stands in for the mechanism.
      final var original =
          ContentBlock.fromCachePoint(
              CachePointBlock.builder().type(CachePointType.DEFAULT).build());
      final var payload = BedrockConverseSdkPojoCodec.capture(original);

      final var blocks =
          converter.toContentBlocks(List.of(new ProviderContent("bedrock", payload, null)));

      assertThat(blocks).hasSize(1);
      assertThat(blocks.get(0)).isEqualTo(original);
    }

    @Test
    void throwsWhenProviderContentPayloadIsNotAMap() {
      assertThatThrownBy(
              () ->
                  converter.toContentBlocks(
                      List.of(new ProviderContent("bedrock", "not-a-map", null))))
          .isInstanceOf(ConnectorException.class)
          .hasFieldOrPropertyWithValue("errorCode", "FAILED_MODEL_CALL");
    }

    @Test
    void dropsReasoningContentFromAForeignProvider() {
      final var payload = Map.<String, Object>of("type", "thinking", "id", "rs_1");

      final var blocks =
          converter.toContentBlocks(List.of(new ReasoningContent("openai", payload, null, null)));

      assertThat(blocks).isEmpty();
    }

    @Test
    void dropsProviderContentFromAForeignProvider() {
      final var payload = Map.<String, Object>of("type", "container_upload", "id", "file_1");

      final var blocks =
          converter.toContentBlocks(List.of(new ProviderContent("anthropic", payload, null)));

      assertThat(blocks).isEmpty();
    }

    @Test
    void mapsMultipleContentItemsInOrder() {
      final var doc = inlineDocument("fake-png-bytes", "photo.png", "image/png");
      final List<Content> content =
          List.of(new TextContent("first", null), new DocumentContent(doc, null));

      final var blocks = converter.toContentBlocks(content);

      assertThat(blocks).hasSize(2);
      assertThat(blocks.get(0).text()).isEqualTo("first");
      assertThat(blocks.get(1).image()).isNotNull();
    }
  }

  @Nested
  class ToToolResultBlocks {

    @Test
    void mapsTextContentToTextBlock() {
      final var blocks = converter.toToolResultBlocks(List.of(new TextContent("hello", null)));

      assertThat(blocks).hasSize(1);
      assertThat(blocks.get(0).text()).isEqualTo("hello");
    }

    @ParameterizedTest
    @CsvSource({
      "photo.jpg, image/jpeg",
      "report.pdf, application/pdf",
      "notes.txt, text/plain",
      "archive.zip, application/zip"
    })
    void mapsDocumentToJsonReferenceRegardlessOfContentType(String fileName, String contentType) {
      // The composer echoes every tool-result document in a separate synthetic user message, which
      // is where its real bytes are delivered; embedding it here as well would send it twice and
      // trip Converse's duplicate-document-name validation. Content type is therefore irrelevant
      // here - even one with no native block shape at all just becomes a reference.
      final var doc = inlineDocument("fake-bytes", fileName, contentType);

      final var blocks = converter.toToolResultBlocks(List.of(new DocumentContent(doc, null)));

      assertThat(blocks).hasSize(1);
      assertThat(blocks.get(0).image()).isNull();
      assertThat(blocks.get(0).document()).isNull();
      final var json = blocks.get(0).json();
      assertThat(json).isNotNull();
      assertInlineDocumentReference(json, "fake-bytes", fileName, contentType);
    }

    @Test
    void mapsObjectContentToNativeJsonBlock() {
      final var blocks =
          converter.toToolResultBlocks(List.of(new ObjectContent(Map.of("a", 1), null)));

      assertThat(blocks).hasSize(1);
      final var json = blocks.get(0).json();
      assertThat(json).isNotNull();
      assertThat(json.isMap()).isTrue();
      assertThat(json.asMap().get("a").asNumber().intValue()).isEqualTo(1);
    }

    @Test
    void mapsNestedObjectContentToNativeJsonBlock() {
      final var blocks =
          converter.toToolResultBlocks(
              List.of(
                  new ObjectContent(
                      Map.of("items", List.of("a", "b"), "count", 2, "ok", true), null)));

      assertThat(blocks).hasSize(1);
      final var json = blocks.get(0).json();
      assertThat(json).isNotNull();
      assertThat(json.asMap().get("items").asList())
          .extracting(software.amazon.awssdk.core.document.Document::asString)
          .containsExactly("a", "b");
      assertThat(json.asMap().get("count").asNumber().intValue()).isEqualTo(2);
      assertThat(json.asMap().get("ok").asBoolean()).isTrue();
    }

    @Test
    void mapsObjectContentWithEmbeddedDocumentToJsonBlockWithSerializedReference() {
      // A tool result whose value isn't a plain string is lifted into a single ObjectContent
      // wrapping the raw tree verbatim (ToolCallResultContent#contentFromObject) - documents
      // embedded inside it (e.g. a FEEL-composed {attachments: [doc]}) are never split out
      // beforehand, so they arrive here as raw Document instances nested in the tree. The actual
      // document content reaches the model separately, via the provider-agnostic synthetic
      // document-echo message (AgentConversationTurnInputComposerImpl) - inlining it here as a
      // native document block too would send it twice and trip Bedrock's "duplicate document
      // names" validation, so this converter only ever emits the serialized reference.
      final var doc = inlineDocument("fake-pdf-bytes", "report.pdf", "application/pdf");

      final var blocks =
          converter.toToolResultBlocks(
              List.of(new ObjectContent(Map.of("attachments", List.of(doc)), null)));

      assertThat(blocks).hasSize(1);
      final var json = blocks.get(0).json();
      assertThat(json).isNotNull();
      final var attachments = json.asMap().get("attachments").asList();
      assertThat(attachments).hasSize(1);
      assertInlineDocumentReference(
          attachments.get(0), "fake-pdf-bytes", "report.pdf", "application/pdf");
    }

    @Test
    void
        mapsObjectContentWithMultipleNestedEmbeddedDocumentsToSerializedReferencesWithoutRecursing() {
      final var cover = inlineDocument("fake-png-bytes", "cover.png", "image/png");
      final var report = inlineDocument("fake-pdf-bytes", "report.pdf", "application/pdf");

      final var blocks =
          converter.toToolResultBlocks(
              List.of(
                  new ObjectContent(
                      Map.of(
                          "attachments", List.of(report),
                          "metadata", Map.of("cover", cover)),
                      null)));

      assertThat(blocks).hasSize(1);
      final var json = blocks.get(0).json();
      assertThat(json).isNotNull();
      assertInlineDocumentReference(
          json.asMap().get("attachments").asList().get(0),
          "fake-pdf-bytes",
          "report.pdf",
          "application/pdf");
      assertInlineDocumentReference(
          json.asMap().get("metadata").asMap().get("cover"),
          "fake-png-bytes",
          "cover.png",
          "image/png");
    }

    @Test
    void mapsListObjectContentToTextBlockSinceBedrockRejectsTopLevelJsonArrays() {
      // Bedrock's ToolResultContentBlock.json advertises "any JSON value" but its runtime
      // validation actually rejects a top-level array with "Provide a json object for the
      // field" - a real REST-style tool result (e.g. a list endpoint) is exactly this shape.
      final var blocks =
          converter.toToolResultBlocks(List.of(new ObjectContent(List.of("a", "b", "c"), null)));

      assertThat(blocks).hasSize(1);
      assertThat(blocks.get(0).json()).isNull();
      assertThat(blocks.get(0).text()).isEqualTo("[\"a\",\"b\",\"c\"]");
    }

    @Test
    void mapsScalarObjectContentToTextBlockSinceBedrockRejectsTopLevelJsonScalars() {
      final var blocks = converter.toToolResultBlocks(List.of(new ObjectContent(42, null)));

      assertThat(blocks).hasSize(1);
      assertThat(blocks.get(0).json()).isNull();
      assertThat(blocks.get(0).text()).isEqualTo("42");
    }

    @Test
    void throwsForReasoningContent() {
      assertThatThrownBy(
              () ->
                  converter.toToolResultBlocks(
                      List.of(
                          new ReasoningContent(
                              "bedrock", Map.of("reasoningText", Map.of()), null, null))))
          .isInstanceOf(ConnectorException.class)
          .hasFieldOrPropertyWithValue("errorCode", "FAILED_MODEL_CALL");
    }

    @Test
    void throwsForProviderContent() {
      assertThatThrownBy(
              () ->
                  converter.toToolResultBlocks(
                      List.of(
                          new ProviderContent("bedrock", Map.of("cachePoint", Map.of()), null))))
          .isInstanceOf(ConnectorException.class)
          .hasFieldOrPropertyWithValue("errorCode", "FAILED_MODEL_CALL");
    }
  }
}
