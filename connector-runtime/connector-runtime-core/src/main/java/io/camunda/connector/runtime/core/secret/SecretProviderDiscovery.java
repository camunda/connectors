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
package io.camunda.connector.runtime.core.secret;

import io.camunda.connector.api.secret.SecretProvider;
import io.camunda.connector.runtime.core.discovery.SPISecretProviderDiscovery;
import java.util.List;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SecretProviderDiscovery {

  private static final Logger LOG = LoggerFactory.getLogger(SecretProviderDiscovery.class);

  /**
   * Performs the look-up of secret providers available in the runtime environment. Default
   * implementation uses {@link SPISecretProviderDiscovery}.
   *
   * @return the list of secret providers available in the runtime environment
   */
  public static List<SecretProvider> discoverSecretProviders() {
    List<SecretProvider> spiSecretProviders = SPISecretProviderDiscovery.discover();
    if (spiSecretProviders.isEmpty()) {
      LOG.debug("No secret providers discovered via SPI.");
      return List.of();
    } else {
      LOG.debug(
          "Discovered {} secret providers via SPI: {}",
          spiSecretProviders.size(),
          spiSecretProviders.stream()
              .map(p -> p.getClass().getName())
              .collect(Collectors.joining(", ")));
      return spiSecretProviders;
    }
  }
}
