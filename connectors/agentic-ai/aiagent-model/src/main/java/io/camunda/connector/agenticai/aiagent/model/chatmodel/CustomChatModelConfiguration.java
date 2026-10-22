/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model.chatmodel;

import java.util.Map;

/**
 * Configuration of a user-supplied chat model provider. {@link #providerType()} selects the {@code
 * ChatModelFactory} that serves the request; {@link #parameters()} is opaque configuration only
 * that factory understands.
 */
public interface CustomChatModelConfiguration extends ChatModelConfiguration {

  /** Identifier of the custom provider implementation. */
  String providerType();

  /** Provider-specific parameters, empty if none are configured. */
  Map<String, Object> parameters();
}
