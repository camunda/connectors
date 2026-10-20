/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.proxy.azure;

import com.azure.core.http.ProxyOptions;
import io.camunda.connector.http.client.proxy.NonProxyHosts;
import io.camunda.connector.http.client.proxy.ProxyConfiguration;
import java.net.InetSocketAddress;
import java.util.Optional;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Provides HTTP proxy options for azure-core based clients. */
public class AzureHttpProxySupport {
  private static final Logger LOG = LoggerFactory.getLogger(AzureHttpProxySupport.class);

  private final ProxyConfiguration proxyConfiguration;

  public AzureHttpProxySupport(ProxyConfiguration proxyConfiguration) {
    this.proxyConfiguration = proxyConfiguration;
  }

  /**
   * Returns the {@link ProxyOptions} configured for the target scheme, if any, for azure-core/
   * azure-identity based clients (e.g. {@code ClientSecretCredentialBuilder}, {@code
   * ManagedIdentityCredentialBuilder}) so their own token-exchange requests to Microsoft Entra ID
   * also go through the configured proxy rather than bypassing it.
   */
  public Optional<ProxyOptions> proxyOptions(String scheme) {
    return proxyConfiguration
        .getProxyDetails(scheme)
        .map(
            proxyDetails -> {
              LOG.debug(
                  "Using proxy for target scheme [{}] => [{}:{}]",
                  scheme,
                  proxyDetails.host(),
                  proxyDetails.port());
              final var options =
                  new ProxyOptions(
                      ProxyOptions.Type.HTTP,
                      new InetSocketAddress(proxyDetails.host(), proxyDetails.port()));
              options.setNonProxyHosts(
                  NonProxyHosts.getNonProxyHostsPatterns()
                      .distinct()
                      .collect(Collectors.joining("|")));
              if (proxyDetails.hasCredentials()) {
                options.setCredentials(proxyDetails.user(), proxyDetails.password());
              }
              return options;
            });
  }
}
