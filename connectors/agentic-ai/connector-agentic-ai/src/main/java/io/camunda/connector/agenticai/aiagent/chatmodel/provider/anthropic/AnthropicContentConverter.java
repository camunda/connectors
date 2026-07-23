/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.chatmodel.provider.anthropic;

import com.anthropic.core.ObjectMappers;
import com.anthropic.models.beta.messages.BetaBase64ImageSource;
import com.anthropic.models.beta.messages.BetaContentBlockParam;
import com.anthropic.models.beta.messages.BetaImageBlockParam;
import com.anthropic.models.beta.messages.BetaRequestDocumentBlock;
import com.anthropic.models.beta.messages.BetaTextBlockParam;
import com.anthropic.models.beta.messages.BetaToolResultBlockParam;
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
 * user/assistant message bodies ({@link BetaContentBlockParam}) and tool-result bodies ({@link
 * BetaToolResultBlockParam.Content.Block}).
 *
 * <p>Uses the <strong>beta</strong> messages client types (rather than the stable {@code
 * com.anthropic.models.messages} family), matching the client wired by {@link
 * AnthropicClientFactory}.
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

  public List<BetaContentBlockParam> toContentBlockParams(List<Content> content) {
    final List<BetaContentBlockParam> blocks = new ArrayList<>();
    for (final Content c : content) {
      switch (c) {
        case TextContent text ->
            blocks.add(
                BetaContentBlockParam.ofText(
                    BetaTextBlockParam.builder().text(text.text()).build()));
        case DocumentContent doc -> blocks.add(documentBlock(doc));
        case ObjectContent obj ->
            blocks.add(
                BetaContentBlockParam.ofText(
                    BetaTextBlockParam.builder().text(writeAsJson(obj.content())).build()));
        // Reasoning content is re-emitted unconditionally as long as a raw providerPayload is
        // present. A null payload (e.g. reasoning content produced by the LangChain4J-routed
        // path, which has no raw block to preserve) has no wire representation to replay; skip
        // it so history replay stays valid.
        case ReasoningContent rc -> {
          if (rc.providerPayload() != null) {
            blocks.add(
                ObjectMappers.jsonMapper()
                    .convertValue(rc.providerPayload(), BetaContentBlockParam.class));
          }
        }
        case ProviderContent pc -> {
          // A null payload (reachable via the public constructor) has no wire representation to
          // replay; skip it instead of emitting a null content block.
          if (pc.payload() != null) {
            blocks.add(
                ObjectMappers.jsonMapper().convertValue(pc.payload(), BetaContentBlockParam.class));
          }
        }
      }
    }
    return blocks;
  }

  public List<BetaToolResultBlockParam.Content.Block> toToolResultBlocks(List<Content> content) {
    final List<BetaToolResultBlockParam.Content.Block> blocks = new ArrayList<>();
    for (final Content c : content) {
      switch (c) {
        case TextContent text ->
            blocks.add(
                BetaToolResultBlockParam.Content.Block.ofText(
                    BetaTextBlockParam.builder().text(text.text()).build()));
        case DocumentContent doc -> {
          final BetaContentBlockParam block = documentBlock(doc);
          block
              .image()
              .ifPresent(i -> blocks.add(BetaToolResultBlockParam.Content.Block.ofImage(i)));
          block
              .document()
              .ifPresent(d -> blocks.add(BetaToolResultBlockParam.Content.Block.ofDocument(d)));
          block.text().ifPresent(t -> blocks.add(BetaToolResultBlockParam.Content.Block.ofText(t)));
        }
        case ObjectContent obj ->
            blocks.add(
                BetaToolResultBlockParam.Content.Block.ofText(
                    BetaTextBlockParam.builder().text(writeAsJson(obj.content())).build()));
        default ->
            blocks.add(
                BetaToolResultBlockParam.Content.Block.ofText(
                    BetaTextBlockParam.builder().text(writeAsJson(c)).build()));
      }
    }
    return blocks;
  }

  private BetaContentBlockParam documentBlock(DocumentContent doc) {
    final var contentType = contentType(doc.document());
    return switch (classify(contentType)) {
      case IMAGE ->
          BetaContentBlockParam.ofImage(
              BetaImageBlockParam.builder()
                  .source(
                      BetaBase64ImageSource.builder()
                          .data(doc.document().asBase64())
                          .mediaType(BetaBase64ImageSource.MediaType.of(contentType))
                          .build())
                  .build());
      case PDF ->
          BetaContentBlockParam.ofDocument(
              BetaRequestDocumentBlock.builder().base64Source(doc.document().asBase64()).build());
      // TEXT-family documents inline as plain text; anything else (audio/video/unrecognized) has
      // no direct Anthropic block, so fall back to a JSON reference like the LangChain4j-routed
      // path.
      case TEXT ->
          BetaContentBlockParam.ofDocument(
              BetaRequestDocumentBlock.builder().textSource(decodeUtf8(doc.document())).build());
      case UNSUPPORTED ->
          BetaContentBlockParam.ofText(BetaTextBlockParam.builder().text(writeAsJson(doc)).build());
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
