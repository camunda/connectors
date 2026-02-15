/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information regarding copyright
 * ownership. Camunda licenses this file to you under the Apache License,
 * Version 2.0; you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.camunda.connector.http.client.client.jdk.proxy;

import io.camunda.connector.http.client.proxy.ProxyConfiguration;
import io.camunda.connector.http.client.proxy.ProxyConfiguration.ProxyDetails;
import java.net.Authenticator;
import java.net.PasswordAuthentication;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * An {@link Authenticator} implementation for the JDK {@link java.net.http.HttpClient} that
 * provides proxy authentication credentials from the same environment variables as the Apache HTTP
 * client proxy support.
 *
 * @see ProxyConfiguration for the list of supported environment variables
 */
public class JdkProxyAuthenticator extends Authenticator {
  private static final Logger LOG = LoggerFactory.getLogger(JdkProxyAuthenticator.class);

  private final ProxyConfiguration proxyConfiguration;

  public JdkProxyAuthenticator(ProxyConfiguration proxyConfiguration) {
    this.proxyConfiguration = proxyConfiguration;
  }

  @Override
  protected PasswordAuthentication getPasswordAuthentication() {
    if (getRequestorType() != RequestorType.PROXY) {
      return null;
    }

    String protocol = ProtocolNormalizer.normalize(getRequestingProtocol());
    return proxyConfiguration
        .getProxyDetails(protocol)
        .filter(ProxyDetails::hasCredentials)
        .map(
            details -> {
              LOG.debug("Providing proxy authentication for protocol [{}]", protocol);
              return new PasswordAuthentication(details.user(), details.password().toCharArray());
            })
        .orElse(null);
  }
}
