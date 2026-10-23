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
import io.camunda.connector.runtime.tenant.PhysicalTenantClientSelector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Scope;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.cloudwatchlogs.CloudWatchLogsClient;
import software.amazon.awssdk.services.lambda.LambdaClient;

@AutoConfiguration
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

  @Bean
  @ConditionalOnProperty(
      prefix = "camunda.connector.managed-code",
      name = "provider",
      havingValue = AwsLambdaManagedCodeProvider.NAME)
  public AwsLambdaManagedCodeProvider awsLambdaManagedCodeProvider(
      ManagedCodeProperties properties) {
    final var aws = properties.aws();
    if (aws.executionRoleArn() == null) {
      throw new IllegalStateException(
          "camunda.connector.managed-code.aws.execution-role-arn must be set for the 'aws' provider");
    }
    final var builder =
        LambdaClient.builder()
            .overrideConfiguration(
                // the synchronous invocation must outlive the function timeout
                o -> o.apiCallAttemptTimeout(properties.executionTimeout().plusSeconds(30)));
    final var logs = CloudWatchLogsClient.builder();
    if (aws.region() != null) {
      builder.region(Region.of(aws.region()));
      logs.region(Region.of(aws.region()));
    }
    LOG.info(
        "Managed-code provider 'aws' is enabled: scripts are deployed as AWS Lambda functions"
            + " named '{}*'",
        properties.deploymentNamePrefix());
    return new AwsLambdaManagedCodeProvider(
        builder.build(),
        logs.build(),
        objectMapper,
        aws.executionRoleArn(),
        aws.memoryMb(),
        aws.logRetentionDays(),
        properties.executionTimeout(),
        properties.provisioningTimeout(),
        aws.pollInterval());
  }

  @Bean
  @ConditionalOnProperty(
      prefix = "camunda.connector.managed-code",
      name = "provider",
      havingValue = GcpCloudRunManagedCodeProvider.NAME)
  public GcpCloudRunManagedCodeProvider gcpCloudRunManagedCodeProvider(
      ManagedCodeProperties properties) {
    final var gcp = properties.gcp();
    if (gcp.project() == null || gcp.region() == null || gcp.functionServiceAccount() == null) {
      throw new IllegalStateException(
          "camunda.connector.managed-code.gcp.project, .region and .function-service-account must"
              + " be set for the 'gcp' provider");
    }
    final var location = "projects/%s/locations/%s".formatted(gcp.project(), gcp.region());
    LOG.info(
        "Managed-code provider 'gcp' is enabled: scripts are deployed as Cloud Run functions in {}"
            + " named '{}*'",
        location,
        properties.deploymentNamePrefix());
    return new GcpCloudRunManagedCodeProvider(
        GoogleCloudRunFunctionsApi.create(location, gcp.deployerServiceAccount()),
        objectMapper,
        location,
        gcp.functionServiceAccount(),
        gcp.memoryMb(),
        gcp.maxInstanceCount(),
        properties.executionTimeout(),
        properties.provisioningTimeout(),
        gcp.pollInterval());
  }

  @Bean(destroyMethod = "close")
  public DeploymentRegistry managedCodeDeploymentRegistry(
      ObjectProvider<ManagedCodeProvider> providers, ManagedCodeProperties properties) {
    return new DeploymentRegistry(
        requireProvider(providers, properties), properties.provisioningConcurrency());
  }

  @Bean
  ManagedScriptJobHandler managedScriptJobHandler(
      ObjectProvider<ManagedCodeProvider> providers,
      DeploymentRegistry registry,
      ManagedCodeProperties properties) {
    return new ManagedScriptJobHandler(
        requireProvider(providers, properties),
        registry,
        new ScriptResources(RESOURCE_CACHE_SIZE),
        objectMapper,
        properties.provisioningTimeout(),
        properties.provisioningRetryBackoff(),
        properties.deploymentNamePrefix());
  }

  /**
   * Served by the connector runtime like any outbound connector. Prototype-scoped so that the
   * runtime builds its instance per physical tenant through this method; every instance shares the
   * handler, and with it the deployment registry.
   */
  @Bean
  @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
  public ManagedScriptFunction managedScriptFunction(
      ManagedScriptJobHandler handler, PhysicalTenantClientSelector clients) {
    return new ManagedScriptFunction(handler, clients);
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
              + " the built-in providers are 'local', 'aws' and 'gcp'");
    }
    final var matching =
        providers
            .orderedStream()
            .filter(provider -> properties.provider().equals(provider.name()))
            .toList();
    if (matching.isEmpty()) {
      throw new IllegalStateException(
          "No managed-code provider '%s' is available; the built-in providers are 'local', 'aws' and 'gcp'"
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
