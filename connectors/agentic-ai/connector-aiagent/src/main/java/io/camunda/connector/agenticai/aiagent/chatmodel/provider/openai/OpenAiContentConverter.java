/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.chatmodel.provider.openai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openai.models.responses.ResponseFunctionCallOutputItem;
import com.openai.models.responses.ResponseInputContent;
import com.openai.models.responses.ResponseInputFile;
import com.openai.models.responses.ResponseInputImage;
import com.openai.models.responses.ResponseInputText;
import com.openai.models.responses.ResponseInputTextContent;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.DocumentMimeTypes;
import io.camunda.connector.agenticai.aiagent.model.message.content.Content;
import io.camunda.connector.agenticai.aiagent.model.message.content.DocumentContent;
import io.camunda.connector.agenticai.aiagent.model.message.content.ObjectContent;
import io.camunda.connector.agenticai.aiagent.model.message.content.ProviderContent;
import io.camunda.connector.agenticai.aiagent.model.message.content.ReasoningContent;
import io.camunda.connector.agenticai.aiagent.model.message.content.TextContent;
import java.util.ArrayList;
import java.util.List;

/**
 * Converts the domain {@link Content} model to OpenAI Responses API content parts ({@link
 * ResponseInputContent}). Used for user/assistant message bodies as well as Responses tool-result
 * bodies ({@link ResponseFunctionCallOutputItem}).
 *
 * <p>The Chat Completions family's equivalent conversion lives in {@code
 * io.camunda....openai.family.completions.OpenAiCompletionsContentChunkStrategy} instead, as a
 * per-provider strategy rather than a method on this class: OpenAI and Mistral share that family's
 * wire format overall, but diverge on the PDF document-part shape, and Mistral has no Responses
 * family at all, so there is nothing to share here.
 */
public class OpenAiContentConverter {

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
        // ReasoningContent/ProviderContent's actual wire-shaped replay is handled by the request
        // converters before this method is ever called with an assistant's plain content; if one
        // still reaches here, it is added as a single JSON-reference text part rather than lost or
        // duplicated - not its native shape, but not dropped either.
        case ReasoningContent reasoning ->
            parts.add(
                ResponseInputContent.ofInputText(
                    ResponseInputText.builder().text(writeAsJson(reasoning)).build()));
        case ProviderContent providerContent ->
            parts.add(
                ResponseInputContent.ofInputText(
                    ResponseInputText.builder().text(writeAsJson(providerContent)).build()));
      }
    }
    return parts;
  }

  /**
   * Converts a tool result's structured content into Responses {@code function_call_output} items.
   * A document here is flattened to a JSON reference rather than emitted natively as {@code
   * input_image}/{@code input_file}, unlike {@link #toResponsesContentParts}: the document's actual
   * bytes are already delivered to the model elsewhere for tool results, so embedding it here as
   * well would send it twice.
   */
  public List<ResponseFunctionCallOutputItem> toResponsesToolResultOutputItems(
      List<Content> content) {
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
        case ReasoningContent reasoning ->
            items.add(
                ResponseFunctionCallOutputItem.ofInputText(
                    ResponseInputTextContent.builder().text(writeAsJson(reasoning)).build()));
        case ProviderContent providerContent ->
            items.add(
                ResponseFunctionCallOutputItem.ofInputText(
                    ResponseInputTextContent.builder().text(writeAsJson(providerContent)).build()));
      }
    }
    return items;
  }

  private ResponseInputContent responsesDocumentPart(DocumentContent doc) {
    final var contentType = DocumentMimeTypes.requireContentType(doc.document());
    return switch (OpenAiDocumentParts.classify(DocumentMimeTypes.parse(contentType))) {
      case IMAGE ->
          ResponseInputContent.ofInputImage(
              ResponseInputImage.builder()
                  .imageUrl(OpenAiDocumentParts.dataUri(contentType, doc.document()))
                  .detail(ResponseInputImage.Detail.AUTO)
                  .build());
      case PDF ->
          ResponseInputContent.ofInputFile(
              ResponseInputFile.builder()
                  .filename(OpenAiDocumentParts.fileName(doc.document()))
                  .fileData(OpenAiDocumentParts.dataUri(contentType, doc.document()))
                  .build());
      case TEXT ->
          ResponseInputContent.ofInputText(
              ResponseInputText.builder()
                  .text(OpenAiDocumentParts.decodeUtf8(doc.document()))
                  .build());
      case UNSUPPORTED -> throw OpenAiDocumentParts.unsupportedContentType(contentType, doc);
    };
  }

  public String writeAsJson(Object value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Failed to serialize content to JSON", e);
    }
  }
}
