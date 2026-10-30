/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.managedscript.local;

import io.camunda.connector.jackson.ConnectorsObjectMapperSupplier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Registers the {@code local} provider when it is the one the runtime is configured with. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(
    prefix = "camunda.connector.managed-code",
    name = "provider",
    havingValue = LocalManagedCodeProvider.NAME)
@EnableConfigurationProperties(LocalManagedCodeProperties.class)
public class LocalManagedCodeConfiguration {

  @Bean
  LocalManagedCodeProvider localManagedCodeProvider(LocalManagedCodeProperties properties) {
    return new LocalManagedCodeProvider(
        properties.directory(), ConnectorsObjectMapperSupplier.getCopy());
  }
}
