/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.idp.extraction.utils;

/** Utility class for string manipulation operations. */
public final class StringUtil {

  private StringUtil() {}

  /**
   * Strips markdown code block syntax from a string response.
   *
   * <p>If the response is wrapped in markdown code blocks (starts and ends with ```), this method
   * removes the code block markers and returns the content inside.
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
      // Find the first newline after opening ```
      int startIndex = trimmed.indexOf('\n');
      if (startIndex != -1) {
        // Find the last ```
        int endIndex = trimmed.lastIndexOf("```");
        if (endIndex > startIndex) {
          return trimmed.substring(startIndex + 1, endIndex).trim();
        }
      }
    }

    return response;
  }

  /**
   * Filters out thinking content from AI responses to prevent including reasoning in the final
   * output.
   *
   * <p>This method removes content between &lt;thinking&gt; and &lt;/thinking&gt; tags from AI
   * responses. It handles case-insensitive matching and content that spans multiple lines.
   *
   * @param response the AI response that may contain thinking tags
   * @return the response with thinking content filtered out, or the original response if no
   *     thinking tags are found
   */
  public static String filterThinkingContent(String response) {
    if (response == null) {
      return null;
    }

    // Remove content between <thinking> and </thinking> tags (case insensitive)
    // Uses DOTALL flag to match across newlines
    String filtered = response.replaceAll("(?is)<thinking>.*?</thinking>", "");

    // Also handle cases where thinking tags might be on separate lines
    filtered = filtered.replaceAll("(?is)<thinking>\\s*\\n.*?\\n\\s*</thinking>", "");

    // Clean up any extra whitespace that might be left
    filtered = filtered.replaceAll("\\n\\s*\\n", "\n").trim();

    return filtered;
  }
}
