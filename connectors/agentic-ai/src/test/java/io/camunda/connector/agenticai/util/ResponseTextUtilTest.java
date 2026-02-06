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
    String input = """
        ```json
        {"name": "John", "age": 30}
        ```
        """;
    String expected = "{\"name\": \"John\", \"age\": 30}";

    // when
    String result = ResponseTextUtil.stripMarkdownCodeBlocks(input);

    // then
    assertThat(result).isEqualTo(expected);
  }

  @Test
  void stripMarkdownCodeBlocks_shouldStripCodeBlocks_whenNoLanguageSpecified() {
    // given
    String input = """
        ```
        {"response": "data"}
        ```
        """;
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
    String input = """
        ```json
        [{"id": 1}, {"id": 2}]
        ```
        """;
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
    String input = """
        ```json
        {"incomplete": "block"}""";

    // when
    String result = ResponseTextUtil.stripMarkdownCodeBlocks(input);

    // then
    assertThat(result).isEqualTo(input);
  }

  @Test
  void stripMarkdownCodeBlocks_shouldHandleEmptyCodeBlocks() {
    // given - empty code block edge case
    String input = """
        ```

        ```
        """;
    String expected = "";

    // when
    String result = ResponseTextUtil.stripMarkdownCodeBlocks(input);

    // then
    assertThat(result).isEqualTo(expected);
  }

  @Test
  void stripMarkdownCodeBlocks_shouldHandleComplexNestedJson() {
    // given - complex nested JSON structure with arrays and objects
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
