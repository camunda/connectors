/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.managedscript.gcp;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.IdTokenProvider;
import io.camunda.connector.jackson.ConnectorsObjectMapperSupplier;
import io.camunda.connector.managedscript.api.ManagedCodeSettings;
import java.io.IOException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

/**
 * Registers the {@code gcp} provider when managed code is enabled with it. The module is not in the
 * default bundle: a distribution that offers managed scripts on GCP adds it to the classpath.
 */
@AutoConfiguration(beforeName = "io.camunda.connector.managedscript.ManagedCodeAutoConfiguration")
@ConditionalOnBooleanProperty("camunda.connector.managed-code.enabled")
@ConditionalOnProperty(
    prefix = "camunda.connector.managed-code",
    name = "provider",
    havingValue = GcpCloudRunManagedCodeProvider.NAME)
@EnableConfigurationProperties(GcpManagedCodeProperties.class)
public class GcpManagedCodeAutoConfiguration {

  @Bean
  GcpCloudRunManagedCodeProvider gcpCloudRunManagedCodeProvider(
      Environment environment,
      GcpManagedCodeProperties gcp,
      ObjectProvider<GcpClients.ApplicationDefault> applicationDefault)
      throws IOException {
    // Bound here, not taken from the connector, which this module does not depend on.
    final var shared =
        Binder.get(environment).bindOrCreate(ManagedCodeSettings.PREFIX, ManagedCodeSettings.class);
    // Before the credentials are loaded, so that a timeout too long fails startup by itself.
    GcpCloudRunManagedCodeProvider.functionTimeoutSeconds(shared.executionTimeout());
    final var credentials =
        GcpClients.deployer(
            applicationDefault
                .getIfAvailable(() -> GoogleCredentials::getApplicationDefault)
                .load(),
            gcp.deployerServiceAccount());
    return new GcpCloudRunManagedCodeProvider(
        GcpClients.functions(credentials),
        GcpClients.http(),
        (IdTokenProvider) credentials,
        ConnectorsObjectMapperSupplier.getCopy(),
        shared.deploymentNamePrefix(),
        gcp,
        shared.executionTimeout());
  }
}
