/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.chatmodel.provider.anthropic;

import com.anthropic.core.ObjectMappers;
import com.anthropic.models.messages.Base64ImageSource;
import com.anthropic.models.messages.ContentBlockParam;
import com.anthropic.models.messages.DocumentBlockParam;
import com.anthropic.models.messages.ImageBlockParam;
import com.anthropic.models.messages.TextBlockParam;
import com.anthropic.models.messages.ToolResultBlockParam;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.agenticai.aiagent.model.message.content.Content;
import io.camunda.connector.agenticai.aiagent.model.message.content.DocumentContent;
import io.camunda.connector.agenticai.aiagent.model.message.content.ObjectContent;
import io.camunda.connector.agenticai.aiagent.model.message.content.ProviderContent;
import io.camunda.connector.agenticai.aiagent.model.message.content.ReasoningContent;
import io.camunda.connector.agenticai.aiagent.model.message.content.TextContent;
import io.camunda.connector.api.document.Document;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.apache.hc.core5.http.ContentType;

/**
 * Converts the domain {@link Content} model to Anthropic SDK content blocks, both for
 * user/assistant message bodies ({@link ContentBlockParam}) and tool-result bodies ({@link
 * ToolResultBlockParam.Content.Block}).
 */
public class AnthropicContentConverter {

  private static final List<ContentType> PDF_CONTENT_TYPES = List.of(ContentType.APPLICATION_PDF);

  private static final List<ContentType> ADDITIONAL_TEXT_FILE_CONTENT_TYPES =
      List.of(
          ContentType.APPLICATION_JSON,
          ContentType.APPLICATION_XML,
          ContentType.create("application/yaml"));

  private final ObjectMapper objectMapper;

  public AnthropicContentConverter(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  public List<ContentBlockParam> toContentBlockParams(List<Content> content) {
    final List<ContentBlockParam> blocks = new ArrayList<>();
    for (final Content c : content) {
      switch (c) {
        case TextContent text ->
            blocks.add(
                ContentBlockParam.ofText(TextBlockParam.builder().text(text.text()).build()));
        case DocumentContent doc -> blocks.add(documentBlock(doc));
        case ObjectContent obj ->
            blocks.add(
                ContentBlockParam.ofText(
                    TextBlockParam.builder().text(writeAsJson(obj.content())).build()));
        // Reasoning content is re-emitted unconditionally as long as a raw providerPayload is
        // present. A null payload has no wire representation to replay; skip it so history
        // replay stays valid.
        case ReasoningContent rc -> {
          if (rc.providerPayload() != null) {
            blocks.add(
                ObjectMappers.jsonMapper()
                    .convertValue(rc.providerPayload(), ContentBlockParam.class));
          }
        }
        case ProviderContent pc -> {
          // A null payload (reachable via the public constructor) has no wire representation to
          // replay; skip it instead of emitting a null content block.
          if (pc.payload() != null) {
            blocks.add(
                ObjectMappers.jsonMapper().convertValue(pc.payload(), ContentBlockParam.class));
          }
        }
      }
    }
    return blocks;
  }

  public List<ToolResultBlockParam.Content.Block> toToolResultBlocks(List<Content> content) {
    final List<ToolResultBlockParam.Content.Block> blocks = new ArrayList<>();
    for (final Content c : content) {
      switch (c) {
        case TextContent text ->
            blocks.add(
                ToolResultBlockParam.Content.Block.ofText(
                    TextBlockParam.builder().text(text.text()).build()));
        case DocumentContent doc -> {
          final ContentBlockParam block = documentBlock(doc);
          block.image().ifPresent(i -> blocks.add(ToolResultBlockParam.Content.Block.ofImage(i)));
          block
              .document()
              .ifPresent(d -> blocks.add(ToolResultBlockParam.Content.Block.ofDocument(d)));
          block.text().ifPresent(t -> blocks.add(ToolResultBlockParam.Content.Block.ofText(t)));
        }
        case ObjectContent obj ->
            blocks.add(
                ToolResultBlockParam.Content.Block.ofText(
                    TextBlockParam.builder().text(writeAsJson(obj.content())).build()));
        default ->
            blocks.add(
                ToolResultBlockParam.Content.Block.ofText(
                    TextBlockParam.builder().text(writeAsJson(c)).build()));
      }
    }
    return blocks;
  }

  private ContentBlockParam documentBlock(DocumentContent doc) {
    final var contentType = contentType(doc.document());
    return switch (classify(contentType)) {
      case IMAGE ->
          ContentBlockParam.ofImage(
              ImageBlockParam.builder()
                  .source(
                      Base64ImageSource.builder()
                          .data(doc.document().asBase64())
                          .mediaType(Base64ImageSource.MediaType.of(contentType))
                          .build())
                  .build());
      case PDF ->
          ContentBlockParam.ofDocument(
              DocumentBlockParam.builder().base64Source(doc.document().asBase64()).build());
      // TEXT-family documents inline as plain text. The Anthropic Messages API otherwise only
      // accepts images and PDFs as document/image blocks; there is no native block for other
      // formats (e.g. zip, docx, pptx), so those fall back to a JSON reference.
      case TEXT ->
          ContentBlockParam.ofDocument(
              DocumentBlockParam.builder().textSource(decodeUtf8(doc.document())).build());
      case UNSUPPORTED ->
          ContentBlockParam.ofText(TextBlockParam.builder().text(writeAsJson(doc)).build());
    };
  }

  /**
   * Coarse content-type buckets driving {@link #documentBlock(DocumentContent)}'s choice of
   * Anthropic block shape. Kept local to this converter -- it only needs to pick between the
   * handful of block shapes below, not classify the full modality space -- rather than a shared
   * modality abstraction. Unknown/blank/unparseable types map conservatively to {@link
   * #UNSUPPORTED}, which gates to the synthetic JSON fallback.
   */
  private enum DocumentBlockKind {
    IMAGE,
    PDF,
    TEXT,
    UNSUPPORTED
  }

  private static DocumentBlockKind classify(String contentType) {
    if (contentType.isBlank()) {
      return DocumentBlockKind.UNSUPPORTED;
    }

    final ContentType parsed;
    try {
      parsed = ContentType.parse(contentType.trim().toLowerCase(Locale.ROOT));
    } catch (RuntimeException e) {
      return DocumentBlockKind.UNSUPPORTED;
    }
    if (parsed == null) {
      return DocumentBlockKind.UNSUPPORTED;
    }

    final var mime = parsed.getMimeType();
    if (mime.startsWith("image/")) {
      return DocumentBlockKind.IMAGE;
    }
    if (isCompatibleWithAnyOf(parsed, PDF_CONTENT_TYPES)) {
      return DocumentBlockKind.PDF;
    }
    if (mime.startsWith("text/")
        || isCompatibleWithAnyOf(parsed, ADDITIONAL_TEXT_FILE_CONTENT_TYPES)
        || mime.equals("application/x-yaml")
        || mime.endsWith("+json")
        || mime.endsWith("+xml")) {
      return DocumentBlockKind.TEXT;
    }
    return DocumentBlockKind.UNSUPPORTED;
  }

  private static boolean isCompatibleWithAnyOf(
      ContentType contentType, List<ContentType> contentTypes) {
    return contentTypes.stream().anyMatch(contentType::isSameMimeType);
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
