/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.chatmodel.provider.openai.family.completions;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openai.core.ObjectMappers;
import com.openai.models.chat.completions.ChatCompletionContentPart;
import com.openai.models.chat.completions.ChatCompletionContentPartImage;
import com.openai.models.chat.completions.ChatCompletionContentPartText;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.DocumentMimeTypes;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.openai.OpenAiDocumentParts;
import io.camunda.connector.agenticai.aiagent.model.message.content.Content;
import io.camunda.connector.agenticai.aiagent.model.message.content.DocumentContent;
import io.camunda.connector.agenticai.aiagent.model.message.content.ObjectContent;
import io.camunda.connector.agenticai.aiagent.model.message.content.ProviderContent;
import io.camunda.connector.agenticai.aiagent.model.message.content.ReasoningContent;
import io.camunda.connector.agenticai.aiagent.model.message.content.TextContent;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Mistral's Chat Completions content-chunk shapes. Only the PDF branch differs from {@link
 * OpenAiFileContentChunkStrategy}: Mistral rejects OpenAI's {@code file}/{@code file_data} chunk
 * and requires a {@code document_url} chunk carrying the data URI directly instead (verified
 * against the real API). Image parts need no such divergence: Mistral accepts the identical {@code
 * image_url} shape OpenAI uses (also verified against the real API).
 */
final class MistralDocumentUrlContentChunkStrategy
    implements OpenAiCompletionsContentChunkStrategy {

  private final ObjectMapper objectMapper;

  MistralDocumentUrlContentChunkStrategy(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  @Override
  public List<ChatCompletionContentPart> toContentParts(List<Content> content) {
    final List<ChatCompletionContentPart> parts = new ArrayList<>();
    for (final Content c : content) {
      switch (c) {
        case TextContent text ->
            parts.add(
                ChatCompletionContentPart.ofText(
                    ChatCompletionContentPartText.builder().text(text.text()).build()));
        case DocumentContent doc -> parts.add(documentPart(doc));
        case ObjectContent obj ->
            parts.add(
                ChatCompletionContentPart.ofText(
                    ChatCompletionContentPartText.builder()
                        .text(writeAsJson(obj.content()))
                        .build()));
        case ReasoningContent reasoning ->
            parts.add(
                ChatCompletionContentPart.ofText(
                    ChatCompletionContentPartText.builder().text(writeAsJson(reasoning)).build()));
        case ProviderContent providerContent ->
            parts.add(
                ChatCompletionContentPart.ofText(
                    ChatCompletionContentPartText.builder()
                        .text(writeAsJson(providerContent))
                        .build()));
      }
    }
    return parts;
  }

  private ChatCompletionContentPart documentPart(DocumentContent doc) {
    final var contentType = DocumentMimeTypes.requireContentType(doc.document());
    return switch (OpenAiDocumentParts.classify(DocumentMimeTypes.parse(contentType))) {
      case IMAGE ->
          ChatCompletionContentPart.ofImageUrl(
              ChatCompletionContentPartImage.builder()
                  .imageUrl(
                      ChatCompletionContentPartImage.ImageUrl.builder()
                          .url(OpenAiDocumentParts.dataUri(contentType, doc.document()))
                          .detail(ChatCompletionContentPartImage.ImageUrl.Detail.AUTO)
                          .build())
                  .build());
      case PDF ->
          rawContentPart(
              Map.of(
                  "type",
                  "document_url",
                  "document_url",
                  OpenAiDocumentParts.dataUri(contentType, doc.document())));
      case TEXT ->
          ChatCompletionContentPart.ofText(
              ChatCompletionContentPartText.builder()
                  .text(OpenAiDocumentParts.decodeUtf8(doc.document()))
                  .build());
      case UNSUPPORTED -> throw OpenAiDocumentParts.unsupportedContentType(contentType, doc);
    };
  }

  /**
   * Builds a {@link ChatCompletionContentPart} for a shape the SDK doesn't model (the {@code
   * document_url} chunk) by deserializing it through the SDK's own mapper: {@code
   * ChatCompletionContentPart}'s deserializer falls back to storing any unrecognized {@code type}
   * verbatim in an internal raw-JSON field, and its serializer writes that field straight back out
   * unchanged, so this round-trips byte-identical without needing a typed SDK builder for it.
   */
  private static ChatCompletionContentPart rawContentPart(Map<String, Object> raw) {
    return ObjectMappers.jsonMapper().convertValue(raw, ChatCompletionContentPart.class);
  }

  private String writeAsJson(Object value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Failed to serialize content to JSON", e);
    }
  }
}
