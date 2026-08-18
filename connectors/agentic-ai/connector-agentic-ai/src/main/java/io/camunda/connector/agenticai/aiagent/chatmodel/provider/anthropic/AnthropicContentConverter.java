/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.chatmodel.provider.anthropic;

import static io.camunda.connector.agenticai.aiagent.agent.AgentErrorCodes.ERROR_CODE_FAILED_MODEL_CALL;
import static io.camunda.connector.agenticai.aiagent.model.request.v2.AnthropicChatModelConfiguration.ANTHROPIC_ID;

import com.anthropic.core.ObjectMappers;
import com.anthropic.models.messages.Base64ImageSource;
import com.anthropic.models.messages.ContentBlockParam;
import com.anthropic.models.messages.DocumentBlockParam;
import com.anthropic.models.messages.ImageBlockParam;
import com.anthropic.models.messages.TextBlockParam;
import com.anthropic.models.messages.ToolResultBlockParam;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.DocumentMimeTypes;
import io.camunda.connector.agenticai.aiagent.model.message.content.Content;
import io.camunda.connector.agenticai.aiagent.model.message.content.DocumentContent;
import io.camunda.connector.agenticai.aiagent.model.message.content.ObjectContent;
import io.camunda.connector.agenticai.aiagent.model.message.content.ProviderContent;
import io.camunda.connector.agenticai.aiagent.model.message.content.ReasoningContent;
import io.camunda.connector.agenticai.aiagent.model.message.content.TextContent;
import io.camunda.connector.api.document.Document;
import io.camunda.connector.api.error.ConnectorException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.apache.hc.core5.http.ContentType;
import org.jspecify.annotations.Nullable;

/**
 * Converts the domain {@link Content} model to Anthropic SDK content blocks, both for
 * user/assistant message bodies ({@link ContentBlockParam}) and tool-result bodies ({@link
 * ToolResultBlockParam.Content.Block}).
 */
public class AnthropicContentConverter {

  private final ObjectMapper objectMapper;

