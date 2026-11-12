/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.document;

import io.camunda.connector.api.document.Document;
import java.io.IOException;
import org.apache.hc.core5.http.ContentType;
import org.jspecify.annotations.Nullable;

/**
 * Checks the content that a runtime tool asks for, in an agent instance that tracks its documents:
 * the document must have a content type that a model provider can render, and it must not exceed
 * the limit of its kind of content (see {@link DocumentContentLimits}).
 *
 * <p>The kinds are the ones the model providers tell apart ({@link DocumentMimeTypes}): text, PDF
 * and image. A document without a content type has no kind and is not loadable, because a provider
 * fails the model call for it. A document with content stays in the conversation until its message
 * leaves the window, so a document that a provider cannot render would fail every later model call.
 *
 * <p>The content type and the size are read from the metadata of the reference of the document,
 * which is known without reading the content. When the metadata has no size, at most one byte more
 * than the limit is read. A document that cannot be read is rejected as unreadable.
 *
 * <p>A runtime tool asks for content by naming a document (see {@code
 * RuntimeToolResult.Success#withDocumentContent}) and the agent checks it with this guard.
 */
public class DocumentContentGuard {

  private static final String PASS_THE_HANDLE =
      " If one of your tools can process the document, pass the handle to it.";

  private final DocumentContentLimits limits;

  public DocumentContentGuard(DocumentContentLimits limits) {
    this.limits = limits;
  }

  /** The outcome of the check of a document. */
  public sealed interface Verdict {

    /** The content of the document may be added to the conversation. */
    record Allowed() implements Verdict {}

    /** The content of the document must not be added to the conversation. */
    record Rejected(Rejection rejection) implements Verdict {}
  }

  /**
   * Why a document gets no content. The note for the model is a standalone sentence that holds the
   * content type and the size at most, never the id of the document and never the cause of a
   * failure.
   */
  public sealed interface Rejection {

    /**
     * The note the model reads in the tool result behind the name of the document, such as {@code
     * Documents of type X cannot be loaded.} followed by what to do instead.
     */
    String modelText();

    /** The failure behind the rejection, for the log; never shown to the model. */
    default @Nullable Throwable cause() {
      return null;
    }

    /**
     * The document has no content type or a content type that no provider can render.
     *
     * @param subject what cannot be loaded, as the subject of a sentence such as {@code Documents
     *     of type X}
     */
    record UnsupportedType(String subject) implements Rejection {

      @Override
      public String modelText() {
        return subject + " cannot be loaded." + PASS_THE_HANDLE;
      }
    }

    /**
     * The document is above the limit of its kind.
     *
     * @param size the size of the document in bytes, or {@code null} when it is not known because
     *     the document was only read up to the limit
     * @param limit the limit in bytes
     */
    record TooLarge(@Nullable Long size, long limit) implements Rejection {

      @Override
      public String modelText() {
        final var sizes =
            size != null
                ? "%s, limit %s".formatted(kilobytes(size), kilobytes(limit))
                : "limit " + kilobytes(limit);
        return "The document is too large to load (%s).%s".formatted(sizes, PASS_THE_HANDLE);
      }
    }

    /**
     * The document could not be read to find out its type or size; a later attempt can succeed.
     *
     * @param cause the failure
     */
    record Unreadable(Throwable cause) implements Rejection {

      @Override
      public String modelText() {
        return "The document could not be read.";
      }
    }
  }

  /** The kinds of content the providers tell apart. */
  private enum Kind {
    TEXT,
    PDF,
    IMAGE
  }

  /** Checks whether the content of the document may be added to the conversation. */
  public Verdict check(Document document) {
    try {
      return checkContent(document);
    } catch (IOException | RuntimeException e) {
      return new Verdict.Rejected(new Rejection.Unreadable(e));
    }
  }

  private Verdict checkContent(Document document) throws IOException {
    final var contentType = contentTypeOf(document);
    if (contentType == null) {
      return unsupported("A document without a content type");
    }
    final var parsed = DocumentMimeTypes.parse(contentType);
    if (parsed == null) {
      return unsupported("A document with a content type that cannot be read");
    }
    final var kind = kindOf(parsed);
    if (kind == null) {
      return unsupported("Documents of type %s".formatted(parsed.getMimeType()));
    }

    final var limit = limitFor(kind);
    final var size = sizeOf(document);
    if (size != null) {
      return size > limit ? tooLarge(size, limit) : allowed();
    }

    return exceeds(document, limit) ? tooLarge(null, limit) : allowed();
  }

  private static Verdict allowed() {
    return new Verdict.Allowed();
  }

  private static Verdict unsupported(String subject) {
    return new Verdict.Rejected(new Rejection.UnsupportedType(subject));
  }

  private static Verdict tooLarge(@Nullable Long size, long limit) {
    return new Verdict.Rejected(new Rejection.TooLarge(size, limit));
  }

  /** The size from the metadata of the document; none when it is not known (a negative size). */
  private static @Nullable Long sizeOf(Document document) {
    final var metadata = document.metadata();
    final var size = metadata != null ? metadata.getSize() : null;
    return size == null || size < 0 ? null : size;
  }

  /** The content type the model providers read: the one of the document metadata. */
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
