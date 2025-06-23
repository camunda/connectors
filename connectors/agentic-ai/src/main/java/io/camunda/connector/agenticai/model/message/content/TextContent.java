/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.model.message.content;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TextContent(String text) implements Content {
  public TextContent {
    if (text == null || text.isBlank()) {
      throw new IllegalArgumentException("Text cannot be null or empty");
    }
  }

  public static TextContent textContent(String text) {
    return new TextContent(text);
  }
}