  public AnthropicContentConverter(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  public List<ContentBlockParam> toContentBlockParams(List<Content> content) {
    final List<ContentBlockParam> blocks = new ArrayList<>();
    for (final Content c : content) {
      switch (c) {
        case TextContent text -> blocks.add(ContentBlockParam.ofText(toTextBlockParam(text)));
        case DocumentContent doc -> blocks.add(toDocumentBlockParam(doc));
        case ObjectContent obj -> blocks.add(ContentBlockParam.ofText(toTextBlockParam(obj)));
        // Content tagged for a different provider is dropped rather than replayed.
        case ReasoningContent rc when ANTHROPIC_ID.equals(rc.provider()) ->
            blocks.add(toReasoningContentBlockParam(rc));
        case ReasoningContent ignored -> {}
        case ProviderContent pc when ANTHROPIC_ID.equals(pc.provider()) ->
            blocks.add(toProviderContentBlockParam(pc));
        case ProviderContent ignored -> {}
      }
    }
    return blocks;
  }

  private TextBlockParam toTextBlockParam(TextContent text) {
    return TextBlockParam.builder().text(text.text()).build();
  }

  private TextBlockParam toTextBlockParam(ObjectContent obj) {
    return TextBlockParam.builder().text(writeAsJson(obj.content())).build();
  }

  private ContentBlockParam toDocumentBlockParam(DocumentContent doc) {
    final var contentType = DocumentMimeTypes.requireContentType(doc.document());
    final var parsed = DocumentMimeTypes.parse(contentType);
    return switch (classify(parsed)) {
      case IMAGE ->
          ContentBlockParam.ofImage(
              ImageBlockParam.builder()
                  .source(
                      Base64ImageSource.builder()
                          .data(doc.document().asBase64())
                          .mediaType(
                              Base64ImageSource.MediaType.of(
                                  Objects.requireNonNull(parsed).getMimeType()))
                          .build())
                  .build());
      case PDF ->
          ContentBlockParam.ofDocument(
              DocumentBlockParam.builder().base64Source(doc.document().asBase64()).build());
      case TEXT ->
          ContentBlockParam.ofDocument(
              DocumentBlockParam.builder().textSource(decodeUtf8(doc.document())).build());
      case UNSUPPORTED ->
          throw new ConnectorException(
              ERROR_CODE_FAILED_MODEL_CALL,
              "Unsupported content type '%s' for document with reference '%s'"
                  .formatted(contentType, doc.document().reference()));
    };
  }

  /**
   * Reconstructs the native content block from a {@link ReasoningContent}. The human-readable
   * thinking text was lifted out of the payload into {@code text} when the response was received
   * (see {@code AnthropicMessageResponseConverter}) so it isn't persisted twice; here it is merged
   * back into the payload verbatim before replay, so the resulting block is byte-identical to the
   * one originally returned by the API (required both for the {@code thinking} block's signature
   * verification and for prompt-caching prefix matching on the next request).
   */
  private ContentBlockParam toReasoningContentBlockParam(ReasoningContent rc) {
    Object payload = rc.payload();
    if (rc.text() != null) {
      if (!(payload instanceof Map<?, ?> rawPayload)) {
        throw new ConnectorException(
            ERROR_CODE_FAILED_MODEL_CALL,
            "Expected reasoning content payload to be a Map when text is present, got %s"
                .formatted(payload == null ? "null" : payload.getClass().getSimpleName()));
      }
      final Map<String, Object> merged = new LinkedHashMap<>();
      rawPayload.forEach((k, v) -> merged.put(String.valueOf(k), v));
      merged.put("thinking", rc.text());
      payload = merged;
    }
    return toContentBlockParam(payload);
  }

  private ContentBlockParam toProviderContentBlockParam(ProviderContent pc) {
    return toContentBlockParam(pc.payload());
  }

  private ContentBlockParam toContentBlockParam(Object payload) {
    return ObjectMappers.jsonMapper().convertValue(payload, ContentBlockParam.class);
  }

  /**
   * Converts a tool result's structured content into Anthropic tool-result blocks. Unlike {@link
   * #toContentBlockParams}, a document here is flattened to a JSON reference rather than embedded
   * natively as an image/document block: the document's actual bytes are already delivered to the
   * model elsewhere for tool results, so embedding it here as well would send it twice.
   */
  public List<ToolResultBlockParam.Content.Block> toToolResultBlocks(List<Content> content) {
    final List<ToolResultBlockParam.Content.Block> blocks = new ArrayList<>();
    for (final Content c : content) {
      switch (c) {
        case TextContent text ->
            blocks.add(ToolResultBlockParam.Content.Block.ofText(toTextBlockParam(text)));
        case DocumentContent doc ->
            blocks.add(
                ToolResultBlockParam.Content.Block.ofText(
                    TextBlockParam.builder().text(writeAsJson(doc.document())).build()));
        case ObjectContent obj ->
            blocks.add(ToolResultBlockParam.Content.Block.ofText(toTextBlockParam(obj)));
        default ->
            blocks.add(
                ToolResultBlockParam.Content.Block.ofText(
                    TextBlockParam.builder().text(writeAsJson(c)).build()));
      }
    }
    return blocks;
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
   * Coarse content-type buckets driving {@link #toDocumentBlockParam(DocumentContent)}'s choice of
   * Anthropic block shape. Unknown/blank/unparseable types map conservatively to {@link
   * #UNSUPPORTED}, which fails the request.
   */
  private enum DocumentBlockKind {
    IMAGE,
    PDF,
    TEXT,
    UNSUPPORTED
  }

  private static DocumentBlockKind classify(@Nullable ContentType contentType) {
    if (contentType == null) {
      return DocumentBlockKind.UNSUPPORTED;
    }
    if (DocumentMimeTypes.isImage(contentType)) {
      return DocumentBlockKind.IMAGE;
    }
    if (DocumentMimeTypes.isPdf(contentType)) {
      return DocumentBlockKind.PDF;
    }
    if (DocumentMimeTypes.isTextIsh(contentType)) {
      return DocumentBlockKind.TEXT;
    }
    return DocumentBlockKind.UNSUPPORTED;
  }
}
