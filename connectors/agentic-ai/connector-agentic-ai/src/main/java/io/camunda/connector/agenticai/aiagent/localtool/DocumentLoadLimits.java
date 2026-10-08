/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.localtool;

import java.util.Locale;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * The content types and size limits of a document that {@code c8__get_document} loads into the
 * conversation.
 *
 * <p>Only a conservative allowlist of content types is loadable: the text-based types, {@code
 * application/pdf} and the images {@code image/png}, {@code image/jpeg}, {@code image/gif} and
 * {@code image/webp}. A loaded document stays in the conversation until its message leaves the
 * window, so a type that the model provider cannot render would fail every later model call; any
 * other type is rejected before the document is loaded. The allowlist is provider-independent: a
 * provider-specific capability matrix is an open point, and a provider without support for a listed
 * type, a PDF for example, still fails at the model call.
 *
 * <p>The limit depends on the content type: a text-based document costs a roughly predictable
 * number of tokens per byte, so the limit is small, a PDF or an image gets a higher one. A document
 * whose content type is not known without reading it, such as an external one, is treated as
 * text-based: it is loadable and gets the stricter text limit.
 *
 * <p>Known limitation: bytes are a poor proxy for the tokens a document costs. A PDF of 214 KB can
 * cost about 230,000 input tokens, depending on the pages and the provider. A guard based on tokens
 * is an open point.
 */
final class DocumentLoadLimits {

  /** The limit in bytes of a text-based document: 256 KB. */
  static final long TEXT_LIMIT_BYTES = 256L * 1024;

  /** The limit in bytes of any other document, such as a PDF or an image: 10 MB. */
  static final long BINARY_LIMIT_BYTES = 10L * 1024 * 1024;

  private static final Set<String> TEXT_APPLICATION_TYPES =
      Set.of(
          "application/json",
          "application/xml",
          "application/yaml",
          "application/x-yaml",
          "application/csv",
          "application/x-ndjson",
          "application/javascript",
          "application/sql",
          "application/toml");

  private static final Set<String> BINARY_TYPES =
      Set.of("application/pdf", "image/png", "image/jpeg", "image/gif", "image/webp");

  private static final Set<String> TEXT_SUFFIXES = Set.of("+json", "+xml", "+yaml");

  private DocumentLoadLimits() {}

  /** Whether a document of the content type, which may be unknown, can be loaded. */
  static boolean isLoadable(@Nullable String contentType) {
    return isTextBased(contentType) || BINARY_TYPES.contains(mediaType(contentType));
  }

  /**
   * The limit in bytes for a loadable document of the content type, which may be unknown. The
   * result for a type that is not {@link #isLoadable loadable} is the binary limit.
   */
  static long limitFor(@Nullable String contentType) {
    return isTextBased(contentType) ? TEXT_LIMIT_BYTES : BINARY_LIMIT_BYTES;
  }

  private static boolean isTextBased(@Nullable String contentType) {
    if (contentType == null || contentType.isBlank()) {
      return true;
    }
    final var mediaType = mediaType(contentType);
    return mediaType.startsWith("text/")
        || TEXT_APPLICATION_TYPES.contains(mediaType)
        || TEXT_SUFFIXES.stream().anyMatch(mediaType::endsWith);
  }

  private static String mediaType(@Nullable String contentType) {
    return contentType == null ? "" : contentType.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
  }
}
