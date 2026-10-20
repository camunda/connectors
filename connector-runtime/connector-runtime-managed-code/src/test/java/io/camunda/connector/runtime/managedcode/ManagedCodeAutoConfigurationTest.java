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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import io.camunda.connector.api.annotation.OutboundConnector;
import io.camunda.connector.runtime.tenant.PhysicalTenantClientSelector;
import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class ManagedCodeAutoConfigurationTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(ManagedCodeAutoConfiguration.class));

  @Test
  void disabledByDefault() {
    contextRunner
        .withPropertyValues("camunda.connector.managed-code.provider=local")
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              assertThat(context).doesNotHaveBean(ManagedCodeProvider.class);
              assertThat(context).doesNotHaveBean(DeploymentRegistry.class);
              assertThat(context).doesNotHaveBean(ManagedScriptJobHandler.class);
            });
  }

  @Test
  void enabledWithLocalProvider() {
    contextRunner
        .withPropertyValues(
            "camunda.connector.managed-code.enabled=true",
            "camunda.connector.managed-code.provider=local",
            "camunda.connector.managed-code.provisioning-timeout=1m",
            "camunda.connector.managed-code.execution-timeout=20s",
            "camunda.connector.managed-code.local.directory=/tmp/managed-code-test",
            "camunda.connector.managed-code.local.install-timeout=3m")
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              assertThat(context).hasSingleBean(LocalManagedCodeProvider.class);
              assertThat(context).hasSingleBean(DeploymentRegistry.class);
              assertThat(context).hasSingleBean(ManagedScriptJobHandler.class);
              final var properties = context.getBean(ManagedCodeProperties.class);
              assertThat(properties.local().directory())
                  .isEqualTo(Path.of("/tmp/managed-code-test"));
              assertThat(properties.local().installTimeout()).isEqualTo(Duration.ofMinutes(3));
              assertThat(properties.provisioningConcurrency()).isEqualTo(2);
              assertThat(properties.deploymentNamePrefix()).isEqualTo("camunda-ms-");
            });
  }

  @Test
  void servesTheReservedJobTypeWithAPrototypeConnectorPerPhysicalTenant() {
    contextRunner
        .withPropertyValues(
            "camunda.connector.managed-code.enabled=true",
            "camunda.connector.managed-code.provider=local")
        .withBean(
            PhysicalTenantClientSelector.class, () -> mock(PhysicalTenantClientSelector.class))
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              assertThat(context.getBean(ManagedScriptFunction.class))
                  .isNotSameAs(context.getBean(ManagedScriptFunction.class));
              final var annotation =
                  ManagedScriptFunction.class.getAnnotation(OutboundConnector.class);
              assertThat(annotation.type()).isEqualTo("io.camunda:managed-script:1");
              assertThat(annotation.inputVariables()).containsExactly("scriptInput");
              assertThat(annotation.withLease()).isTrue();
            });
  }

  @Test
  void usesTheConfiguredDeploymentNamePrefix() {
    contextRunner
        .withPropertyValues(
            "camunda.connector.managed-code.enabled=true",
            "camunda.connector.managed-code.provider=local",
            "camunda.connector.managed-code.deployment-name-prefix=camunda-ms-c1-")
        .run(
            context ->
                assertThat(context.getBean(ManagedCodeProperties.class).deploymentNamePrefix())
                    .isEqualTo("camunda-ms-c1-"));
  }

  @Test
  void rejectsAnInvalidDeploymentNamePrefix() {
    contextRunner
        .withPropertyValues(
            "camunda.connector.managed-code.enabled=true",
            "camunda.connector.managed-code.provider=local",
            "camunda.connector.managed-code.deployment-name-prefix=Cluster_1")
        .run(
            context ->
                assertThat(context)
                    .getFailure()
                    .rootCause()
                    .hasMessageContaining("deployment-name-prefix 'Cluster_1'"));
  }

  @Test
  void failsWithoutProvider() {
    contextRunner
        .withPropertyValues("camunda.connector.managed-code.enabled=true")
        .run(
            context ->
                assertThat(context)
                    .getFailure()
                    .rootCause()
                    .hasMessageContaining("provider must be set"));
  }

  @Test
  void failsForUnknownProvider() {
    contextRunner
        .withPropertyValues(
            "camunda.connector.managed-code.enabled=true",
            "camunda.connector.managed-code.provider=gcp")
        .run(
            context ->
                assertThat(context)
                    .getFailure()
                    .rootCause()
                    .hasMessageContaining("No managed-code provider 'gcp'"));
  }

  @Test
  void awsProviderRequiresAnExecutionRole() {
    contextRunner
        .withPropertyValues(
            "camunda.connector.managed-code.enabled=true",
            "camunda.connector.managed-code.provider=aws",
            "camunda.connector.managed-code.aws.region=eu-central-1")
        .run(
            context ->
                assertThat(context)
                    .getFailure()
                    .rootCause()
                    .hasMessageContaining("aws.execution-role-arn must be set"));
  }

  @Test
  void enabledWithAwsProvider() {
    contextRunner
        .withPropertyValues(
            "camunda.connector.managed-code.enabled=true",
            "camunda.connector.managed-code.provider=aws",
            "camunda.connector.managed-code.aws.region=eu-central-1",
            "camunda.connector.managed-code.aws.execution-role-arn=arn:aws:iam::000000000000:role/test",
            "camunda.connector.managed-code.aws.memory-mb=512")
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              assertThat(context).hasSingleBean(AwsLambdaManagedCodeProvider.class);
              assertThat(context).doesNotHaveBean(LocalManagedCodeProvider.class);
              assertThat(context.getBean(ManagedCodeProperties.class).aws().memoryMb())
                  .isEqualTo(512);
            });
  }

  @Test
  void usesProviderBeanMatchingTheConfiguredName() {
    contextRunner
        .withPropertyValues(
            "camunda.connector.managed-code.enabled=true",
            "camunda.connector.managed-code.provider=controlled")
        .withBean(ManagedCodeProvider.class, ControlledProvider::new)
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              assertThat(context).doesNotHaveBean(LocalManagedCodeProvider.class);
              assertThat(context).hasSingleBean(ManagedScriptJobHandler.class);
            });
  }

  @Test
  void contributedProviderCoexistsWithTheBuiltInOne() {
    contextRunner
        .withPropertyValues(
            "camunda.connector.managed-code.enabled=true",
            "camunda.connector.managed-code.provider=local")
        .withBean(ControlledProvider.class, ControlledProvider::new)
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              assertThat(context).hasSingleBean(LocalManagedCodeProvider.class);
              assertThat(context).hasSingleBean(ControlledProvider.class);
              assertThat(context).hasSingleBean(ManagedScriptJobHandler.class);
            });
  }

  @Test
  void failsForDuplicateProviderNames() {
    contextRunner
        .withPropertyValues(
            "camunda.connector.managed-code.enabled=true",
            "camunda.connector.managed-code.provider=controlled")
        .withBean("first", ControlledProvider.class, ControlledProvider::new)
        .withBean("second", ControlledProvider.class, ControlledProvider::new)
        .run(
            context ->
                assertThat(context)
                    .getFailure()
                    .rootCause()
                    .hasMessageContaining("2 managed-code provider beans are named 'controlled'"));
  }
}
