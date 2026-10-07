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
package io.camunda.connector.runtime.managedcode;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.client.spring.configuration.CamundaAutoConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@AutoConfigureBefore(CamundaAutoConfiguration.class)
@ConditionalOnProperty(
    prefix = "camunda.connector.managed-code",
    name = "enabled",
    havingValue = "true")
@EnableConfigurationProperties(ManagedCodeProperties.class)
public class ManagedCodeAutoConfiguration {

  private static final Logger LOG = LoggerFactory.getLogger(ManagedCodeAutoConfiguration.class);
  private static final int RESOURCE_CACHE_SIZE = 256;

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Bean
  @ConditionalOnProperty(
      prefix = "camunda.connector.managed-code",
      name = "provider",
      havingValue = LocalManagedCodeProvider.NAME)
  public LocalManagedCodeProvider localManagedCodeProvider(ManagedCodeProperties properties) {
    LOG.warn(
        "Managed-code provider 'local' is enabled: scripts are deployed to {} and run in local"
            + " Node.js or Python subprocesses with the Connector Runtime operating-system"
            + " identity. This is for trusted development only and is not a sandbox.",
        properties.local().directory());
    final var interpreters = LocalInterpreterDiscovery.discover();
    return new LocalManagedCodeProvider(
        new LocalProcessScriptExecutor(objectMapper, interpreters, properties.executionTimeout()),
        interpreters,
        objectMapper,
        properties.local().directory(),
        properties.local().installTimeout());
  }

  @Bean(destroyMethod = "close")
  public DeploymentRegistry managedCodeDeploymentRegistry(
      ObjectProvider<ManagedCodeProvider> providers, ManagedCodeProperties properties) {
    return new DeploymentRegistry(
        requireProvider(providers, properties), properties.provisioningConcurrency());
  }

  @Bean(destroyMethod = "shutdown")
  public ManagedScriptJobWorker managedScriptJobWorker(
      ObjectProvider<ManagedCodeProvider> providers,
      DeploymentRegistry registry,
      ManagedCodeProperties properties) {
    final var handler =
        new ManagedScriptJobHandler(
            requireProvider(providers, properties),
            registry,
            new ScriptResources(RESOURCE_CACHE_SIZE),
            objectMapper,
            properties.provisioningTimeout(),
            properties.provisioningRetryBackoff());
    return new ManagedScriptJobWorker(handler, properties);
  }

  /**
   * Selects the provider bean named by {@code camunda.connector.managed-code.provider}. Several
   * provider beans may coexist, for example the built-in one and one contributed by a custom jar.
   */
  private static ManagedCodeProvider requireProvider(
      ObjectProvider<ManagedCodeProvider> providers, ManagedCodeProperties properties) {
    if (properties.provider().isBlank()) {
      throw new IllegalStateException(
          "camunda.connector.managed-code.provider must be set when managed code is enabled;"
              + " the only built-in provider is 'local'");
    }
    final var matching =
        providers
            .orderedStream()
            .filter(provider -> properties.provider().equals(provider.name()))
            .toList();
    if (matching.isEmpty()) {
      throw new IllegalStateException(
          "No managed-code provider '%s' is available; the only built-in provider is 'local'"
              .formatted(properties.provider()));
    }
    if (matching.size() > 1) {
      throw new IllegalStateException(
          "%d managed-code provider beans are named '%s'"
              .formatted(matching.size(), properties.provider()));
    }
    return matching.getFirst();
  }
}
