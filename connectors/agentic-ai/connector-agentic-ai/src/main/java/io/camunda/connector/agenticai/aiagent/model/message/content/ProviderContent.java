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
 * Opaque, provider-specific content block (e.g. Anthropic {@code server_tool_use}, {@code
 * code_execution_tool_result}, {@code container_upload}) that has no provider-neutral
 * representation in the domain {@link Content} model. {@code payload} is persisted as plain JSON (a
 * {@code Map} at runtime, never a live vendor SDK object) so it round-trips losslessly through
 * conversation memory and can be replayed back to the originating provider.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ProviderContent(
    String provider,
    String blockType,
    Object payload,
    @JsonInclude(JsonInclude.Include.NON_EMPTY) @Nullable Map<String, Object> metadata)
    implements Content {

  public static ProviderContent providerContent(String provider, String blockType, Object payload) {
    return new ProviderContent(provider, blockType, payload, null);
  }
}
