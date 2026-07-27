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
import org.jspecify.annotations.Nullable;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ReasoningContent(
    String provider,
    @JsonInclude(JsonInclude.Include.NON_NULL) @Nullable Object payload,
    @JsonInclude(JsonInclude.Include.NON_EMPTY) @Nullable Map<String, Object> metadata)
    implements Content {

  public static ReasoningContent reasoningContent(String provider, @Nullable Object payload) {
    return new ReasoningContent(provider, payload, Map.of());
  }
}
