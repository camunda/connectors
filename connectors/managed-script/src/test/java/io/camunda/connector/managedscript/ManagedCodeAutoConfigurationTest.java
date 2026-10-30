/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.managedscript;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import io.camunda.connector.managedscript.api.ManagedCodeProvider;
import io.camunda.connector.managedscript.local.LocalManagedCodeProperties;
import io.camunda.connector.managedscript.local.LocalManagedCodeProvider;
import io.camunda.connector.runtime.core.ConnectorConfigurationUtil;
import io.camunda.connector.runtime.tenant.PhysicalTenantClientSelector;
import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class ManagedCodeAutoConfigurationTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(ManagedCodeAutoConfiguration.class))
          .withBean(
              PhysicalTenantClientSelector.class, () -> mock(PhysicalTenantClientSelector.class));

  private final ApplicationContextRunner enabled =
      contextRunner
          .withPropertyValues(
              "camunda.connector.managed-code.enabled=true",
              "camunda.connector.managed-code.provider=fake")
          .withBean("fakeProvider", ManagedCodeProvider.class, FakeProvider::new);

  @Test
  void isDisabledByDefault() {
    contextRunner
        .withPropertyValues("camunda.connector.managed-code.provider=fake")
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              assertThat(context).doesNotHaveBean(ManagedScriptJobHandler.class);
              assertThat(context).doesNotHaveBean(ManagedScriptFunction.class);
            });
  }

  @Test
  void servesTheReservedJobTypeWithAPrototypeConnectorFetchingOnlyTheScriptInput() {
    enabled.run(
        context -> {
          assertThat(context).hasNotFailed();
          assertThat(context.getBean(ManagedScriptFunction.class))
              .isNotSameAs(context.getBean(ManagedScriptFunction.class));
          final var configuration =
              ConnectorConfigurationUtil.getOutboundConnectorConfiguration(
                  ManagedScriptFunction.class);
          assertThat(configuration.type()).isEqualTo("io.camunda:managed-script:1");
          assertThat(configuration.inputVariables()).containsExactly("scriptInput");
        });
  }

  @Test
  void registersTheLocalProviderOnlyWhenItIsConfigured(@TempDir Path directory) {
    enabled
        .withPropertyValues(
            "camunda.connector.managed-code.provider=local",
            "camunda.connector.managed-code.local.directory=" + directory)
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              assertThat(context).hasSingleBean(LocalManagedCodeProvider.class);
              assertThat(context).hasSingleBean(ManagedScriptJobHandler.class);
            });
    enabled.run(context -> assertThat(context).doesNotHaveBean(LocalManagedCodeProvider.class));
  }

  @Test
  void keepsTheWrapperFilesOfTheLocalProviderInTheDirectoryOfTheUser() {
    assertThat(new LocalManagedCodeProperties(null).directory())
        .isEqualTo(Path.of(System.getProperty("user.home"), ".camunda", "managed-script"));
  }

  @Test
  void hasDefaultsForEveryProperty() {
    enabled.run(
        context -> {
          final var properties = context.getBean(ManagedCodeProperties.class);
          assertThat(properties.deploymentNamePrefix()).isEqualTo("camunda-ms-");
          assertThat(properties.executionTimeout()).isEqualTo(Duration.ofSeconds(30));
          assertThat(properties.provisioningRetryBackoff()).isEqualTo(Duration.ofSeconds(10));
        });
  }

  @Test
  void acceptsTheFixedLengthPrefixOfSaas() {
    final var prefix = "ce-0123456789abcdef01234567-";
    enabled
        .withPropertyValues("camunda.connector.managed-code.deployment-name-prefix=" + prefix)
        .run(
            context ->
                assertThat(context.getBean(ManagedCodeProperties.class).deploymentNamePrefix())
                    .isEqualTo(prefix));
  }

  @Test
  void rejectsAPrefixThatLeavesNoRoomForTheExecutorName() {
    enabled
        .withPropertyValues(
            "camunda.connector.managed-code.deployment-name-prefix=" + "a".repeat(49))
        .run(
            context ->
                assertThat(context).getFailure().rootCause().hasMessageContaining("at most 48"));
  }

  @Test
  void rejectsAnInvalidDeploymentNamePrefix() {
    enabled
        .withPropertyValues("camunda.connector.managed-code.deployment-name-prefix=Cluster_1")
        .run(
            context ->
                assertThat(context)
                    .getFailure()
                    .rootCause()
                    .hasMessageContaining("deployment-name-prefix 'Cluster_1'"));
  }

  @Test
  void failsStartupWithoutAProviderName() {
    contextRunner
        .withPropertyValues("camunda.connector.managed-code.enabled=true")
        .run(
            context ->
                assertThat(context)
                    .getFailure()
                    .rootCause()
                    .hasMessageContaining("camunda.connector.managed-code.provider must be set"));
  }

  @Test
  void failsStartupWhenNoProviderHasTheConfiguredName() {
    contextRunner
        .withPropertyValues(
            "camunda.connector.managed-code.enabled=true",
            "camunda.connector.managed-code.provider=aws")
        .run(
            context ->
                assertThat(context)
                    .getFailure()
                    .rootCause()
                    .hasMessageContaining("No managed-code provider 'aws' is available"));
  }

  @Test
  void usesTheProviderWithTheConfiguredName() {
    final var chosen = new FakeProvider("chosen");
    contextRunner
        .withPropertyValues(
            "camunda.connector.managed-code.enabled=true",
            "camunda.connector.managed-code.provider=chosen")
        .withBean("otherProvider", ManagedCodeProvider.class, () -> new FakeProvider("other"))
        .withBean("chosenProvider", ManagedCodeProvider.class, () -> chosen)
        .run(context -> assertThat(context).hasNotFailed());
  }

  @Test
  void failsStartupForTwoProvidersWithTheSameName() {
    enabled
        .withBean("secondProvider", ManagedCodeProvider.class, FakeProvider::new)
        .run(
            context ->
                assertThat(context)
                    .getFailure()
                    .rootCause()
                    .hasMessageContaining("2 managed-code provider beans are named 'fake'"));
  }
}
