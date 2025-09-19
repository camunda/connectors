/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.idp.extraction.utils;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class StringUtilTest {

  @Test
  void stripMarkdownCodeBlocks_shouldStripCodeBlocks_whenValidMarkdownFormat() {
    // given
    String input = "```json\n{\"name\": \"John\", \"age\": 30}\n```";
    String expected = "{\"name\": \"John\", \"age\": 30}";

    // when
    String result = StringUtil.stripMarkdownCodeBlocks(input);

    // then
    assertThat(result).isEqualTo(expected);
  }

  @Test
  void stripMarkdownCodeBlocks_shouldReturnOriginalString_whenNoCodeBlocks() {
    // given
    String input = "{\"response\": \"data without markdown\"}";

    // when
    String result = StringUtil.stripMarkdownCodeBlocks(input);

    // then
    assertThat(result).isEqualTo(input);
  }

  @Test
  void filterThinkingContent_shouldRemoveThinkingTags_whenPresent() {
    // given
    String input = "<thinking>This is my reasoning</thinking>{\"name\": \"John\", \"age\": 30}";
    String expected = "{\"name\": \"John\", \"age\": 30}";

    // when
    String result = StringUtil.filterThinkingContent(input);

    // then
    assertThat(result).isEqualTo(expected);
  }

  @Test
  void filterThinkingContent_shouldRemoveMultilineThinkingContent() {
    // given
    String input =
        "<thinking>\nLet me analyze this document...\nI can see that...\n</thinking>\n{\"result\": \"extracted data\"}";
    String expected = "{\"result\": \"extracted data\"}";

    // when
    String result = StringUtil.filterThinkingContent(input);

    // then
    assertThat(result).isEqualTo(expected);
  }

  @Test
  void filterThinkingContent_shouldBeCaseInsensitive() {
    // given
    String input = "<THINKING>reasoning here</THINKING>{\"data\": \"value\"}";
    String expected = "{\"data\": \"value\"}";

    // when
    String result = StringUtil.filterThinkingContent(input);

    // then
    assertThat(result).isEqualTo(expected);
  }

  @Test
  void filterThinkingContent_shouldReturnOriginalString_whenNoThinkingTags() {
    // given
    String input = "{\"response\": \"data without thinking tags\"}";

    // when
    String result = StringUtil.filterThinkingContent(input);

    // then
    assertThat(result).isEqualTo(input);
  }

  @Test
  void filterThinkingContent_shouldReturnNull_whenInputIsNull() {
    // when
    String result = StringUtil.filterThinkingContent(null);

    // then
    assertThat(result).isNull();
  }

  @Test
  void filterThinkingContent_shouldCleanUpExtraWhitespace() {
    // given
    String input = "Some text\n\n<thinking>reasoning</thinking>\n\nMore text";
    String expected = "Some text\nMore text";

    // when
    String result = StringUtil.filterThinkingContent(input);

    // then
    assertThat(result).isEqualTo(expected);
  }
}
