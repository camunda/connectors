/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.managedscript;

import io.camunda.connector.jackson.ConnectorsObjectMapperSupplier;
import io.camunda.connector.managedscript.api.ManagedCodeProvider;
import io.camunda.connector.managedscript.local.LocalManagedCodeConfiguration;
import io.camunda.connector.runtime.tenant.PhysicalTenantClientSelector;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Scope;

@AutoConfiguration
@ConditionalOnProperty(
    prefix = "camunda.connector.managed-code",
    name = "enabled",
    havingValue = "true")
@EnableConfigurationProperties(ManagedCodeProperties.class)
@Import(LocalManagedCodeConfiguration.class)
public class ManagedCodeAutoConfiguration {

  private static final int RESOURCE_CACHE_SIZE = 256;

  @Bean
  ManagedScriptJobHandler managedScriptJobHandler(
      ObjectProvider<ManagedCodeProvider> providers, ManagedCodeProperties properties) {
    return new ManagedScriptJobHandler(
        requireProvider(providers, properties),
        new ScriptResources(RESOURCE_CACHE_SIZE),
        ConnectorsObjectMapperSupplier.getCopy(),
        properties.executionTimeout(),
        properties.provisioningRetryBackoff());
  }

  /**
   * Served by the connector runtime like any outbound connector. Prototype-scoped so that the
   * runtime builds its instance per physical tenant through this method; every instance shares the
   * handler, and with it the executors.
   */
  @Bean
  @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
  public ManagedScriptFunction managedScriptFunction(
      ManagedScriptJobHandler handler, PhysicalTenantClientSelector clients) {
    return new ManagedScriptFunction(handler, clients);
  }

  /**
   * Selects the provider bean named by {@code camunda.connector.managed-code.provider}. Provider
   * beans come from separate modules, or from a jar added to the classpath.
   */
  private static ManagedCodeProvider requireProvider(
      ObjectProvider<ManagedCodeProvider> providers, ManagedCodeProperties properties) {
    if (properties.provider().isBlank()) {
      throw new IllegalStateException(
          "camunda.connector.managed-code.provider must be set when managed code is enabled");
    }
    final var matching =
        providers
            .orderedStream()
            .filter(provider -> properties.provider().equals(provider.name()))
            .toList();
    if (matching.isEmpty()) {
      throw new IllegalStateException(
          "No managed-code provider '%s' is available: add the module of the provider to the"
                  .formatted(properties.provider())
              + " runtime's classpath");
    }
    if (matching.size() > 1) {
      throw new IllegalStateException(
          "%d managed-code provider beans are named '%s'"
              .formatted(matching.size(), properties.provider()));
    }
    return matching.getFirst();
  }
}
