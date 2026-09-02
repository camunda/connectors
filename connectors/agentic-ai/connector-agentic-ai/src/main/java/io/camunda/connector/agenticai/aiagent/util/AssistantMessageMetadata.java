/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.util;

import java.time.ZonedDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Builds the {@code metadata} map every {@code ChatModel} implementation attaches to the {@link
 * io.camunda.connector.agenticai.aiagent.model.message.AssistantMessage} it returns.
 *
 * <p>Every provider/framework converter must stamp the response with a common {@code timestamp}
 * entry (when the model responded) in addition to whatever provider-specific data it wants to
 * preserve (raw response id, framework metadata, provider stop reason, ...). Centralizing that
 * merge here means a new provider gets the timestamp for free and can't forget it, and it keeps the
 * shape of that common entry consistent across providers.
 */
public final class AssistantMessageMetadata {

  /** Key under which the response-received timestamp is stored. */
  public static final String TIMESTAMP_KEY = "timestamp";

  private AssistantMessageMetadata() {}

  /**
   * Merges the given provider-specific metadata with the common defaults (currently just {@code
   * timestamp}). Provider-specific keys take precedence over defaults on collision, though no
   * provider is expected to emit a {@code timestamp} key of its own.
   *
   * @param providerMetadata provider/framework-specific metadata, e.g. {@code Map.of("anthropic",
   *     Map.of("stopReason", ...))} or {@code Map.of("framework", ...)}
   */
  public static Map<String, Object> withDefaults(Map<String, ?> providerMetadata) {
    final Map<String, Object> merged = new LinkedHashMap<>();
    merged.put(TIMESTAMP_KEY, ZonedDateTime.now());
    merged.putAll(providerMetadata);
    return merged;
  }
}
