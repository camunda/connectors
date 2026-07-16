/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.framework.openai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openai.models.chat.completions.ChatCompletionContentPart;
import com.openai.models.chat.completions.ChatCompletionContentPartImage;
import com.openai.models.chat.completions.ChatCompletionContentPartText;
import com.openai.models.responses.ResponseFunctionCallOutputItem;
import com.openai.models.responses.ResponseInputContent;
import com.openai.models.responses.ResponseInputFile;
import com.openai.models.responses.ResponseInputFileContent;
import com.openai.models.responses.ResponseInputImage;
import com.openai.models.responses.ResponseInputImageContent;
import com.openai.models.responses.ResponseInputText;
import com.openai.models.responses.ResponseInputTextContent;
import io.camunda.connector.agenticai.aiagent.framework.multimodal.DocumentModality;
import io.camunda.connector.agenticai.aiagent.model.message.content.Content;
import io.camunda.connector.agenticai.aiagent.model.message.content.DocumentContent;
import io.camunda.connector.agenticai.aiagent.model.message.content.ObjectContent;
import io.camunda.connector.agenticai.aiagent.model.message.content.TextContent;
import io.camunda.connector.api.document.Document;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Converts the domain {@link Content} model to OpenAI SDK content parts, for both the Responses API
 * ({@link ResponseInputContent}) and the Chat Completions API ({@link ChatCompletionContentPart})
 * families. Used for user/assistant message bodies as well as Responses tool-result bodies ({@link
 * ResponseFunctionCallOutputItem}).
 */
public class OpenAiContentConverter {

  private static final String DEFAULT_FILE_NAME = "document";

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
   * Converts a tool result's structured content into Responses {@code function_call_output} items,
   * the multimodal counterpart of {@link #toResponsesContentParts}: documents are emitted natively
   * as {@code input_image} / {@code input_file} so the model can read them, rather than being
   * flattened to an opaque JSON reference. Mirrors the Anthropic sibling's {@code
   * toToolResultBlocks}. Callers only route documents here when the capability matrix declares the
   * modality supported in tool results (see {@code CapabilityAwareToolCallResultStrategy}).
   */
  public List<ResponseFunctionCallOutputItem> toToolResultOutputItems(List<Content> content) {
    final List<ResponseFunctionCallOutputItem> items = new ArrayList<>();
    for (final Content c : content) {
      switch (c) {
        case TextContent text ->
            items.add(
                ResponseFunctionCallOutputItem.ofInputText(
                    ResponseInputTextContent.builder().text(text.text()).build()));
        case DocumentContent doc -> items.add(toolResultDocumentItem(doc));
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
    final var modality = DocumentModality.fromDocument(doc.document());
    final var contentType = contentType(doc.document());
    return switch (modality) {
      case IMAGE ->
          ResponseInputContent.ofInputImage(
              ResponseInputImage.builder()
                  .imageUrl(dataUri(contentType, doc.document()))
                  .detail(ResponseInputImage.Detail.AUTO)
                  .build());
      case DOCUMENT ->
          ResponseInputContent.ofInputFile(
              ResponseInputFile.builder()
                  .filename(fileName(doc.document()))
                  .fileData(dataUri(contentType, doc.document()))
                  .build());
      case TEXT ->
          ResponseInputContent.ofInputText(
              ResponseInputText.builder().text(decodeUtf8(doc.document())).build());
      default ->
          ResponseInputContent.ofInputText(
              ResponseInputText.builder().text(writeAsJson(doc)).build());
    };
  }

  private ResponseFunctionCallOutputItem toolResultDocumentItem(DocumentContent doc) {
    final var modality = DocumentModality.fromDocument(doc.document());
    final var contentType = contentType(doc.document());
    return switch (modality) {
      case IMAGE ->
          ResponseFunctionCallOutputItem.ofInputImage(
              ResponseInputImageContent.builder()
                  .imageUrl(dataUri(contentType, doc.document()))
                  .detail(ResponseInputImageContent.Detail.AUTO)
                  .build());
      case DOCUMENT ->
          ResponseFunctionCallOutputItem.ofInputFile(
              ResponseInputFileContent.builder()
                  .filename(fileName(doc.document()))
                  .fileData(dataUri(contentType, doc.document()))
                  .build());
      case TEXT ->
          ResponseFunctionCallOutputItem.ofInputText(
              ResponseInputTextContent.builder().text(decodeUtf8(doc.document())).build());
      default ->
          ResponseFunctionCallOutputItem.ofInputText(
              ResponseInputTextContent.builder().text(writeAsJson(doc)).build());
    };
  }

  private ChatCompletionContentPart completionsDocumentPart(DocumentContent doc) {
    final var modality = DocumentModality.fromDocument(doc.document());
    final var contentType = contentType(doc.document());
    return switch (modality) {
      case IMAGE ->
          ChatCompletionContentPart.ofImageUrl(
              ChatCompletionContentPartImage.builder()
                  .imageUrl(
                      ChatCompletionContentPartImage.ImageUrl.builder()
                          .url(dataUri(contentType, doc.document()))
                          .detail(ChatCompletionContentPartImage.ImageUrl.Detail.AUTO)
                          .build())
                  .build());
      case DOCUMENT ->
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
      default ->
          ChatCompletionContentPart.ofText(
              ChatCompletionContentPartText.builder().text(writeAsJson(doc)).build());
    };
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
}
