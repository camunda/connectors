/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.chatmodel.provider.bedrock;

import static io.camunda.connector.agenticai.aiagent.agent.AgentErrorCodes.ERROR_CODE_FAILED_MODEL_CALL;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.agenticai.aiagent.model.document.DocumentHandle;
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
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.apache.hc.core5.http.ContentType;
import org.jspecify.annotations.Nullable;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.DocumentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.DocumentFormat;
import software.amazon.awssdk.services.bedrockruntime.model.ImageBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ImageFormat;
import software.amazon.awssdk.services.bedrockruntime.model.ReasoningContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ToolResultContentBlock;

/**
 * Converts the domain {@link Content} model to Bedrock Converse SDK content blocks, both for
 * user/assistant message bodies ({@link ContentBlock}) and tool-result bodies ({@link
 * ToolResultContentBlock}).
 *
 * <p>Note: {@code software.amazon.awssdk.core.document.Document} (the generic AWS "any JSON value"
 * type used for tool-use input and {@code ToolResultContentBlock.json}) shares its simple name with
 * {@link Document}, the Camunda document abstraction backing {@link DocumentContent}. The latter is
 * imported normally; the former is always fully qualified below.
 */
public class BedrockConverseContentConverter {

  private static final Set<String> IMAGE_MIME_TYPES =
      Set.of("image/png", "image/jpeg", "image/gif", "image/webp");

  /**
   * Content types with a native Bedrock {@link DocumentFormat}. These are sent as-is ({@code
   * source.bytes}) rather than downgraded to a text fallback.
   */
  private static final Set<String> NATIVE_DOCUMENT_MIME_TYPES =
      Set.of(
          "application/pdf",
          "text/csv",
          "application/msword",
          "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
          "application/vnd.ms-excel",
          "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
          "text/html",
          "text/plain",
          "text/markdown");

  private final ObjectMapper objectMapper;

