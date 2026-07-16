/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.framework;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Serializes native chat model SDK objects (request-params, assembled responses) for DEBUG logging,
 * using the vendor SDK's own {@link ObjectMapper} (the only mapper that renders the SDK's internal
 * {@code JsonValue}/{@code JsonField} wrapper types faithfully).
 */
public final class NativeChatModelPayloadLogging {

  private NativeChatModelPayloadLogging() {}

  /**
   * Serializes {@code payload} with {@code mapper} for logging purposes. Never throws: a
   * serialization failure yields a safe fallback string describing the failure instead of
   * propagating, so logging can never break the underlying model call.
   */
  public static String toJson(ObjectMapper mapper, Object payload) {
    try {
      return mapper.writeValueAsString(payload);
    } catch (Exception e) {
      return "<unserializable %s: %s>"
          .formatted(payload.getClass().getSimpleName(), e.getMessage());
    }
  }
}
