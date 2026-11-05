/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.document;

import io.camunda.connector.api.document.Document;
import io.camunda.connector.api.document.DocumentReference.CamundaDocumentReference;
import io.camunda.connector.api.document.DocumentReference.ExternalDocumentReference;
import java.io.IOException;
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
 *
 * <p>The size is read from the reference metadata without reading the document; when the metadata
 * has none, at most one byte more than the limit is read.
 *
 * <p>A runtime tool asks for content by naming a document (see {@code
 * RuntimeToolResult.Success#withDocumentContent}) and the agent checks it with this guard.
 */
public final class DocumentContentGuard {

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
   * Why a document gets no content. The text for the model holds the content type and the size at
   * most, never the cause of a failure.
   */
  public sealed interface Rejection {

    /**
     * What the model reads about the document: a predicate that follows {@code The document <id>},
     * such as {@code is of type X which cannot be loaded into the conversation}.
     */
    String modelText();

    /** The failure behind the rejection, for the log; never shown to the model. */
    default @Nullable Throwable cause() {
      return null;
    }

    /** The document is an external one. */
    record External() implements Rejection {

      @Override
      public String modelText() {
        return "is an external document, which cannot be loaded into the conversation"
            + PASS_THE_TAG;
      }
    }

    /**
     * The document has no content type or a content type that no provider can render.
     *
     * @param problem the problem as a predicate of the document
     */
    record UnsupportedType(String problem) implements Rejection {

      @Override
      public String modelText() {
        return problem + PASS_THE_TAG;
      }
    }

    /**
     * The document is above the limit of its kind.
     *
     * @param sizeText the size of the document, or what is known of it
     * @param limit the limit in bytes
     */
    record TooLarge(String sizeText, long limit) implements Rejection {

      @Override
      public String modelText() {
        return "%s, too large to load into the conversation (limit %s)%s"
            .formatted(sizeText, kilobytes(limit), PASS_THE_TAG);
      }
    }

    /**
     * The document could not be read to find out its size; a later attempt can succeed.
     *
     * @param cause the failure
     */
    record Unreadable(Throwable cause) implements Rejection {

      @Override
      public String modelText() {
        return "could not be read, so its content is not loaded";
      }
    }
  }

  private static final String PASS_THE_TAG = "; pass its tag to a tool that processes it";

  /** The kinds of content the providers tell apart. */
  private enum Kind {
    TEXT,
    PDF,
    IMAGE
  }

  /** Checks whether the content of the document may be added to the conversation. */
  public Verdict check(Document document) {
    return switch (document.reference()) {
      case ExternalDocumentReference ignored -> new Verdict.Rejected(new Rejection.External());
      case null, default -> checkContent(document);
    };
  }

  private Verdict checkContent(Document document) {
    final var contentType = contentTypeOf(document);
    if (contentType == null) {
      return unsupported("has no content type, so it cannot be loaded into the conversation");
    }
    final var parsed = DocumentMimeTypes.parse(contentType);
    if (parsed == null) {
      return unsupported(
          "has a content type that cannot be read, so it cannot be loaded into the conversation");
    }
    final var kind = kindOf(parsed);
    if (kind == null) {
      return unsupported(
          "is of type %s which cannot be loaded into the conversation"
              .formatted(parsed.getMimeType()));
    }

    final var limit = limitFor(kind);
    final var size = sizeOf(document);
    if (size != null) {
      return size > limit ? tooLarge("is " + kilobytes(size), limit) : allowed();
    }

    try {
      return exceeds(document, limit)
          ? tooLarge("is larger than " + kilobytes(limit), limit)
          : allowed();
    } catch (IOException | RuntimeException e) {
      return new Verdict.Rejected(new Rejection.Unreadable(e));
    }
  }

  private static Verdict allowed() {
    return new Verdict.Allowed();
  }

  private static Verdict unsupported(String problem) {
    return new Verdict.Rejected(new Rejection.UnsupportedType(problem));
  }

  private static Verdict tooLarge(String sizeText, long limit) {
    return new Verdict.Rejected(new Rejection.TooLarge(sizeText, limit));
  }

  /** The size from the metadata of a Camunda document, which is known without reading it. */
  private static @Nullable Long sizeOf(Document document) {
    return switch (document.reference()) {
      case CamundaDocumentReference ignored -> {
        final var metadata = document.metadata();
        yield metadata != null ? metadata.getSize() : null;
      }
      case null, default -> null;
    };
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
