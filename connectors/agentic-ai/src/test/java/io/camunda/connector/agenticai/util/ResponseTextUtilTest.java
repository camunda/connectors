/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ResponseTextUtilTest {

  @Test
  void stripMarkdownCodeBlocks_shouldStripCodeBlocks_whenValidMarkdownFormat() {
    // given
    String input = "```json\n{\"name\": \"John\", \"age\": 30}\n```";
    String expected = "{\"name\": \"John\", \"age\": 30}";

    // when
    String result = ResponseTextUtil.stripMarkdownCodeBlocks(input);

    // then
    assertThat(result).isEqualTo(expected);
  }

  @Test
  void stripMarkdownCodeBlocks_shouldStripCodeBlocks_whenNoLanguageSpecified() {
    // given
    String input = "```\n{\"response\": \"data\"}\n```";
    String expected = "{\"response\": \"data\"}";

    // when
    String result = ResponseTextUtil.stripMarkdownCodeBlocks(input);

    // then
    assertThat(result).isEqualTo(expected);
  }

  @Test
  void stripMarkdownCodeBlocks_shouldReturnOriginalString_whenNoCodeBlocks() {
    // given
    String input = "{\"response\": \"data without markdown\"}";

    // when
    String result = ResponseTextUtil.stripMarkdownCodeBlocks(input);

    // then
    assertThat(result).isEqualTo(input);
  }

  @Test
  void stripMarkdownCodeBlocks_shouldReturnNull_whenInputIsNull() {
    // when
    String result = ResponseTextUtil.stripMarkdownCodeBlocks(null);

    // then
    assertThat(result).isNull();
  }

  @Test
  void stripMarkdownCodeBlocks_shouldHandleMultilineJson() {
    // given
    String input =
        """
        ```json
        {
          "name": "John",
          "age": 30,
          "address": {
            "city": "New York"
          }
        }
        ```
        """;
    String expected =
        """
        {
          "name": "John",
          "age": 30,
          "address": {
            "city": "New York"
          }
        }""";

    // when
    String result = ResponseTextUtil.stripMarkdownCodeBlocks(input);

    // then
    assertThat(result).isEqualTo(expected);
  }

  @Test
  void stripMarkdownCodeBlocks_shouldHandleJsonArray() {
    // given
    String input = "```json\n[{\"id\": 1}, {\"id\": 2}]\n```";
    String expected = "[{\"id\": 1}, {\"id\": 2}]";

    // when
    String result = ResponseTextUtil.stripMarkdownCodeBlocks(input);

    // then
    assertThat(result).isEqualTo(expected);
  }

  @Test
  void stripMarkdownCodeBlocks_shouldHandleWhitespace() {
    // given - response with extra whitespace around code blocks
    String input = "  ```json\n{\"key\": \"value\"}\n```  ";
    String expected = "{\"key\": \"value\"}";

    // when
    String result = ResponseTextUtil.stripMarkdownCodeBlocks(input);

    // then
    assertThat(result).isEqualTo(expected);
  }

  @Test
  void stripMarkdownCodeBlocks_shouldNotStripPartialCodeBlocks() {
    // given - malformed input with only opening code fence
    String input = "```json\n{\"incomplete\": \"block\"}";

    // when
    String result = ResponseTextUtil.stripMarkdownCodeBlocks(input);

    // then
    assertThat(result).isEqualTo(input);
  }

  @Test
  void stripMarkdownCodeBlocks_shouldHandleEmptyCodeBlocks() {
    // given
    String input = "```\n\n```";
    String expected = "";

    // when
    String result = ResponseTextUtil.stripMarkdownCodeBlocks(input);

    // then
    assertThat(result).isEqualTo(expected);
  }

  @Test
  void stripMarkdownCodeBlocks_shouldHandleAnthropicClaudeResponse() {
    // given - typical response from Anthropic Claude Sonnet 4.5 on Bedrock
    String input =
        """
        ```json
        {
          "result": "success",
          "data": {
            "items": [
              {"id": 1, "name": "Item 1"},
              {"id": 2, "name": "Item 2"}
            ]
          }
        }
        ```
        """;
    String expected =
        """
        {
          "result": "success",
          "data": {
            "items": [
              {"id": 1, "name": "Item 1"},
              {"id": 2, "name": "Item 2"}
            ]
          }
        }""";

    // when
    String result = ResponseTextUtil.stripMarkdownCodeBlocks(input);

    // then
    assertThat(result).isEqualTo(expected);
  }

  @Test
  void stripMarkdownCodeBlocks_shouldReturnOriginal_whenNoNewlineAfterOpeningFence() {
    // given - malformed markdown with no newline after opening fence
    String input = "```json{\"test\":\"value\"}\n```";

    // when
    String result = ResponseTextUtil.stripMarkdownCodeBlocks(input);

    // then
    assertThat(result).isEqualTo(input);
  }

  @Test
  void stripMarkdownCodeBlocks_shouldReturnOriginal_whenNewlineTooFarFromOpeningFence() {
    // given - malformed markdown with language specifier longer than MAX_LANGUAGE_SPECIFIER_LENGTH
    // (45 characters > 20 character limit)
    String input = "```verylonglanguagespecifierthatexceedslimit\n{\"test\":\"value\"}\n```";

    // when
    String result = ResponseTextUtil.stripMarkdownCodeBlocks(input);

    // then
    assertThat(result).isEqualTo(input);
  }
}
