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
}
