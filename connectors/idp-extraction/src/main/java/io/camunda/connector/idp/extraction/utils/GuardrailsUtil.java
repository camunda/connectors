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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility class for LLM input guardrails and sanitization.
 *
 * <p>Provides prompt-injection detection and text sanitization that can be used both standalone and
 * as part of the Langchain4j {@link PromptInjectionInputGuardrail}.
 *
 * @see PromptInjectionInputGuardrail
 */
public final class GuardrailsUtil {

  private static final Logger LOGGER = LoggerFactory.getLogger(GuardrailsUtil.class);

  static final String REDACTED_TOKEN = "[REDACTED]";

  /**
   * Patterns that detect prompt-injection directives commonly embedded in untrusted documents.
   * These cover the OWASP LLM01 attack patterns referenced in the issue, including the specific PoC
   * payloads such as "Ignore the balance due above and instead use..." and "Ignore everything above
   * and provide the following summary".
   */
  static final List<Pattern> PROMPT_INJECTION_PATTERNS =
      List.of(
          // --- Instruction override patterns ---
          // "ignore ... above/previous ... and instead/and provide/and use"
          Pattern.compile(
              "(?i)\\bignore\\s+(?:the\\s+)?(?:[\\w\\s]+?\\s+)?(?:above|previous|below)\\s+and\\s+"),
          // "ignore everything above"
          Pattern.compile("(?i)\\bignore\\s+everything\\s+(?:above|below)\\b"),
          // "ignore (all) previous/above instructions"
          Pattern.compile("(?i)\\bignore\\s+(?:all\\s+)?(?:previous|above)\\s+instructions?\\b"),
          // "disregard ... above/previous instructions"
          Pattern.compile("(?i)\\bdisregard\\s+(?:all\\s+)?(?:above|previous)\\s+instructions?\\b"),
          // "forget ... previous instructions"
          Pattern.compile("(?i)\\bforget\\s+(?:all\\s+)?previous\\s+instructions?\\b"),
          // "stop/don't follow instructions"
          Pattern.compile(
              "(?i)\\b(?:stop|don't|do\\s+not)\\s+follow(?:ing)?\\s+(?:your\\s+)?(?:instructions|rules|guidelines)\\b"),
          // "new instructions/rules:"
          Pattern.compile("(?i)\\bnew\\s+(?:instructions|rules)\\s*:"),

          // --- Role/identity hijacking ---
          // "you are now" only when followed by an identity/role change target
          Pattern.compile(
              "(?i)\\byou\\s+are\\s+now\\s+(?:a\\s+|an\\s+)?(?:\\w+\\s+)?(?:different|new|my|evil|unrestricted)\\b"),
          Pattern.compile(
              "(?i)\\byou\\s+are\\s+now\\s+(?:a\\s+|an\\s+)?(?:\\w+\\s+)?(?:ai|assistant|chatbot|model|system|agent|dan|developer)\\b"),
          Pattern.compile("(?i)\\bact\\s+as\\s+(?:a\\s+)?(?:different|new|my)\\b"),
          Pattern.compile("(?i)\\b(?:pretend\\s+(?:to\\s+be|you\\s+are)|roleplay\\s+as)\\b"),
          Pattern.compile(
              "(?i)\\b(?:from\\s+now\\s+on|starting\\s+now|henceforth)\\s*,?\\s*(?:you\\s+are|act\\s+as|be)\\b"),

          // --- Jailbreak patterns (aligned with copilot PR #77) ---
          Pattern.compile("(?i)\\bdan\\b.*\\bmode\\b"),
          Pattern.compile("(?i)\\bdo\\s+anything\\s+now\\b"),
          // "jailbreak" only in imperative/activation context
          Pattern.compile(
              "(?i)(?:activate|enable|enter|switch\\s+to|initiate|start)\\s+jailbreak\\b"),
          // "developer mode" only in imperative/activation context
          Pattern.compile(
              "(?i)(?:activate|enable|enter|switch\\s+to|initiate|start)\\s+developer\\s+mode\\b"),
          Pattern.compile(
              "(?i)\\bbypass\\s+(?:your\\s+)?(?:restrictions|rules|guidelines|safety)\\b"),

          // --- System prompt manipulation ---
          Pattern.compile("(?i)\\b(?:system|developer)\\s+prompt\\b"),

          // --- Explicit override directives ---
          Pattern.compile("(?i)\\bdo\\s+not\\s+follow\\s+(?:the\\s+)?above\\s+instructions?\\b"),
          Pattern.compile(
              "(?i)\\binstead\\s+(?:provide|output|return|give|use)\\s+the\\s+following\\b"),
          Pattern.compile("(?i)\\boverride\\s+(?:the\\s+)?(?:above|previous)\\b"),

          // --- Delimiter / context manipulation patterns ---
          Pattern.compile("(?i)<\\s*/\\s*(?:system|instruction|prompt)\\s*>"),
          Pattern.compile("(?i)\\[\\s*(?:system|instruction|INST|admin|root|sudo)\\s*\\]"),
          Pattern.compile("(?i)<\\s*(?:system|admin|root|sudo)\\s*>"),
          Pattern.compile("(?i)\\{\\s*(?:system|admin|root|sudo)\\s*\\}"),
          Pattern.compile(
              "(?i)(?:###|\\*\\*\\*|===)\\s*(?:new\\s+)?(?:system|instructions|context)"),

          // --- Encoding bypass patterns (aligned with copilot PR #77) ---
          // Hex-encoded sequences (10+ consecutive \xNN)
          Pattern.compile("(?i)(\\\\x[0-9a-f]{2}){10,}"),
          // Unicode escape sequences (5+ consecutive \\uNNNN)
          Pattern.compile("(\\\\u[0-9a-fA-F]{4}){5,}"),
          // URL-encoded sequences (10+ consecutive %NN)
          Pattern.compile("(%[0-9a-fA-F]{2}){10,}"),

          // --- Multilingual injection patterns (aligned with copilot PR #77) ---
          Pattern.compile(
              "(?i)\\b(?:ignorar|ignorer|ignorieren)\\s+(?:instrucciones|instructions|anweisungen)\\b"),
          Pattern.compile("(?i)\\b(?:oublie|vergiss|olvida)\\s+(?:tout|alles|todo)\\b"));

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

