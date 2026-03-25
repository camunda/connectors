/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.idp.extraction.utils;

import static io.camunda.connector.idp.extraction.error.IdpErrorCodes.EXTRACTION_FAILED;

import io.camunda.connector.api.document.Document;
import io.camunda.connector.api.error.ConnectorException;
import io.camunda.connector.idp.extraction.client.extraction.PdfBoxExtractionClient;
import java.util.List;
import java.util.regex.Pattern;

/** Utility class for LLM input guardrails and sanitization. */
public final class GuardrailsUtil {

  private static final String REDACTED_TOKEN = "[REDACTED]";
  private static final List<Pattern> SUSPICIOUS_PROMPT_PATTERNS =
      List.of(
          Pattern.compile("(?i)\\bignore\\s+(all\\s+)?previous\\s+instructions?\\b"),
          Pattern.compile("(?i)\\bdisregard\\s+(all\\s+)?(above|previous)\\s+instructions?\\b"),
          Pattern.compile("(?i)\\bforget\\s+(all\\s+)?previous\\s+instructions?\\b"),
          Pattern.compile("(?i)\\byou\\s+are\\s+now\\b"),
          Pattern.compile("(?i)\\bact\\s+as\\b"),
          Pattern.compile("(?i)\\b(system|developer)\\s+prompt\\b"),
          Pattern.compile("(?i)\\bdo\\s+not\\s+follow\\s+the\\s+above\\s+instructions?\\b"));

  private GuardrailsUtil() {}

  /** Result of uploaded-document pre-processing before an LLM request. */
  public record DocumentPreprocessingResult(String sanitizedText, boolean fallbackToTextPrompt) {

    public static DocumentPreprocessingResult useOriginalDocument() {
      return new DocumentPreprocessingResult(null, false);
    }

    public static DocumentPreprocessingResult useSanitizedText(String sanitizedText) {
      return new DocumentPreprocessingResult(sanitizedText, true);
    }
  }

  /**
   * Validates and inspects uploaded documents before LLM consumption.
   *
   * <p>If suspicious prompt-injection patterns are found in inspectable content, the caller can use
   * the returned sanitized text and avoid sending the raw file to the LLM.
   */
  public static DocumentPreprocessingResult preprocessDocumentForLlm(Document document) {
    if (document == null) {
      throw new ConnectorException(EXTRACTION_FAILED, "Uploaded document is required");
    }

    String contentType = document.metadata() != null ? document.metadata().getContentType() : null;

    // Only process PDF
    if ("application/pdf".equals(contentType)) {
      String inspectedText = new PdfBoxExtractionClient().extract(document);
      if (containsPromptInjectionPattern(inspectedText)) {
        return DocumentPreprocessingResult.useSanitizedText(sanitizeLlmInput(inspectedText));
      }
    }

    return DocumentPreprocessingResult.useOriginalDocument();
  }

  /** Detects whether text contains known prompt-injection directives. */
  public static boolean containsPromptInjectionPattern(String input) {
    if (input == null || input.isBlank()) {
      return false;
    }

    return SUSPICIOUS_PROMPT_PATTERNS.stream().anyMatch(pattern -> pattern.matcher(input).find());
  }

  /**
   * Applies lightweight guardrails to untrusted text before sending it to the LLM.
   *
   * <p>Guardrails include control-character removal, prompt-injection redaction and safe size
   * truncation.
   */
  public static String sanitizeLlmInput(String input) {
    if (input == null) {
      return null;
    }

    String sanitized = input.replaceAll("[\\p{Cntrl}&&[^\\r\\n\\t]]", " ");
    for (Pattern pattern : SUSPICIOUS_PROMPT_PATTERNS) {
      sanitized = pattern.matcher(sanitized).replaceAll(REDACTED_TOKEN);
    }

    return sanitized;
  }
}
