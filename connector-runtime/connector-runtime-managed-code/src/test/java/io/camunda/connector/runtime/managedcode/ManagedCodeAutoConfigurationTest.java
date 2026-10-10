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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.worker.JobWorkerBuilderStep1;
import io.camunda.client.api.worker.JobWorkerBuilderStep1.JobWorkerBuilderStep2;
import io.camunda.client.api.worker.JobWorkerBuilderStep1.JobWorkerBuilderStep3;
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
              assertThat(context).doesNotHaveBean(ManagedScriptJobWorker.class);
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
              assertThat(context).hasSingleBean(ManagedScriptJobWorker.class);
              final var properties = context.getBean(ManagedCodeProperties.class);
              assertThat(properties.local().directory())
                  .isEqualTo(Path.of("/tmp/managed-code-test"));
              assertThat(properties.local().installTimeout()).isEqualTo(Duration.ofMinutes(3));
              assertThat(properties.provisioningConcurrency()).isEqualTo(2);
              assertThat(ManagedScriptJobWorker.jobTimeout(properties))
                  .isEqualTo(Duration.ofSeconds(90));
            });
  }

  @Test
  void opensOneLeasedWorkerPerClientWithTimeoutCoveringProvisioning() {
    contextRunner
        .withPropertyValues(
            "camunda.connector.managed-code.enabled=true",
            "camunda.connector.managed-code.provider=local",
            "camunda.connector.managed-code.invocation-concurrency=3")
        .run(
            context -> {
              final var client = mock(CamundaClient.class, RETURNS_DEEP_STUBS);
              when(client.getConfiguration().getPhysicalTenantId()).thenReturn("tenant-a");
              final var step1 = mock(JobWorkerBuilderStep1.class);
              final var step2 = mock(JobWorkerBuilderStep2.class);
              final var step3 = mock(JobWorkerBuilderStep3.class, RETURNS_SELF);
              when(client.newWorker()).thenReturn(step1);
              when(step1.jobType(anyString())).thenReturn(step2);
              when(step2.handler(any())).thenReturn(step3);

              context.getBean(ManagedScriptJobWorker.class).onStart(client, "default");

              verify(step1).jobType("io.camunda:managed-script:1");
              verify(step3).timeout(Duration.ofSeconds(160));
              verify(step3).maxJobsActive(3);
              verify(step3).withLease(true);
              verify(step3).name("managed-script-worker");
              verify(step3).open();
            });
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
            "camunda.connector.managed-code.provider=aws")
        .run(
            context ->
                assertThat(context)
                    .getFailure()
                    .rootCause()
                    .hasMessageContaining("No managed-code provider 'aws'"));
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
              assertThat(context).hasSingleBean(ManagedScriptJobWorker.class);
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
              assertThat(context).hasSingleBean(ManagedScriptJobWorker.class);
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
