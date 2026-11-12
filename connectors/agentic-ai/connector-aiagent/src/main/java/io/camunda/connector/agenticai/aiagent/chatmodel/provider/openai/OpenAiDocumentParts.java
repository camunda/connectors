/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.chatmodel.provider.openai;

import static io.camunda.connector.agenticai.aiagent.agent.AgentErrorCodes.ERROR_CODE_FAILED_MODEL_CALL;

import io.camunda.connector.agenticai.aiagent.chatmodel.provider.DocumentMimeTypes;
import io.camunda.connector.agenticai.aiagent.model.message.content.DocumentContent;
import io.camunda.connector.api.document.Document;
import io.camunda.connector.api.error.ConnectorException;
import java.nio.charset.StandardCharsets;
import org.apache.hc.core5.http.ContentType;
import org.jspecify.annotations.Nullable;

/**
 * Low-level document-part building blocks shared by both OpenAI SDK families: {@link
 * OpenAiContentConverter} (Responses) and the Chat Completions family's content-chunk strategies
 * (see {@code io.camunda....openai.family.completions.OpenAiCompletionsContentChunkStrategy}).
 * Public so the latter, in a different package, can reuse it.
 */
public final class OpenAiDocumentParts {

  private static final String DEFAULT_FILE_NAME = "document";

  private OpenAiDocumentParts() {}

  /**
   * Coarse content-type buckets driving a document-part builder's choice of part shape.
   * Unknown/blank/unparseable types map conservatively to {@link #UNSUPPORTED}, which fails the
   * request.
   */
  public enum Kind {
    IMAGE,
    PDF,
    TEXT,
    UNSUPPORTED
  }

  public static Kind classify(@Nullable ContentType contentType) {
    if (contentType == null) {
      return Kind.UNSUPPORTED;
    }
    if (DocumentMimeTypes.isImage(contentType)) {
      return Kind.IMAGE;
    }
    if (DocumentMimeTypes.isPdf(contentType)) {
      return Kind.PDF;
    }
    if (DocumentMimeTypes.isTextIsh(contentType)) {
      return Kind.TEXT;
    }
    return Kind.UNSUPPORTED;
  }

  public static String dataUri(String contentType, Document document) {
    return "data:" + contentType + ";base64," + document.asBase64();
  }

  public static String fileName(Document document) {
    final var metadata = document.metadata();
    final var name = metadata != null ? metadata.getFileName() : null;
    return name != null ? name : DEFAULT_FILE_NAME;
  }

  public static String decodeUtf8(Document document) {
    return new String(document.asByteArray(), StandardCharsets.UTF_8);
  }

  public static ConnectorException unsupportedContentType(String contentType, DocumentContent doc) {
    return new ConnectorException(
        ERROR_CODE_FAILED_MODEL_CALL,
        "Unsupported content type '%s' for document with reference '%s'"
            .formatted(contentType, doc.document().reference()));
  }
}
