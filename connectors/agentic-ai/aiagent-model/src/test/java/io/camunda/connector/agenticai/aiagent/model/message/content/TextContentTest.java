/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model.message.content;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

class TextContentTest {

  @Test
  void ofNonBlankDropsTextWithoutInformation() {
    assertThat(TextContent.ofNonBlank(null)).isEmpty();
    assertThat(TextContent.ofNonBlank("")).isEmpty();
    assertThat(TextContent.ofNonBlank(" \n\t")).isEmpty();
    assertThat(TextContent.ofNonBlank(" ", Map.of("k", "v"))).isEmpty();
  }

  @Test
  void ofNonBlankKeepsTheTextAndItsMetadata() {
    assertThat(TextContent.ofNonBlank("hello")).contains(TextContent.textContent("hello"));
    assertThat(TextContent.ofNonBlank("hello", Map.of("k", "v")))
        .contains(new TextContent("hello", Map.of("k", "v")));
  }
}
