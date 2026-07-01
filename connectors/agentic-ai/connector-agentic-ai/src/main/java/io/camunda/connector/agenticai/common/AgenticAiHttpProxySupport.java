/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.common;

import io.camunda.connector.http.client.client.jdk.proxy.JdkHttpClientProxyConfigurator;
import io.camunda.connector.http.client.proxy.ProxyConfiguration;

/** Provides HTTP proxy support utilities for Agentic AI connectors. */
public class AgenticAiHttpProxySupport {
  private final ProxyConfiguration proxyConfiguration;
  private final JdkHttpClientProxyConfigurator jdkHttpClientProxyConfigurator;

  public AgenticAiHttpProxySupport(ProxyConfiguration proxyConfiguration) {
    this.proxyConfiguration = proxyConfiguration;
    this.jdkHttpClientProxyConfigurator = new JdkHttpClientProxyConfigurator(proxyConfiguration);
  }

  public ProxyConfiguration getProxyConfiguration() {
    return proxyConfiguration;
  }

  public JdkHttpClientProxyConfigurator getJdkHttpClientProxyConfigurator() {
    return jdkHttpClientProxyConfigurator;
  }
}
