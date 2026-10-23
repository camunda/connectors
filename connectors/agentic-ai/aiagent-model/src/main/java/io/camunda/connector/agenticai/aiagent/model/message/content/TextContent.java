/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model.message.content;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Map;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TextContent(
    String text, @JsonInclude(JsonInclude.Include.NON_EMPTY) @Nullable Map<String, Object> metadata)
    implements Content {
  public TextContent {
    if (text == null || text.isBlank()) {
      throw new IllegalArgumentException("Text cannot be null or empty");
    }
  }

  public static TextContent textContent(String text) {
    return new TextContent(text, null);
  }

  /**
   * The text content of a text a model returned, or empty if there is none: blank text carries no
   * information, and the constructor rejects it.
   */
  public static Optional<TextContent> ofNonBlank(@Nullable String text) {
    return ofNonBlank(text, null);
  }

  public static Optional<TextContent> ofNonBlank(
      @Nullable String text, @Nullable Map<String, Object> metadata) {
    return text == null || text.isBlank()
        ? Optional.empty()
        : Optional.of(new TextContent(text, metadata));
  }
}
