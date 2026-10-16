/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.common.configuration;

import io.camunda.connector.agenticai.common.http.AgenticAiHttpProxySupport;
import io.camunda.connector.http.client.proxy.EnvironmentProxyConfiguration;
import io.camunda.connector.http.client.proxy.ProxyConfiguration;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@ConditionalOnBooleanProperty(value = "camunda.connector.agenticai.enabled", matchIfMissing = true)
@EnableConfigurationProperties(AgenticAiHttpProperties.class)
public class AgenticAiCommonAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean
  public AgenticAiHttpProxySupport agenticAiHttpProxySupport(
      AgenticAiHttpProperties httpProperties) {
    // IMPORTANT: the proxy configuration needs to be configured to support
    // CONNECTOR_HTTP(S)_PLAIN_* proxy vars as JDK clients are not able to connect to proxies
    // running on HTTPS
    final ProxyConfiguration proxyConfiguration =
        httpProperties.proxySupport().enabled()
            ? EnvironmentProxyConfiguration.withPlainProxySupport()
            : ProxyConfiguration.NONE;

    return new AgenticAiHttpProxySupport(proxyConfiguration);
  }
}
