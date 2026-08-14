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
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.DocumentMimeTypes;
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
import java.util.Map;
import java.util.Objects;
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

  private static final Map<ContentType, ImageFormat> IMAGE_FORMATS =
      Map.of(
          ContentType.IMAGE_PNG, ImageFormat.PNG,
          ContentType.IMAGE_JPEG, ImageFormat.JPEG,
          ContentType.IMAGE_GIF, ImageFormat.GIF,
          ContentType.IMAGE_WEBP, ImageFormat.WEBP);

  /**
   * Content types with a native Bedrock {@link DocumentFormat}. These are sent as-is ({@code
   * source.bytes}) rather than downgraded to a text fallback.
   */
  private static final Map<ContentType, DocumentFormat> NATIVE_DOCUMENT_FORMATS =
      Map.ofEntries(
          Map.entry(ContentType.APPLICATION_PDF, DocumentFormat.PDF),
          Map.entry(ContentType.create("text/csv"), DocumentFormat.CSV),
          Map.entry(ContentType.create("application/msword"), DocumentFormat.DOC),
          Map.entry(
              ContentType.create(
                  "application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
              DocumentFormat.DOCX),
          Map.entry(ContentType.create("application/vnd.ms-excel"), DocumentFormat.XLS),
          Map.entry(
              ContentType.create(
                  "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
              DocumentFormat.XLSX),
          Map.entry(ContentType.TEXT_HTML, DocumentFormat.HTML),
          Map.entry(ContentType.TEXT_PLAIN, DocumentFormat.TXT),
          Map.entry(ContentType.create("text/markdown"), DocumentFormat.MD));

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

  /**
   * Converts tool-result content to Converse {@link ToolResultContentBlock}s.
   *
   * <p>A document is always flattened to the same serialized JSON reference an embedded document
   * already gets here (see {@link BedrockConverseDocuments}), never embedded as a native {@code
   * DocumentBlock}: the composer echoes every tool-result document in a separate synthetic user
   * message, which is the one place its real bytes are delivered. Embedding it here as well would
   * send it twice and trip Converse's duplicate-document-name validation.
   */
  public List<ToolResultContentBlock> toToolResultBlocks(List<Content> content) {
    final List<ToolResultContentBlock> blocks = new ArrayList<>();
    for (final Content c : content) {
      switch (c) {
        case TextContent text -> blocks.add(ToolResultContentBlock.fromText(text.text()));
        case DocumentContent doc ->
            blocks.add(ToolResultContentBlock.fromJson(toBedrockDocument(doc.document())));
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
   * Builds the {@link ContentBlock} for a {@link DocumentContent}, classifying by content type:
   * images -> {@link ImageBlock}; native {@link DocumentFormat} types and remaining text-ish types
   * -> {@link DocumentBlock}; anything else throws.
   *
   * <p>Every {@link DocumentBlock} is named via {@link DocumentHandle#idFor(Document)}. That name
   * must stay deterministic across requests: a fresh name each time means no prefix containing a
   * document ever matches, and prompt caching silently never hits.
   */
  private ContentBlock documentContentBlock(DocumentContent doc) {
    final Document document = doc.document();
    final String rawContentType = DocumentMimeTypes.requireContentType(document);
    final ContentType contentType = DocumentMimeTypes.parse(rawContentType);
    return switch (classify(contentType)) {
      case IMAGE ->
          ContentBlock.fromImage(
              ImageBlock.builder()
                  .format(lookup(IMAGE_FORMATS, Objects.requireNonNull(contentType)))
                  .source(s -> s.bytes(SdkBytes.fromByteArray(document.asByteArray())))
                  .build());
      case DOCUMENT_NATIVE ->
          ContentBlock.fromDocument(
              DocumentBlock.builder()
                  .format(lookup(NATIVE_DOCUMENT_FORMATS, Objects.requireNonNull(contentType)))
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
                  .formatted(rawContentType, document.reference()));
    };
  }

  /**
   * Reconstructs the native {@code reasoningContent} block from a {@link ReasoningContent}. The
   * human-readable reasoning text is lifted out of the payload's {@code reasoningText.text} into
   * {@link ReasoningContent#text()} when a response is received, so it isn't persisted twice; here
   * it is merged back into the payload before replay via {@link BedrockConverseSdkPojoCodec}, so
   * the resulting block is byte-identical to the one originally returned by the API. This is
   * mandatory, not optional: AWS requires the signature and all previous reasoning blocks to be
   * included verbatim in subsequent Converse requests.
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
        BedrockConverseSdkPojoCodec.replay(payload, ReasoningContentBlock::builder);
    return ContentBlock.fromReasoningContent(block);
  }

  /**
   * Replays a {@code bedrock} {@link ProviderContent} payload back into its native {@link
   * ContentBlock} via the generic {@link BedrockConverseSdkPojoCodec}. Any {@code ContentBlock}
   * member beyond the typed three ({@code text}, {@code toolUse}, {@code reasoningContent})
   * round-trips through this mechanism, never silently dropped.
   */
  private ContentBlock replayProviderContentBlock(ProviderContent pc) {
    final Map<String, Object> payload = asPayloadMap(pc.payload(), "provider content");
    return BedrockConverseSdkPojoCodec.replay(payload, ContentBlock::builder);
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
   * value tree, used by {@code ToolResultContentBlock.json}. See {@link BedrockConverseDocuments}
   * for the conversion policy shared with the other Bedrock Converse converters.
   */
  private software.amazon.awssdk.core.document.Document toBedrockDocument(@Nullable Object value) {
    return BedrockConverseDocuments.toDocument(value, objectMapper);
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

  private static DocumentBlockKind classify(@Nullable ContentType contentType) {
    if (contentType == null) {
      return DocumentBlockKind.UNSUPPORTED;
    }
    if (DocumentMimeTypes.isImage(contentType)) {
      return DocumentBlockKind.IMAGE;
    }
    if (lookup(NATIVE_DOCUMENT_FORMATS, contentType) != null) {
      return DocumentBlockKind.DOCUMENT_NATIVE;
    }
    if (DocumentMimeTypes.isTextIsh(contentType)) {
      return DocumentBlockKind.TEXT_FALLBACK;
    }
    return DocumentBlockKind.UNSUPPORTED;
  }

  /**
   * Looks a content type up in one of the format tables via {@link ContentType#isSameMimeType}
   * rather than map equality, so parameters and casing on the incoming type don't cause a miss.
   */
  private static <T> @Nullable T lookup(Map<ContentType, T> formats, ContentType contentType) {
    return formats.entrySet().stream()
        .filter(entry -> contentType.isSameMimeType(entry.getKey()))
        .map(Map.Entry::getValue)
        .findFirst()
        .orElse(null);
  }
}
