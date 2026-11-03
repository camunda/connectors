/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.managedscript.runner;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.connector.managedscript.api.ManagedCodeProvider;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;

class RunnerProviderAutoConfigurationTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(RunnerProviderAutoConfiguration.class));

  private final ApplicationContextRunner selected =
      contextRunner.withPropertyValues(
          "camunda.connector.managed-code.enabled=true",
          "camunda.connector.managed-code.provider=runner",
          "camunda.connector.managed-code.runner.url=http://runner.sandbox-3f9a1c8e.svc.cluster.local",
          "camunda.connector.managed-code.runner.token-path=/var/run/secrets/scriptrunner/token",
          "camunda.connector.managed-code.runner.runtimes.python=python-3.13");

  @Test
  void providesTheRunnerProviderWhenManagedCodeSelectsIt() {
    selected.run(context -> {
      assertThat(context).hasNotFailed();
      assertThat(context.getBean(ManagedCodeProvider.class).name()).isEqualTo("runner");
      final var properties = context.getBean(RunnerProviderProperties.class);
      assertThat(properties.url()).isEqualTo("http://runner.sandbox-3f9a1c8e.svc.cluster.local");
      assertThat(properties.tokenPath()).isEqualTo("/var/run/secrets/scriptrunner/token");
      assertThat(properties.runtimes()).isEqualTo(Map.of("python", "python-3.13"));
    });
  }

  @Test
  void providesNothingForAnotherProvider() {
    contextRunner
        .withPropertyValues(
            "camunda.connector.managed-code.enabled=true",
            "camunda.connector.managed-code.provider=gcp")
        .run(context -> {
          assertThat(context).hasNotFailed();
          assertThat(context).doesNotHaveBean(ManagedCodeProvider.class);
        });
  }

  @Test
  void providesNothingWhenManagedCodeIsDisabled() {
    contextRunner
        .withPropertyValues("camunda.connector.managed-code.provider=runner")
        .run(context -> {
          assertThat(context).hasNotFailed();
          assertThat(context).doesNotHaveBean(ManagedCodeProvider.class);
        });
  }

  @Test
  void bindsTheEnvironmentVariablesThatCamundaOperatorSets() {
    contextRunner
        .withInitializer(context ->
            context
                .getEnvironment()
                .getPropertySources()
                .addFirst(
                    new SystemEnvironmentPropertySource(
                        StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME,
                        Map.of(
                            "CAMUNDA_CONNECTOR_MANAGEDCODE_ENABLED", "true",
                            "CAMUNDA_CONNECTOR_MANAGEDCODE_PROVIDER", "runner",
                            "CAMUNDA_CONNECTOR_MANAGEDCODE_RUNNER_URL", "http://runner.example",
                            "CAMUNDA_CONNECTOR_MANAGEDCODE_RUNNER_TOKENPATH", "/token",
                            "CAMUNDA_CONNECTOR_MANAGEDCODE_RUNNER_RUNTIMES_PYTHON",
                                "python-3.13"))))
        .run(context -> {
          assertThat(context).hasNotFailed();
          final var properties = context.getBean(RunnerProviderProperties.class);
          assertThat(properties.url()).isEqualTo("http://runner.example");
          assertThat(properties.tokenPath()).isEqualTo("/token");
          assertThat(properties.runtimes()).isEqualTo(Map.of("python", "python-3.13"));
        });
  }

  @Test
  void requiresTheUrl() {
    selected
        .withPropertyValues("camunda.connector.managed-code.runner.url=")
        .run(context ->
            assertThat(context)
                .getFailure()
                .rootCause()
                .hasMessageContaining("camunda.connector.managed-code.runner.url"));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "runner.sandbox.svc",
        "ftp://runner.sandbox.svc",
        "http://user:password@runner.sandbox.svc",
        "http://runner.sandbox.svc?target=elsewhere",
        "http://runner.sandbox.svc#fragment",
        "http:///no-host"
      })
  void rejectsAUrlThatIsNotAPlainHttpUrlOfAHost(String url) {
    selected
        .withPropertyValues("camunda.connector.managed-code.runner.url=" + url)
        .run(context ->
            assertThat(context)
                .getFailure()
                .rootCause()
                .hasMessageContaining("camunda.connector.managed-code.runner.url"));
  }

  @Test
  void requiresTheTokenPath() {
    selected
        .withPropertyValues("camunda.connector.managed-code.runner.token-path=")
        .run(context ->
            assertThat(context)
                .getFailure()
                .rootCause()
                .hasMessageContaining("camunda.connector.managed-code.runner.token-path"));
  }
}
