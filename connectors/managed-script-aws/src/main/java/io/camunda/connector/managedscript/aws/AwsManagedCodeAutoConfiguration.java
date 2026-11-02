/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.managedscript.aws;

import io.camunda.connector.jackson.ConnectorsObjectMapperSupplier;
import io.camunda.connector.managedscript.ManagedCodeAutoConfiguration;
import io.camunda.connector.managedscript.ManagedCodeProperties;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Registers the {@code aws} provider when managed code is enabled with it. The module is not in the
 * default bundle: a distribution that offers managed scripts on AWS adds it to the classpath.
 */
@AutoConfiguration(before = ManagedCodeAutoConfiguration.class)
@ConditionalOnBooleanProperty("camunda.connector.managed-code.enabled")
@ConditionalOnProperty(
    prefix = "camunda.connector.managed-code",
    name = "provider",
    havingValue = AwsLambdaManagedCodeProvider.NAME)
@EnableConfigurationProperties({ManagedCodeProperties.class, AwsManagedCodeProperties.class})
public class AwsManagedCodeAutoConfiguration {

  @Bean
  AwsLambdaManagedCodeProvider awsLambdaManagedCodeProvider(
      ManagedCodeProperties shared, AwsManagedCodeProperties aws) {
    final var functionTimeoutSeconds =
        AwsLambdaManagedCodeProvider.functionTimeoutSeconds(shared.executionTimeout());
    return new AwsLambdaManagedCodeProvider(
        AwsClients.lambda(aws.region()),
        AwsClients.invoker(aws.region(), functionTimeoutSeconds),
        AwsClients.logs(aws.region()),
        ConnectorsObjectMapperSupplier.getCopy(),
        shared.deploymentNamePrefix(),
        aws,
        shared.executionTimeout());
  }
}