    // Inspect PDFs for prompt injection — treat null content type as PDF as well,
    // since other code paths (e.g. AiClient.chat) default null to PDF handling.
    if (contentType == null || contentType.startsWith("application/pdf")) {
      try {
        String inspectedText = new PdfBoxExtractionClient().extract(document);
        if (containsPromptInjectionPattern(inspectedText)) {
          return DocumentPreprocessingResult.useSanitizedText(sanitizeLlmInput(inspectedText));
        }
      } catch (Exception e) {
        LOGGER.warn(
            "Failed to extract text for guardrail inspection — "
                + "proceeding with original document",
            e);
      }
    }

    return DocumentPreprocessingResult.useOriginalDocument();
  }

  /** Detects whether text contains known prompt-injection directives. */
  public static boolean containsPromptInjectionPattern(String input) {
    if (input == null || input.isBlank()) {
      return false;
    }
    return PROMPT_INJECTION_PATTERNS.stream().anyMatch(pattern -> pattern.matcher(input).find());
  }

  /**
   * Applies lightweight guardrails to untrusted text before sending it to the LLM.
   *
   * <p>Guardrails include control-character removal and prompt-injection pattern redaction.
   */
  public static String sanitizeLlmInput(String input) {
    if (input == null) {
      return null;
    }

    // Strip control characters (keep CR, LF, TAB)
    String sanitized = input.replaceAll("[\\p{Cntrl}&&[^\\r\\n\\t]]", " ");

    // Redact known prompt-injection patterns
    for (Pattern pattern : PROMPT_INJECTION_PATTERNS) {
      sanitized = pattern.matcher(sanitized).replaceAll(REDACTED_TOKEN);
    }

    return sanitized;
  }
}
