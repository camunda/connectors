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
}
