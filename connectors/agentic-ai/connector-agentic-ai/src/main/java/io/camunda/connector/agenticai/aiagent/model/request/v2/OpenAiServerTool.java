/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model.request.v2;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * A server tool that the model provider runs inside the model call, so the agent does not model or
 * execute it. Prototype: only the OpenRouter {@code web_search} server tool exists.
 */
public record OpenAiServerTool(
    @NotNull Type type, @Valid @Nullable WebSearchParameters parameters) {

  public enum Type {
    @JsonProperty("web_search")
    WEB_SEARCH
  }

  /** Optional parameters of the OpenRouter {@code openrouter:web_search} server tool. */
  public record WebSearchParameters(
      @Nullable String engine,
      @Min(1) @Max(25) @Nullable Integer maxResults,
      @Nullable List<String> allowedDomains) {}
}
