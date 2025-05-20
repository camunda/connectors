/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model.message.content;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TextContentBlock(String text) implements ContentBlock {
  public TextContentBlock {
    if (text == null || text.isBlank()) {
      throw new IllegalArgumentException("Text cannot be null or empty");
    }
  }

  public static TextContentBlock textContent(String text) {
    return new TextContentBlock(text);
  }
}
