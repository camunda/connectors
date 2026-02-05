/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.util;

/**
 * Utility class for cleaning and preparing response text from AI models before JSON parsing.
 *
 * <p>This utility handles common patterns in AI model outputs that wrap valid JSON content in
 * additional formatting, particularly Markdown code blocks.
 */
public final class ResponseTextUtil {

  /**
   * Maximum length for a language specifier in markdown code blocks (e.g., "json", "javascript").
   * If a newline appears beyond this distance from the opening fence, the input is treated as plain
   * text rather than a code block.
   */
  private static final int MAX_LANGUAGE_SPECIFIER_LENGTH = 20;

  private ResponseTextUtil() {}

  /**
   * Strips markdown code block syntax from a string response.
   *
   * <p>If the response is wrapped in markdown code blocks (starts and ends with ```), this method
   * removes the code block markers and returns the content inside.
   *
   * <p>This is useful when AI models (like Anthropic Claude) wrap JSON responses in markdown code
   * fences such as:
   *
   * <pre>
   * ```json
   * {"key": "value"}
   * ```
   * </pre>
   *
   * @param response the string response that may contain markdown code blocks
   * @return the response with markdown code blocks stripped, or the original response if no code
   *     blocks are found
   */
  public static String stripMarkdownCodeBlocks(String response) {
    if (response == null) {
      return null;
    }

    String trimmed = response.trim();

    // Check if response is wrapped in markdown code blocks
    if (trimmed.startsWith("```") && trimmed.endsWith("```")) {
      // Find the first newline after opening ``` (after optional language specifier)
      int startIndex = trimmed.indexOf('\n');
      // If there's no newline or it's too far from the opening fence, treat as plain text
      if (startIndex == -1 || startIndex > MAX_LANGUAGE_SPECIFIER_LENGTH) {
        return response;
      }
      // Find the last ```
      int endIndex = trimmed.lastIndexOf("```");
      if (endIndex > startIndex) {
        return trimmed.substring(startIndex + 1, endIndex).trim();
      }
    }

    return response;
  }
}
