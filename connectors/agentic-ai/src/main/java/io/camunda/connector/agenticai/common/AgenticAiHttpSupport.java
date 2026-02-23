/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.common;

import io.camunda.connector.http.client.client.jdk.proxy.JdkHttpClientProxyConfigurator;

/** Provides HTTP support utilities for Agentic AI connectors, including proxy configuration. */
public class AgenticAiHttpSupport {
  private final JdkHttpClientProxyConfigurator jdkHttpClientProxyConfigurator;

  public AgenticAiHttpSupport(JdkHttpClientProxyConfigurator jdkHttpClientProxyConfigurator) {
    this.jdkHttpClientProxyConfigurator = jdkHttpClientProxyConfigurator;
  }

  public JdkHttpClientProxyConfigurator getJdkHttpClientProxyConfigurator() {
    return jdkHttpClientProxyConfigurator;
  }
}
