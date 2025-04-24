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

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Default implementation of the DnsResolver interface. This class is responsible for resolving
 * hostnames to IP addresses. It uses the standard Java library to perform the DNS resolution.
 *
 * @see InetAddress
 */
public class DefaultDnsResolver implements DnsResolver {

  private static final Logger LOGGER = LoggerFactory.getLogger(DefaultDnsResolver.class);

  @Override
  public String[] resolve(String host) {
    try {
      LOGGER.debug("Resolving host: {}", host);
      var resolvedAddresses =
          Stream.of(InetAddress.getAllByName(host))
              .map(InetAddress::getHostAddress)
              .toArray(String[]::new);
      LOGGER.debug("Resolved host addresses: {}", resolvedAddresses);
      return resolvedAddresses;
    } catch (UnknownHostException e) {
      LOGGER.error("No addresses found for service: {}.", host);
      throw new RuntimeException(e);
    }
  }
}
