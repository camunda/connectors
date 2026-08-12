/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.chatmodel.provider.openai;

import static io.camunda.connector.agenticai.aiagent.agent.AgentErrorCodes.ERROR_CODE_FAILED_MODEL_CALL;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openai.models.chat.completions.ChatCompletionContentPart;
import com.openai.models.chat.completions.ChatCompletionContentPartImage;
import com.openai.models.chat.completions.ChatCompletionContentPartText;
import com.openai.models.responses.ResponseFunctionCallOutputItem;
import com.openai.models.responses.ResponseInputContent;
import com.openai.models.responses.ResponseInputFile;
import com.openai.models.responses.ResponseInputImage;
import com.openai.models.responses.ResponseInputText;
import com.openai.models.responses.ResponseInputTextContent;
import com.openai.models.responses.ResponseOutputMessage;
import com.openai.models.responses.ResponseOutputText;
import io.camunda.connector.agenticai.aiagent.model.message.content.Content;
import io.camunda.connector.agenticai.aiagent.model.message.content.DocumentContent;
import io.camunda.connector.agenticai.aiagent.model.message.content.ObjectContent;
import io.camunda.connector.agenticai.aiagent.model.message.content.TextContent;
import io.camunda.connector.api.document.Document;
import io.camunda.connector.api.error.ConnectorException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.apache.hc.core5.http.ContentType;

/**
 * Converts the domain {@link Content} model to OpenAI SDK content parts, for both the Responses API
 * ({@link ResponseInputContent}) and the Chat Completions API ({@link ChatCompletionContentPart})
 * families. Used for user/assistant message bodies as well as Responses tool-result bodies ({@link
 * ResponseFunctionCallOutputItem}).
 */
public class OpenAiContentConverter {

  private static final String DEFAULT_FILE_NAME = "document";

  private static final List<ContentType> PDF_CONTENT_TYPES = List.of(ContentType.APPLICATION_PDF);

  private static final List<ContentType> IMAGE_CONTENT_TYPES =
      List.of(
          ContentType.IMAGE_JPEG,
          ContentType.IMAGE_PNG,
          ContentType.IMAGE_GIF,
          ContentType.IMAGE_WEBP);

  private static final List<ContentType> ADDITIONAL_TEXT_FILE_CONTENT_TYPES =
      List.of(
          ContentType.APPLICATION_JSON,
          ContentType.APPLICATION_XML,
          ContentType.create("application/yaml"));

  private final ObjectMapper objectMapper;

