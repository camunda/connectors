/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.chatmodel.provider.gemini;

import static io.camunda.connector.agenticai.aiagent.agent.AgentErrorCodes.ERROR_CODE_FAILED_MODEL_CALL;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.genai.JsonSerializable;
import com.google.genai.types.Blob;
import com.google.genai.types.FunctionResponse;
import com.google.genai.types.Part;
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
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.apache.hc.core5.http.ContentType;
import org.jspecify.annotations.Nullable;

/**
 * Converts the domain {@link Content} model to Gemini SDK {@link Part}s: {@link #toParts(List)} for
 * user/assistant message bodies, {@link #toFunctionResponseParts(List)} for tool-result bodies.
 *
 * <p>Request direction only. The reverse direction ({@link Part} to domain {@link Content}) is
 * handled inline by the response converter, not delegated to this class.
 */
public class GeminiContentConverter {

  /**
   * Metadata key under which the Gemini {@code thoughtSignature} is stored on a {@link
   * ReasoningContent}'s {@link Content#metadata()}, base64-encoded as a {@link String}.
   *
   * <p>The response converter writes this exact key when it first extracts the signature from a
   * live response; {@link #toParts(List)} reads it back here to restore the signature verbatim on
   * replay, which Gemini 3 requires for follow-up tool-calling requests.
   */
  public static final String THOUGHT_SIGNATURE_METADATA_KEY = "thoughtSignature";

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

