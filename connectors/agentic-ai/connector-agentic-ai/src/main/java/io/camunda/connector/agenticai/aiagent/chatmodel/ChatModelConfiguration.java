/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.chatmodel;

/**
 * Neutral, connector-agnostic descriptor a {@link ChatModelFactory} inspects to decide whether it
 * can serve a request and to build a {@link ChatModel}. Today the built-in AI Agent request
 * supplies configurations via the sealed {@code ProviderConfiguration} union; the SPI is the seam
 * for custom/native providers to contribute their own {@link ChatModelConfiguration}
 * implementation, whose request-side wiring lands with the v2 request types.
 */
public interface ChatModelConfiguration {

  /** Type of the provider implementation used to resolve the backing chat model. */
  String provider();

  /** Identifier of the model to use (provider-specific format). */
  String model();
}