  public OpenAiContentConverter(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  public List<ResponseInputContent> toResponsesContentParts(List<Content> content) {
    final List<ResponseInputContent> parts = new ArrayList<>();
    for (final Content c : content) {
      switch (c) {
        case TextContent text ->
            parts.add(
                ResponseInputContent.ofInputText(
                    ResponseInputText.builder().text(text.text()).build()));
        case DocumentContent doc -> parts.add(responsesDocumentPart(doc));
        case ObjectContent obj ->
            parts.add(
                ResponseInputContent.ofInputText(
                    ResponseInputText.builder().text(writeAsJson(obj.content())).build()));
        // Reasoning/provider content have no wire representation in this converter (their replay
        // is handled by the request converters); fall back to a JSON reference so the switch
        // stays exhaustive without crashing on unsupported content in a message/tool-result body.
        default ->
            parts.add(
                ResponseInputContent.ofInputText(
                    ResponseInputText.builder().text(writeAsJson(c)).build()));
      }
    }
    return parts;
  }

  /**
   * Converts a domain assistant message's plain content into Responses {@code output_text} parts,
   * for replaying it back as an assistant {@code message} input item. Unlike {@link
   * #toResponsesContentParts}, whose {@code input_text}/{@code input_image}/{@code input_file}
   * parts are only valid for user-authored content, the API rejects those types on an assistant
   * message -- assistant content must be {@code output_text} or {@code refusal}.
   */
  public List<ResponseOutputMessage.Content> toResponsesOutputContentParts(List<Content> content) {
    final List<ResponseOutputMessage.Content> parts = new ArrayList<>();
    for (final Content c : content) {
      switch (c) {
        case TextContent text -> parts.add(outputTextPart(text.text()));
        case ObjectContent obj -> parts.add(outputTextPart(writeAsJson(obj.content())));
        // Documents/reasoning/provider content have no wire representation as assistant output
        // text; fall back to a JSON reference so the switch stays exhaustive without crashing.
        default -> parts.add(outputTextPart(writeAsJson(c)));
      }
    }
    return parts;
  }

  private ResponseOutputMessage.Content outputTextPart(String text) {
    return ResponseOutputMessage.Content.ofOutputText(
        ResponseOutputText.builder().text(text).annotations(List.of()).build());
  }

  /**
   * Converts a tool result's structured content into Responses {@code function_call_output} items.
   * Unlike {@link #toResponsesContentParts}, a document here is flattened to a JSON reference
   * rather than emitted natively as {@code input_image}/{@code input_file}: the composer's
   * synthetic {@code <doc/>} fallback message already delivers the actual document bytes for tool
   * results (see {@code AgentConversationTurnInputComposerImpl}), so embedding it here as well
   * would send it to the model twice.
   */
  public List<ResponseFunctionCallOutputItem> toToolResultOutputItems(List<Content> content) {
    final List<ResponseFunctionCallOutputItem> items = new ArrayList<>();
    for (final Content c : content) {
      switch (c) {
        case TextContent text ->
            items.add(
                ResponseFunctionCallOutputItem.ofInputText(
                    ResponseInputTextContent.builder().text(text.text()).build()));
        case DocumentContent doc ->
            items.add(
                ResponseFunctionCallOutputItem.ofInputText(
                    ResponseInputTextContent.builder().text(writeAsJson(doc.document())).build()));
        case ObjectContent obj ->
            items.add(
                ResponseFunctionCallOutputItem.ofInputText(
                    ResponseInputTextContent.builder().text(writeAsJson(obj.content())).build()));
        default ->
            items.add(
                ResponseFunctionCallOutputItem.ofInputText(
                    ResponseInputTextContent.builder().text(writeAsJson(c)).build()));
      }
    }
    return items;
  }

  public List<ChatCompletionContentPart> toCompletionsContentParts(List<Content> content) {
    final List<ChatCompletionContentPart> parts = new ArrayList<>();
    for (final Content c : content) {
      switch (c) {
        case TextContent text ->
            parts.add(
                ChatCompletionContentPart.ofText(
                    ChatCompletionContentPartText.builder().text(text.text()).build()));
        case DocumentContent doc -> parts.add(completionsDocumentPart(doc));
        case ObjectContent obj ->
            parts.add(
                ChatCompletionContentPart.ofText(
                    ChatCompletionContentPartText.builder()
                        .text(writeAsJson(obj.content()))
                        .build()));
        default ->
            parts.add(
                ChatCompletionContentPart.ofText(
                    ChatCompletionContentPartText.builder().text(writeAsJson(c)).build()));
      }
    }
    return parts;
  }

  private ResponseInputContent responsesDocumentPart(DocumentContent doc) {
    final var contentType = contentType(doc.document());
    return switch (classify(contentType)) {
      case IMAGE ->
          ResponseInputContent.ofInputImage(
              ResponseInputImage.builder()
                  .imageUrl(dataUri(contentType, doc.document()))
                  .detail(ResponseInputImage.Detail.AUTO)
                  .build());
      case PDF ->
          ResponseInputContent.ofInputFile(
              ResponseInputFile.builder()
                  .filename(fileName(doc.document()))
                  .fileData(dataUri(contentType, doc.document()))
                  .build());
      case TEXT ->
          ResponseInputContent.ofInputText(
              ResponseInputText.builder().text(decodeUtf8(doc.document())).build());
      case UNSUPPORTED -> throw unsupportedContentType(contentType, doc);
    };
  }

  private ChatCompletionContentPart completionsDocumentPart(DocumentContent doc) {
    final var contentType = contentType(doc.document());
    return switch (classify(contentType)) {
      case IMAGE ->
          ChatCompletionContentPart.ofImageUrl(
              ChatCompletionContentPartImage.builder()
                  .imageUrl(
                      ChatCompletionContentPartImage.ImageUrl.builder()
                          .url(dataUri(contentType, doc.document()))
                          .detail(ChatCompletionContentPartImage.ImageUrl.Detail.AUTO)
                          .build())
                  .build());
      case PDF ->
          ChatCompletionContentPart.ofFile(
              ChatCompletionContentPart.File.builder()
                  .file(
                      ChatCompletionContentPart.File.FileObject.builder()
                          .filename(fileName(doc.document()))
                          .fileData(dataUri(contentType, doc.document()))
                          .build())
                  .build());
      case TEXT ->
          ChatCompletionContentPart.ofText(
              ChatCompletionContentPartText.builder().text(decodeUtf8(doc.document())).build());
      case UNSUPPORTED -> throw unsupportedContentType(contentType, doc);
    };
  }

  private static ConnectorException unsupportedContentType(
      String contentType, DocumentContent doc) {
    return new ConnectorException(
        ERROR_CODE_FAILED_MODEL_CALL,
        "Unsupported content type '%s' for document with reference '%s'"
            .formatted(contentType, doc.document().reference()));
  }

  private static String dataUri(String contentType, Document document) {
    return "data:" + contentType + ";base64," + document.asBase64();
  }

  private static String fileName(Document document) {
    final var metadata = document.metadata();
    final var name = metadata != null ? metadata.getFileName() : null;
    return name != null ? name : DEFAULT_FILE_NAME;
  }

  private static String contentType(Document document) {
    final var metadata = document.metadata();
    final var type = metadata != null ? metadata.getContentType() : null;
    return type != null ? type : "application/octet-stream";
  }

  private static String decodeUtf8(Document document) {
    return new String(document.asByteArray(), StandardCharsets.UTF_8);
  }

  private String writeAsJson(Object value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Failed to serialize content to JSON", e);
    }
  }

