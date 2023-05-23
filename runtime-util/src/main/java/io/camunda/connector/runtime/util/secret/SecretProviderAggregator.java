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
package io.camunda.connector.runtime.util.secret;

import io.camunda.connector.api.secret.SecretProvider;
import io.camunda.connector.runtime.util.discovery.SPISecretProviderDiscovery;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A wrapping secret provider that encapsulates a strategy for selecting a {@link SecretProvider}
 * from a list of available providers.
 */
public class SecretProviderAggregator implements SecretProvider {

  private static final Logger LOG = LoggerFactory.getLogger(SecretProviderAggregator.class);

  protected final List<SecretProvider> secretProviders;

  public SecretProviderAggregator(List<SecretProvider> secretProvidersOverride) {
    if (secretProvidersOverride == null || secretProvidersOverride.isEmpty()) {
      LOG.debug("No secret providers override provided, using SPI discovery");
      this.secretProviders = lookupSecretProviders();
    } else {
      LOG.debug("Using provided secret providers override");
      this.secretProviders = secretProvidersOverride;
    }
  }

  public SecretProviderAggregator() {
    LOG.debug("No secret providers override provided, using SPI discovery");
    this.secretProviders = lookupSecretProviders();
  }

  /**
   * Resolves the secret from the given providers.
   *
   * @param secretName the name of the secret
   * @return the secret value
   */
  public String getSecret(String secretName) {
    for (SecretProvider secretProvider : secretProviders) {
      String secret = secretProvider.getSecret(secretName);
      if (secret != null) {
        LOG.debug(
            "Resolved secret '{}' from provider '{}'",
            secretName,
            secretProvider.getClass().getName());
        return secret;
      }
    }
    LOG.debug("Could not resolve secret '{}'", secretName);
    return null;
  }

  /**
   * Performs the look-up of secret providers available in the runtime environment. Default
   * implementation uses {@link SPISecretProviderDiscovery}.
   *
   * @return the list of secret providers available in the runtime environment
   */
  protected List<SecretProvider> lookupSecretProviders() {
    List<SecretProvider> spiSecretProviders = SPISecretProviderDiscovery.discover();
    if (spiSecretProviders.isEmpty()) {
      LOG.debug("No secret providers discovered via SPI. Falling back to environment variables");
      return List.of(System::getenv);
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

  List<SecretProvider> getSecretProviders() {
    return Collections.unmodifiableList(secretProviders);
  }
}
