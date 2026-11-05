/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.managedscript.aws;

import io.camunda.connector.jackson.ConnectorsObjectMapperSupplier;
import io.camunda.connector.managedscript.api.ManagedCodeSettings;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

/**
 * Registers the {@code aws} provider when managed code is enabled with it. The module is not in the
 * default bundle: a distribution that offers managed scripts on AWS adds it to the classpath.
 */
@AutoConfiguration(beforeName = "io.camunda.connector.managedscript.ManagedCodeAutoConfiguration")
@ConditionalOnBooleanProperty("camunda.connector.managed-code.enabled")
@ConditionalOnProperty(
    prefix = "camunda.connector.managed-code",
    name = "provider",
    havingValue = AwsLambdaManagedCodeProvider.NAME)
@EnableConfigurationProperties(AwsManagedCodeProperties.class)
public class AwsManagedCodeAutoConfiguration {

  @Bean
  AwsLambdaManagedCodeProvider awsLambdaManagedCodeProvider(
      Environment environment, AwsManagedCodeProperties aws) {
    // Bound here, not taken from the connector, which this module does not depend on.
    final var shared =
        Binder.get(environment).bindOrCreate(ManagedCodeSettings.PREFIX, ManagedCodeSettings.class);
    final var functionTimeoutSeconds =
        AwsLambdaManagedCodeProvider.functionTimeoutSeconds(shared.executionTimeout());
    final var credentials = AwsClients.credentials();
    return new AwsLambdaManagedCodeProvider(
        AwsClients.lambda(aws.region(), credentials),
        AwsClients.invoker(aws.region(), functionTimeoutSeconds, credentials),
        credentials,
        AwsClients.logs(aws.region(), credentials),
        ConnectorsObjectMapperSupplier.getCopy(),
        shared.deploymentNamePrefix(),
        aws,
        shared.executionTimeout());
  }
}
