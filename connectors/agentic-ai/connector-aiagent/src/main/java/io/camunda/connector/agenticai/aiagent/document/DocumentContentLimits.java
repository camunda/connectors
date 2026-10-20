/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.document;

/**
 * The size limits in bytes of a document whose content is added to the conversation, per kind of
 * content (see {@link DocumentContentGuard}).
 *
 * <p>The defaults stay below the limits of the most restrictive supported model provider, Amazon
 * Bedrock Converse (4.5 MB for a document, 3.75 MB for an image): 3.5 MB for a PDF document and for
 * an image. They apply to every provider. Limits per provider are an open point, together with a
 * capability matrix of the providers.
 *
 * <p>A text document costs a roughly predictable number of tokens per byte, so its limit is the
 * smallest. Bytes are a poor proxy for the tokens a document costs: a PDF of 214 KB can cost about
 * 230,000 input tokens, depending on the pages and the provider.
 *
 * @param textBytes the limit of a text-based document
 * @param pdfBytes the limit of a PDF document
 * @param imageBytes the limit of an image
 */
public record DocumentContentLimits(long textBytes, long pdfBytes, long imageBytes) {

  /** The default limit of a text-based document: 256 KB. */
  public static final long DEFAULT_TEXT_BYTES = 256L * 1024;

  /** The default limit of a PDF document: 3.5 MB. */
  public static final long DEFAULT_PDF_BYTES = 3584L * 1024;

  /** The default limit of an image: 3.5 MB. */
  public static final long DEFAULT_IMAGE_BYTES = 3584L * 1024;

  /**
   * @throws IllegalArgumentException if a limit is not positive
   */
  public DocumentContentLimits {
    requirePositive("text", textBytes);
    requirePositive("pdf", pdfBytes);
    requirePositive("image", imageBytes);
  }

  /** The default limits. */
  public static DocumentContentLimits defaults() {
    return new DocumentContentLimits(DEFAULT_TEXT_BYTES, DEFAULT_PDF_BYTES, DEFAULT_IMAGE_BYTES);
  }

  private static void requirePositive(String kind, long bytes) {
    if (bytes <= 0) {
      throw new IllegalArgumentException(
          "The content limit of %s documents must be positive, but is %d bytes."
              .formatted(kind, bytes));
    }
  }
}
