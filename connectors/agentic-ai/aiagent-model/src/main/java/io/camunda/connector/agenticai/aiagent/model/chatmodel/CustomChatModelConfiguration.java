/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model.chatmodel;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Configuration of a user-supplied chat model provider. {@link #providerType()} selects the {@code
 * ChatModelFactory} that serves the request; {@link #parameters()} is opaque configuration only
 * that factory understands.
 *
 * @param providerType identifier of the custom provider implementation
 * @param model identifier of the model to use, in the format of the provider
 * @param parameters provider-specific parameters, empty if none are configured
 */
public record CustomChatModelConfiguration(
    String providerType, String model, Map<String, Object> parameters)
    implements ChatModelConfiguration {

  public CustomChatModelConfiguration {
    parameters = Collections.unmodifiableMap(new LinkedHashMap<>(parameters));
  }

  @Override
  public String provider() {
    return providerType;
  }

  /** Lists the names of the parameters only, as their values may be secrets. */
  @Override
  public String toString() {
    return "CustomChatModelConfiguration{providerType=%s, model=%s, parameters=%s}"
        .formatted(providerType, model, parameters.keySet());
  }
}
