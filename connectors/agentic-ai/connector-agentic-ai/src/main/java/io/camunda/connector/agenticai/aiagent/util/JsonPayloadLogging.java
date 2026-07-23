/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.util;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Serializes an arbitrary payload (e.g. request params, assembled responses) to JSON for DEBUG
 * logging, with a safe fallback if serialization fails. Callers supply the {@link ObjectMapper} to
 * use, e.g. a vendor SDK's own mapper when that is the only mapper that renders the SDK's internal
 * wrapper types faithfully.
 */
public final class JsonPayloadLogging {

  private JsonPayloadLogging() {}

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
