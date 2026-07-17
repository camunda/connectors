/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.framework.anthropic;

import com.anthropic.core.ObjectMappers;
import com.anthropic.models.beta.messages.BetaBase64ImageSource;
import com.anthropic.models.beta.messages.BetaContentBlockParam;
import com.anthropic.models.beta.messages.BetaImageBlockParam;
import com.anthropic.models.beta.messages.BetaRequestDocumentBlock;
import com.anthropic.models.beta.messages.BetaTextBlockParam;
import com.anthropic.models.beta.messages.BetaToolResultBlockParam;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.agenticai.aiagent.framework.multimodal.DocumentModality;
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

/**
 * Converts the domain {@link Content} model to Anthropic SDK content blocks, both for
 * user/assistant message bodies ({@link BetaContentBlockParam}) and tool-result bodies ({@link
 * BetaToolResultBlockParam.Content.Block}).
 *
 * <p>Uses the <strong>beta</strong> messages client types (rather than the stable {@code
 * com.anthropic.models.messages} family) since the beta client is required for upcoming Skills
 * support; this migration is otherwise behavior-identical.
 */
public class AnthropicContentConverter {

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
        // Reasoning content is re-emitted unconditionally (no capability gate; see spec §4b) as
        // long as a raw providerPayload is present. A null payload (e.g. reasoning content
        // produced by the LangChain4J-routed path, which has no raw block to preserve) has no
        // wire representation to replay; skip it so history replay stays valid.
        case ReasoningContent rc -> {
          if (rc.providerPayload() != null) {
            blocks.add(
                ObjectMappers.jsonMapper()
                    .convertValue(rc.providerPayload(), BetaContentBlockParam.class));
          }
        }
        case ProviderContent pc -> {
          // A null payload (reachable via the public constructor) has no wire
          // representation to replay; skip it instead of emitting a null content block.
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
    final var modality = DocumentModality.fromDocument(doc.document());
    final var contentType = contentType(doc.document());
    return switch (modality) {
      case IMAGE ->
          BetaContentBlockParam.ofImage(
              BetaImageBlockParam.builder()
                  .source(
                      BetaBase64ImageSource.builder()
                          .data(doc.document().asBase64())
                          .mediaType(BetaBase64ImageSource.MediaType.of(contentType))
                          .build())
                  .build());
      case DOCUMENT ->
          BetaContentBlockParam.ofDocument(
              BetaRequestDocumentBlock.builder().base64Source(doc.document().asBase64()).build());
      // TEXT-family documents inline as plain text; AUDIO/VIDEO have no direct
      // Anthropic block yet, so fall back to a JSON reference like the LangChain4j-routed path.
      case TEXT ->
          BetaContentBlockParam.ofDocument(
              BetaRequestDocumentBlock.builder().textSource(decodeUtf8(doc.document())).build());
      default ->
          BetaContentBlockParam.ofText(BetaTextBlockParam.builder().text(writeAsJson(doc)).build());
    };
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
