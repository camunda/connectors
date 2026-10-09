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

/**
 * Opaque, provider-specific content that has no provider-neutral representation. {@code payload} is
 * the lossless source used for replay. {@code text} is an optional human-readable rendering for
 * display only (e.g. agent instance history) and is never replayed.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ProviderContent(
    String provider,
    Object payload,
    @JsonInclude(JsonInclude.Include.NON_NULL) @Nullable String text,
    @JsonInclude(JsonInclude.Include.NON_EMPTY) @Nullable Map<String, Object> metadata)
    implements Content {

  public ProviderContent {
    if (payload == null) {
      throw new IllegalArgumentException("Payload cannot be null");
    }
  }

  public ProviderContent(String provider, Object payload, @Nullable Map<String, Object> metadata) {
    this(provider, payload, null, metadata);
  }

  public static ProviderContent providerContent(String provider, Object payload) {
    return new ProviderContent(provider, payload, null, null);
  }
}
