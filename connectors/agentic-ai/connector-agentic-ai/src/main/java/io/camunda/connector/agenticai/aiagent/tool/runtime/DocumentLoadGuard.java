/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.tool.runtime;

import io.camunda.connector.agenticai.aiagent.model.document.DocumentHandle;
import io.camunda.connector.api.document.Document;
import java.io.IOException;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * The guard every load of a document content into the conversation passes: the content type must be
 * on the {@link DocumentLoadLimits allowlist} and the document must not exceed the limit of its
 * type. The size is read from the reference metadata without reading the document; when the
 * metadata has none, at most one byte more than the limit is read.
 *
 * <p>The model triggers a load with {@code c8__get_document}, the agent triggers it for the
 * documents of a user prompt or of an inline tool result. All of them use this guard, so the same
 * documents are loadable whoever asks.
 */
public final class DocumentLoadGuard {

  private DocumentLoadGuard() {}

  /**
   * Why a document is not loaded.
   *
   * @param reason the kind of the problem
   * @param problem the problem as a predicate of the document, such as {@code is of type X which
   *     cannot be loaded into the conversation}
   * @param cause the failure behind {@link Reason#UNREADABLE}, otherwise {@code null}
   */
  public record Rejection(Reason reason, String problem, @Nullable Throwable cause) {

    /** The kinds of problems of a document. */
    public enum Reason {
      /** The content type is outside the allowlist. */
      UNSUPPORTED_TYPE,
      /** The document is above the limit of its content type. */
      TOO_LARGE,
      /** The document could not be read to find out its size. */
      UNREADABLE
    }

    /**
     * Whether a later attempt to load the same document can succeed. A document that is of an
     * unsupported type or too large stays so.
     */
    public boolean retryable() {
      return reason == Reason.UNREADABLE;
    }
  }

  /** Returns why the document must not be loaded into the conversation, or empty if it may. */
  public static Optional<Rejection> check(Document document) {
    final var contentType = DocumentHandle.contentTypeFor(document);
    if (!DocumentLoadLimits.isLoadable(contentType)) {
      return Optional.of(
          new Rejection(
              Rejection.Reason.UNSUPPORTED_TYPE,
              "is of type %s which cannot be loaded into the conversation".formatted(contentType),
              null));
    }

    final var limit = DocumentLoadLimits.limitFor(contentType);
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
          new Rejection(
              Rejection.Reason.UNREADABLE,
              "could not be read to check its size: " + e.getMessage(),
              e));
    }
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
      return in.readNBytes((int) limit + 1).length > limit;
    }
  }

  private static String kilobytes(long bytes) {
    return ((bytes + 1023) / 1024) + " KB";
  }
}
