/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.document;

import io.camunda.connector.api.document.Document;
import io.camunda.connector.api.document.DocumentReference.ExternalDocumentReference;
import java.io.IOException;
import java.util.Optional;
import org.apache.hc.core5.http.ContentType;
import org.jspecify.annotations.Nullable;

/**
 * The guard every addition of document content to the conversation passes on request of a tool or
 * of the process: the document must not be an external one, it must have a content type that a
 * model provider can render, and it must not exceed the limit of its kind of content (see {@link
 * DocumentContentLimits}).
 *
 * <p>The kinds are the ones the model providers tell apart ({@link DocumentMimeTypes}): text, PDF
 * and image. A document without a content type has no kind and is not loadable, because a provider
 * fails the model call for it. A document with content stays in the conversation until its message
 * leaves the window, so a document that a provider cannot render would fail every later model call.
 *
 * <p>The content of an external document, one that is a URL, is never added on request of a tool:
 * its content type and size are only known after the runtime downloaded the whole body, so the
 * guard could neither check nor bound them. The model passes its tag to a tool that processes it.
 * The content of an external document in a user prompt is not guarded: the model provider reads it
 * as it reads every document of a prompt.
 *
 * <p>The size is read from the reference metadata without reading the document; when the metadata
 * has none, at most one byte more than the limit is read.
 *
 * <p>A runtime tool asks for content by naming a document (see {@code
 * RuntimeToolResult#withDocumentContent}) and the agent checks it with this guard. The agent checks
 * the documents of a user prompt with the delivery {@code content} the same way, so the same
 * documents pass whoever asks.
 */
public final class DocumentContentGuard {

  private final DocumentContentLimits limits;

  public DocumentContentGuard(DocumentContentLimits limits) {
    this.limits = limits;
  }

  /**
   * Why a document gets no content.
   *
   * @param reason the kind of the problem
   * @param problem the problem as a predicate of the document, such as {@code is of type X which
   *     cannot be loaded into the conversation}; it is shown to the model, so it holds the content
   *     type and the size at most
   * @param cause the failure behind {@link Reason#UNREADABLE}, otherwise {@code null}; it is for
   *     the log and never shown to the model
   */
  public record Rejection(Reason reason, String problem, @Nullable Throwable cause) {

    /** The kinds of problems of a document. */
    public enum Reason {
      /** The document is an external one. */
      EXTERNAL,
      /** The document has no content type or a content type that no provider can render. */
      UNSUPPORTED_TYPE,
      /** The document is above the limit of its kind. */
      TOO_LARGE,
      /** The document could not be read to find out its size. */
      UNREADABLE
    }

    /**
     * Whether a later attempt for the same document can succeed. A document that is of an
     * unsupported type or too large stays so.
     */
    public boolean retryable() {
      return reason == Reason.UNREADABLE;
    }
  }

  /** The kinds of content the providers tell apart. */
  private enum Kind {
    TEXT,
    PDF,
    IMAGE
  }

  /**
   * Returns why the content of the document must not be added to the conversation, or empty if it
   * may.
   */
  public Optional<Rejection> check(Document document) {
    if (document.reference() instanceof ExternalDocumentReference) {
      return Optional.of(
          new Rejection(
              Rejection.Reason.EXTERNAL,
              "is an external document, which cannot be loaded into the conversation",
              null));
    }

    final var contentType = contentTypeOf(document);
    if (contentType == null) {
      return Optional.of(
          new Rejection(
              Rejection.Reason.UNSUPPORTED_TYPE,
              "has no content type, so it cannot be loaded into the conversation",
              null));
    }
    final var parsed = DocumentMimeTypes.parse(contentType);
    if (parsed == null) {
      return Optional.of(
          new Rejection(
              Rejection.Reason.UNSUPPORTED_TYPE,
              "has a content type that cannot be read, so it cannot be loaded into the conversation",
              null));
    }
    final var kind = kindOf(parsed);
    if (kind == null) {
      return Optional.of(
          new Rejection(
              Rejection.Reason.UNSUPPORTED_TYPE,
              "is of type %s which cannot be loaded into the conversation"
                  .formatted(parsed.getMimeType()),
              null));
    }

    final var limit = limitFor(kind);
    final var size = DocumentHandle.sizeFor(document);
    if (size != null) {
      return size > limit
          ? Optional.of(tooLarge("is " + kilobytes(size), limit))
          : Optional.empty();
    }

    try {
      return exceeds(document, limit)
          ? Optional.of(tooLarge("is larger than " + kilobytes(limit), limit))
          : Optional.empty();
    } catch (IOException | RuntimeException e) {
      return Optional.of(
          new Rejection(Rejection.Reason.UNREADABLE, "could not be read to check its size", e));
    }
  }

  /**
   * The content type the model providers read: the one of the document metadata, which a document
   * of a Camunda or inline reference has without reading its content.
   */
  private static @Nullable String contentTypeOf(Document document) {
    final var metadata = document.metadata();
    final var contentType = metadata != null ? metadata.getContentType() : null;
    return contentType == null || contentType.isBlank() ? null : contentType;
  }

  private static @Nullable Kind kindOf(ContentType contentType) {
    if (DocumentMimeTypes.isImage(contentType)) {
      return Kind.IMAGE;
    }
    if (DocumentMimeTypes.isPdf(contentType)) {
      return Kind.PDF;
    }
    if (DocumentMimeTypes.isTextIsh(contentType)) {
      return Kind.TEXT;
    }
    return null;
  }

  private long limitFor(Kind kind) {
    return switch (kind) {
      case TEXT -> limits.textBytes();
      case PDF -> limits.pdfBytes();
      case IMAGE -> limits.imageBytes();
    };
  }

  private static Rejection tooLarge(String sizeText, long limit) {
    return new Rejection(
        Rejection.Reason.TOO_LARGE,
        "%s, too large to load into the conversation (limit %s)"
            .formatted(sizeText, kilobytes(limit)),
        null);
  }

  /** Reads at most one byte more than the limit to find out whether the document exceeds it. */
  private static boolean exceeds(Document document, long limit) throws IOException {
    try (final var in = document.asInputStream()) {
      return in.readNBytes((int) Math.min(limit + 1, Integer.MAX_VALUE)).length > limit;
    }
  }

  private static String kilobytes(long bytes) {
    return ((bytes + 1023) / 1024) + " KB";
  }
}