  public GeminiContentConverter(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  public List<Part> toParts(List<Content> content) {
    final List<Part> parts = new ArrayList<>();
    for (final Content c : content) {
      switch (c) {
        case TextContent text -> parts.add(toTextPart(text));
        case DocumentContent doc -> parts.add(toDocumentPart(doc));
        case ObjectContent obj -> parts.add(Part.fromText(writeAsJson(obj.content())));
        case ReasoningContent rc -> parts.add(toThoughtPart(rc));
        case ProviderContent pc -> parts.add(toProviderPart(pc));
      }
    }
    return parts;
  }

  /**
   * Converts tool-result {@link Content} into {@link Part}s carrying the result payload.
   *
   * <p>The {@code functionResponse} parts built here deliberately omit {@code name}/{@code id}:
   * that correlating identity lives on the wrapping {@code ToolCallResultContent}, one level above
   * the individual {@link Content} items this method receives. The caller is expected to rebuild
   * each returned {@link FunctionResponse} via {@code toBuilder()} to add {@code name}/{@code id}
   * before sending the request. This assumes one {@link Part} per tool call (the current shape of
   * {@code ToolCallResultContent.content()}); if that list ever becomes multi-element for a single
   * tool call, the caller must merge the results into a single {@code functionResponse} rather than
   * emit sibling parts sharing one name/id.
   *
   * <p>Unlike {@link #toParts(List)}, a document here is flattened to a JSON reference rather than
   * embedded natively as {@code inlineData}: the document's actual bytes are already delivered to
   * the model elsewhere for tool results, so embedding it here as well would send it twice. Matches
   * {@code AnthropicContentConverter#toToolResultBlocks} and {@code
   * OpenAiContentConverter#toResponsesToolResultOutputItems}. Every branch still wraps its payload
   * in a {@code functionResponse}, the reference-only document included: a plain text {@link Part}
   * would carry no {@code name}/{@code id} to correlate with the preceding {@code functionCall}, so
   * a tool result whose only content is a document (or reasoning/provider content) would never
   * close out that call.
   */
  public List<Part> toFunctionResponseParts(List<Content> content) {
    final List<Part> parts = new ArrayList<>();
    for (final Content c : content) {
      switch (c) {
        case TextContent text -> parts.add(toFunctionResponsePart(text.text()));
        case ObjectContent obj -> parts.add(toFunctionResponsePart(obj.content()));
        case DocumentContent doc -> parts.add(toFunctionResponsePart(writeAsJson(doc.document())));
        default -> parts.add(toFunctionResponsePart(writeAsJson(c)));
      }
    }
    return parts;
  }

  private Part toFunctionResponsePart(Object value) {
    return Part.builder()
        .functionResponse(FunctionResponse.builder().response(Map.of("output", value)).build())
        .build();
  }

  /**
   * A {@code thoughtSignature} is not exclusive to thinking parts: Gemini attaches it to whichever
   * part the reasoning continuity belongs to, a plain answer text part included. The response
   * converter therefore records it on {@link TextContent#metadata()} under the same {@link
   * #THOUGHT_SIGNATURE_METADATA_KEY}, and it must be restored here verbatim or Gemini 3 rejects the
   * follow-up request that replays this message.
   */
  private Part toTextPart(TextContent text) {
    final Part part = Part.fromText(text.text());
    final byte @Nullable [] signature = thoughtSignature(text.metadata());
    return signature == null ? part : part.toBuilder().thoughtSignature(signature).build();
  }

  private Part toThoughtPart(ReasoningContent rc) {
    final Part.Builder builder = Part.builder().thought(true);
    if (rc.text() != null) {
      builder.text(rc.text());
    }
    final byte @Nullable [] signature = thoughtSignature(rc.metadata());
    if (signature != null) {
      builder.thoughtSignature(signature);
    }
    return builder.build();
  }

  private byte @Nullable [] thoughtSignature(@Nullable Map<String, Object> metadata) {
    if (metadata == null) {
      return null;
    }
    final Object value = metadata.get(THOUGHT_SIGNATURE_METADATA_KEY);
    if (value == null) {
      return null;
    }
    if (value instanceof byte[] bytes) {
      return bytes;
    }
    if (value instanceof String base64) {
      return Base64.getDecoder().decode(base64);
    }
    throw new ConnectorException(
        ERROR_CODE_FAILED_MODEL_CALL,
        "Unsupported %s metadata value type '%s'"
            .formatted(THOUGHT_SIGNATURE_METADATA_KEY, value.getClass().getSimpleName()));
  }

  /**
   * Converts a {@link ProviderContent} payload straight to a {@link Part} using the Gemini SDK's
   * own {@link JsonSerializable#objectMapper()} rather than the injected {@link #objectMapper} --
   * {@link Part} is built via an AutoValue builder whose fields are {@code Optional<...>}, which
   * requires the SDK's {@code Jdk8Module} registration to deserialize correctly (mirrors {@code
   * AnthropicContentConverter} using {@code com.anthropic.core.ObjectMappers.jsonMapper()} for the
   * equivalent lossless round-trip).
   */
  private Part toProviderPart(ProviderContent pc) {
    return JsonSerializable.objectMapper().convertValue(pc.payload(), Part.class);
  }

  private Part toDocumentPart(DocumentContent doc) {
    final var contentType = contentType(doc.document());
    return switch (classify(contentType)) {
      case IMAGE, PDF ->
          Part.builder()
              .inlineData(
                  Blob.builder()
                      .data(doc.document().asByteArray())
                      .mimeType(normalizedMimeType(contentType))
                      .build())
              .build();
      case TEXT -> Part.fromText(decodeUtf8(doc.document()));
      case UNSUPPORTED ->
          throw new ConnectorException(
              ERROR_CODE_FAILED_MODEL_CALL,
              "Unsupported content type '%s' for document with reference '%s'"
                  .formatted(contentType, doc.document().reference()));
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

  /**
   * Coarse content-type buckets driving {@link #toDocumentPart(DocumentContent)}'s choice of Gemini
   * part shape. Unknown/blank/unparseable types map conservatively to {@link #UNSUPPORTED}, which
   * fails the request.
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

  /**
   * Strips parameters (e.g. {@code ; charset=UTF-8}) from a content type, matching the
   * normalization {@link #classify(String)} already applies before comparing MIME types.
   */
  private static String normalizedMimeType(String contentType) {
    return ContentType.parse(contentType.trim().toLowerCase(Locale.ROOT)).getMimeType();
  }
}