  public BedrockConverseContentConverter(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  public List<ContentBlock> toContentBlocks(List<Content> content) {
    final List<ContentBlock> blocks = new ArrayList<>();
    for (final Content c : content) {
      switch (c) {
        case TextContent text -> blocks.add(ContentBlock.fromText(text.text()));
        case DocumentContent doc -> blocks.add(documentContentBlock(doc));
        case ObjectContent obj -> blocks.add(ContentBlock.fromText(writeAsJson(obj.content())));
        case ReasoningContent rc -> blocks.add(toReasoningContentBlock(rc));
        case ProviderContent pc -> blocks.add(replayProviderContentBlock(pc));
      }
    }
    return blocks;
  }

  public List<ToolResultContentBlock> toToolResultBlocks(List<Content> content) {
    final List<ToolResultContentBlock> blocks = new ArrayList<>();
    for (final Content c : content) {
      switch (c) {
        case TextContent text -> blocks.add(ToolResultContentBlock.fromText(text.text()));
        case DocumentContent doc -> blocks.add(toToolResultDocumentBlock(doc));
        case ObjectContent obj ->
            blocks.add(ToolResultContentBlock.fromJson(toBedrockDocument(obj.content())));
        case ReasoningContent rc ->
            throw new ConnectorException(
                ERROR_CODE_FAILED_MODEL_CALL,
                "Unsupported content type 'reasoning' for tool result: Bedrock's "
                    + "ToolResultContentBlock has no reasoning-content member.");
        case ProviderContent pc ->
            throw new ConnectorException(
                ERROR_CODE_FAILED_MODEL_CALL,
                "Unsupported content type 'provider' for tool result: provider content is only "
                    + "ever produced from assistant message content, never from a tool result.");
      }
    }
    return blocks;
  }

  /**
   * Builds the {@link ContentBlock} for a {@link DocumentContent}, classifying by content type per
   * the design spec (images -> {@link ImageBlock}; native {@link DocumentFormat} types and
   * remaining text-ish types -> {@link DocumentBlock}; anything else throws).
   *
   * <p>Every {@link DocumentBlock} is named via {@link DocumentHandle#idFor(Document)}. This must
   * stay deterministic: LangChain4j's v1 Bedrock integration names every {@code DocumentBlock} with
   * {@code UUID.randomUUID()}, which is cache-hostile, since a fresh name per request means no
   * prefix containing a document ever matches and prompt caching silently never hits.
   */
  private ContentBlock documentContentBlock(DocumentContent doc) {
    final Document document = doc.document();
    final String contentType = contentType(document);
    final String mime = parseMimeType(contentType);
    return switch (classify(mime)) {
      case IMAGE ->
          ContentBlock.fromImage(
              ImageBlock.builder()
                  .format(imageFormat(Objects.requireNonNull(mime)))
                  .source(s -> s.bytes(SdkBytes.fromByteArray(document.asByteArray())))
                  .build());
      case DOCUMENT_NATIVE ->
          ContentBlock.fromDocument(
              DocumentBlock.builder()
                  .format(nativeDocumentFormat(Objects.requireNonNull(mime)))
                  .name(DocumentHandle.idFor(document))
                  .source(s -> s.bytes(SdkBytes.fromByteArray(document.asByteArray())))
                  .build());
      case TEXT_FALLBACK ->
          ContentBlock.fromDocument(
              DocumentBlock.builder()
                  .format(DocumentFormat.TXT)
                  .name(DocumentHandle.idFor(document))
                  .source(s -> s.text(decodeUtf8(document)))
                  .build());
      case UNSUPPORTED ->
          throw new ConnectorException(
              ERROR_CODE_FAILED_MODEL_CALL,
              "Unsupported content type '%s' for document with reference '%s'"
                  .formatted(contentType, document.reference()));
    };
  }

  private ToolResultContentBlock toToolResultDocumentBlock(DocumentContent doc) {
    final ContentBlock block = documentContentBlock(doc);
    final ImageBlock image = block.image();
    if (image != null) {
      return ToolResultContentBlock.fromImage(image);
    }
    final DocumentBlock document = block.document();
    if (document != null) {
      return ToolResultContentBlock.fromDocument(document);
    }
    // documentContentBlock() only ever returns an image or a document ContentBlock, or throws for
    // an unsupported content type, so this is unreachable.
    throw new IllegalStateException(
        "Unexpected content block produced for document content: " + block.type());
  }

  /**
   * Reconstructs the native {@code reasoningContent} block from a {@link ReasoningContent}. The
   * human-readable reasoning text is lifted out of the payload's {@code reasoningText.text} into
   * {@link ReasoningContent#text()} when a response is received, so it isn't persisted twice; here
   * it is merged back into the payload before replay via {@link BedrockSdkPojoCodec}, so the
   * resulting block is byte-identical to the one originally returned by the API. This is mandatory,
   * not optional: AWS requires the signature and all previous reasoning blocks to be included
   * verbatim in subsequent Converse requests.
   */
  private ContentBlock toReasoningContentBlock(ReasoningContent rc) {
    final Map<String, Object> payload = asPayloadMap(rc.payload(), "reasoning content");
    if (rc.text() != null) {
      final Map<String, Object> reasoningText = new LinkedHashMap<>();
      if (payload.get("reasoningText") instanceof Map<?, ?> existing) {
        existing.forEach((k, v) -> reasoningText.put(String.valueOf(k), v));
      }
      reasoningText.put("text", rc.text());
      payload.put("reasoningText", reasoningText);
    }
    final ReasoningContentBlock block =
        BedrockSdkPojoCodec.replay(payload, ReasoningContentBlock::builder);
    return ContentBlock.fromReasoningContent(block);
  }

  /**
   * Replays a {@code bedrock} {@link ProviderContent} payload back into its native {@link
   * ContentBlock} via the generic codec (design spec &sect;5.4, {@link BedrockSdkPojoCodec}). Any
   * {@code ContentBlock} member beyond the typed three ({@code text}, {@code toolUse}, {@code
   * reasoningContent}) round-trips through this mechanism, never silently dropped.
   */
  private ContentBlock replayProviderContentBlock(ProviderContent pc) {
    final Map<String, Object> payload = asPayloadMap(pc.payload(), "provider content");
    return BedrockSdkPojoCodec.replay(payload, ContentBlock::builder);
  }

  private static Map<String, Object> asPayloadMap(@Nullable Object payload, String description) {
    if (!(payload instanceof Map<?, ?> map)) {
      throw new ConnectorException(
          ERROR_CODE_FAILED_MODEL_CALL,
          "Expected %s payload to be a Map, got '%s'"
              .formatted(
                  description, payload == null ? "null" : payload.getClass().getSimpleName()));
    }
    final Map<String, Object> result = new LinkedHashMap<>();
    map.forEach((k, v) -> result.put(String.valueOf(k), v));
    return result;
  }

  /**
   * Normalizes an arbitrary Java value (as produced by {@code
   * ToolCallResultContent.contentFromObject}, i.e. already-deserialized JSON: maps, lists, strings,
   * numbers, booleans, null, or an arbitrary POJO) into the AWS SDK's generic {@code Document}
   * value tree, used by {@code ToolResultContentBlock.json}.
   */
  private software.amazon.awssdk.core.document.Document toBedrockDocument(@Nullable Object value) {
    if (value == null) {
      return software.amazon.awssdk.core.document.Document.fromNull();
    }
    if (value instanceof Boolean bool) {
      return software.amazon.awssdk.core.document.Document.fromBoolean(bool);
    }
    if (value instanceof String str) {
      return software.amazon.awssdk.core.document.Document.fromString(str);
    }
    if (value instanceof Number number) {
      return software.amazon.awssdk.core.document.Document.fromNumber(number.toString());
    }
    if (value instanceof Map<?, ?> map) {
      final Map<String, software.amazon.awssdk.core.document.Document> result =
          new LinkedHashMap<>();
      map.forEach((k, v) -> result.put(String.valueOf(k), toBedrockDocument(v)));
      return software.amazon.awssdk.core.document.Document.fromMap(result);
    }
    if (value instanceof List<?> list) {
      final List<software.amazon.awssdk.core.document.Document> result =
          new ArrayList<>(list.size());
      for (final Object element : list) {
        result.add(toBedrockDocument(element));
      }
      return software.amazon.awssdk.core.document.Document.fromList(result);
    }
    // Arbitrary POJO: normalize to its JSON tree shape (Map/List/scalar) via Jackson and retry.
    return toBedrockDocument(objectMapper.convertValue(value, Object.class));
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
   * Coarse content-type buckets driving {@link #documentContentBlock(DocumentContent)}'s choice of
   * Bedrock block shape. Unknown/blank/unparseable types map conservatively to {@link
   * #UNSUPPORTED}, which fails the request.
   */
  private enum DocumentBlockKind {
    IMAGE,
    DOCUMENT_NATIVE,
    TEXT_FALLBACK,
    UNSUPPORTED
  }

  private static DocumentBlockKind classify(@Nullable String mime) {
    if (mime == null) {
      return DocumentBlockKind.UNSUPPORTED;
    }
    if (IMAGE_MIME_TYPES.contains(mime)) {
      return DocumentBlockKind.IMAGE;
    }
    if (NATIVE_DOCUMENT_MIME_TYPES.contains(mime)) {
      return DocumentBlockKind.DOCUMENT_NATIVE;
    }
    if (isTextIsh(mime)) {
      return DocumentBlockKind.TEXT_FALLBACK;
    }
    return DocumentBlockKind.UNSUPPORTED;
  }

  private static boolean isTextIsh(String mime) {
    return mime.startsWith("text/")
        || mime.equals("application/json")
        || mime.equals("application/xml")
        || mime.equals("application/yaml")
        || mime.equals("application/x-yaml")
        || mime.endsWith("+json")
        || mime.endsWith("+xml");
  }

  private static @Nullable String parseMimeType(String contentType) {
    if (contentType.isBlank()) {
      return null;
    }
    final ContentType parsed;
    try {
      parsed = ContentType.parse(contentType.trim().toLowerCase(Locale.ROOT));
    } catch (RuntimeException e) {
      return null;
    }
    return parsed != null ? parsed.getMimeType() : null;
  }

  private static ImageFormat imageFormat(String mime) {
    return switch (mime) {
      case "image/png" -> ImageFormat.PNG;
      case "image/jpeg" -> ImageFormat.JPEG;
      case "image/gif" -> ImageFormat.GIF;
      case "image/webp" -> ImageFormat.WEBP;
      default ->
          throw new IllegalStateException(
              "Unexpected image content type after classification: " + mime);
    };
  }

  private static DocumentFormat nativeDocumentFormat(String mime) {
    return switch (mime) {
      case "application/pdf" -> DocumentFormat.PDF;
      case "text/csv" -> DocumentFormat.CSV;
      case "application/msword" -> DocumentFormat.DOC;
      case "application/vnd.openxmlformats-officedocument.wordprocessingml.document" ->
          DocumentFormat.DOCX;
      case "application/vnd.ms-excel" -> DocumentFormat.XLS;
      case "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" ->
          DocumentFormat.XLSX;
      case "text/html" -> DocumentFormat.HTML;
      case "text/plain" -> DocumentFormat.TXT;
      case "text/markdown" -> DocumentFormat.MD;
      default ->
          throw new IllegalStateException(
              "Unexpected native document content type after classification: " + mime);
    };
  }
}