  /**
   * Coarse content-type buckets driving the document-part builder methods' choice of OpenAI part
   * shape. Unknown/blank/unparseable types map conservatively to {@link #UNSUPPORTED}, which fails
   * the request. Mirrors {@code AnthropicContentConverter}'s {@code DocumentBlockKind}.
   */
  private enum DocumentPartKind {
    IMAGE,
    PDF,
    TEXT,
    UNSUPPORTED
  }

  private static DocumentPartKind classify(String contentType) {
    if (contentType.isBlank()) {
      return DocumentPartKind.UNSUPPORTED;
    }

    final ContentType parsed;
    try {
      parsed = ContentType.parse(contentType.trim().toLowerCase(Locale.ROOT));
    } catch (RuntimeException e) {
      return DocumentPartKind.UNSUPPORTED;
    }
    if (parsed == null) {
      return DocumentPartKind.UNSUPPORTED;
    }

    if (isCompatibleWithAnyOf(parsed, IMAGE_CONTENT_TYPES)) {
      return DocumentPartKind.IMAGE;
    }
    if (isCompatibleWithAnyOf(parsed, PDF_CONTENT_TYPES)) {
      return DocumentPartKind.PDF;
    }

    final var mime = parsed.getMimeType();
    if (mime.startsWith("text/")
        || isCompatibleWithAnyOf(parsed, ADDITIONAL_TEXT_FILE_CONTENT_TYPES)
        || mime.equals("application/x-yaml")
        || mime.endsWith("+json")
        || mime.endsWith("+xml")) {
      return DocumentPartKind.TEXT;
    }
    return DocumentPartKind.UNSUPPORTED;
  }

  private static boolean isCompatibleWithAnyOf(
      ContentType contentType, List<ContentType> contentTypes) {
    return contentTypes.stream().anyMatch(contentType::isSameMimeType);
  }
}
