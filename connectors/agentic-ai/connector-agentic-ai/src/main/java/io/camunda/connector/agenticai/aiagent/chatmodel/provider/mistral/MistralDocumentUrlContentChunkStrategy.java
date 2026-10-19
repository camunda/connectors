/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.chatmodel.provider.mistral;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openai.core.ObjectMappers;
import com.openai.models.chat.completions.ChatCompletionContentPart;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.openai.OpenAiDocumentParts;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.openai.family.completions.OpenAiFileContentChunkStrategy;
import io.camunda.connector.agenticai.aiagent.model.message.content.DocumentContent;
import java.util.Map;

/**
 * Mistral's Chat Completions content-chunk shapes. Only the PDF branch differs from {@link
 * OpenAiFileContentChunkStrategy}: Mistral rejects OpenAI's {@code file}/{@code file_data} chunk
 * and requires a {@code document_url} chunk carrying the data URI directly instead. Image parts
 * need no such divergence: Mistral accepts the identical {@code image_url} shape OpenAI uses.
 */
public final class MistralDocumentUrlContentChunkStrategy extends OpenAiFileContentChunkStrategy {

  public MistralDocumentUrlContentChunkStrategy(ObjectMapper objectMapper) {
    super(objectMapper);
  }

  @Override
  protected ChatCompletionContentPart pdfPart(String contentType, DocumentContent doc) {
    return rawContentPart(
        Map.of(
            "type",
            "document_url",
            "document_url",
            OpenAiDocumentParts.dataUri(contentType, doc.document())));
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
}
