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
package io.camunda.connector.runtime.core.http;

import java.net.URI;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DefaultInstancesUrlBuilder implements InstancesUrlBuilder {
  private static final Logger LOGGER = LoggerFactory.getLogger(DefaultInstancesUrlBuilder.class);

  private final Integer appPort;
  private final DnsResolver dnsResolver;
  private final String headlessServiceHost;
  private Set<String> baseUrls;

  public DefaultInstancesUrlBuilder(Integer appPort, String headlessServiceHost) {
    this(appPort, headlessServiceHost, new DefaultDnsResolver());
  }

  DefaultInstancesUrlBuilder(Integer appPort, String headlessServiceHost, DnsResolver dnsResolver) {
    this.appPort = appPort;
    this.dnsResolver = dnsResolver;
    this.headlessServiceHost = extractHeadlessServiceHost(headlessServiceHost);
  }

  private String extractHeadlessServiceHost(String headlessServiceUrl) {
    if (StringUtils.isNotBlank(headlessServiceUrl) && headlessServiceUrl.startsWith("http")) {
      URI uri = URI.create(headlessServiceUrl);
      return uri.getHost();
    }
    return headlessServiceUrl;
  }

  private Set<String> buildBaseUrls(String headlessServiceHost) {
    if (headlessServiceHost != null) {
      try {
        String[] addresses = dnsResolver.resolve(headlessServiceHost);
        if (addresses.length == 0) {
          LOGGER.error(
              "No addresses found for service: {}. Please check the environment variable CAMUNDA_CONNECTOR_HEADLESS_SERVICEURL.",
              headlessServiceHost);
          throw new UnknownHostException(
              "No Connectors Runtime addresses found for hostname: " + headlessServiceHost);
        }
        return Stream.of(addresses)
            .map(ip -> "http://" + ip + ":" + appPort)
            .collect(Collectors.toSet());
      } catch (UnknownHostException e) {
        LOGGER.error(
            "Unable to resolve hostname: {}. Please check the environment variable CAMUNDA_CONNECTOR_HEADLESS_SERVICEURL.",
            headlessServiceHost,
            e);
        throw new RuntimeException("Unable to resolve hostname: " + headlessServiceHost, e);
      } catch (Exception e) {
        LOGGER.error("An error occurred while resolving hostname: {}.", headlessServiceHost, e);
        throw new RuntimeException(
            "An error occurred while resolving hostname: " + headlessServiceHost, e);
      }
    } else {
      return Set.of("http://localhost" + ":" + appPort);
    }
  }

  @Override
  public List<String> buildUrls(String path) {
    refreshBaseUrls();

    var sanitizedPath = path.startsWith("/") ? path : "/" + path;
    var urls = baseUrls.stream().map(url -> url + sanitizedPath).toList();

    LOGGER.debug("Base URLs built for path {}: {}", path, urls);

    return urls;
  }

  /**
   * Refreshes the base URLs by resolving the headless service URL. This method is called before
   * building URLs to ensure that the latest IP addresses are used (pods may be removed or added).
   *
   * <p>We might consider caching the base URLs for a certain period of time to avoid excessive DNS
   * lookups, but it's not useful in the current implementation given that the number of requests
   * will be pretty low.
   */
  private void refreshBaseUrls() {
    baseUrls = buildBaseUrls(headlessServiceHost);
    LOGGER.debug("Base URLs refreshed: {}", baseUrls);
  }
}
