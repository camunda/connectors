/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.model.message.content;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Map;
import org.springframework.lang.Nullable;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ReasoningContent(
    String text,
    @Nullable String signature,
    boolean redacted,
    @JsonInclude(JsonInclude.Include.NON_EMPTY) Map<String, Object> metadata)
    implements Content {

  public ReasoningContent {
    if (text == null) {
      throw new IllegalArgumentException("Text cannot be null");
    }
    // empty/blank text is allowed — providers emit empty thinking blocks
    // with non-null signatures for redaction or roundtrip-only purposes
  }

  public static ReasoningContent reasoningContent(String text) {
    return new ReasoningContent(text, null, false, null);
  }

  public static ReasoningContent reasoningContent(String text, String signature) {
    return new ReasoningContent(text, signature, false, null);
  }
}
