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
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.client.CamundaClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class ManagedCodeAutoConfigurationTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(ManagedCodeAutoConfiguration.class));

  @Test
  void disabledByDefault() {
    contextRunner.run(
        context -> {
          assertThat(context).doesNotHaveBean(ManagedCodeReconciler.class);
          assertThat(context).doesNotHaveBean(FakeManagedCodeDeploymentProvider.class);
          assertThat(context).doesNotHaveBean(LocalProcessScriptExecutor.class);
          assertThat(context).doesNotHaveBean(ManagedScriptJobWorker.class);
        });
  }

  @Test
  void enabledReconciliationUsesExplicitFakeProvider() {
    var client = mock(CamundaClient.class, RETURNS_DEEP_STUBS);
    when(client.getConfiguration().getPhysicalTenantId()).thenReturn("tenant-a");
    var controlPlane = new SingleDeploymentControlPlane(deployment("deployment-a"));

    contextRunner
        .withPropertyValues(
            "camunda.connector.managed-code.enabled=true",
            "camunda.connector.managed-code.provider=fake",
            "camunda.connector.managed-code.initial-delay=1h",
            "camunda.connector.managed-code.interval=1h")
        .withBean(CamundaClient.class, () -> client)
        .withBean(ManagedScriptControlPlane.class, () -> controlPlane)
        .run(
            context -> {
              assertThat(context).hasSingleBean(ManagedCodeReconciler.class);
              assertThat(context).hasSingleBean(FakeManagedCodeDeploymentProvider.class);

              context.getBean(ManagedCodeReconciler.class).reconcile();

              assertThat(controlPlane.completed.await(5, TimeUnit.SECONDS)).isTrue();
              assertThat(controlPlane.completedPhysicalTenantId).isEqualTo("tenant-a");
              assertThat(controlPlane.providerOperationId).startsWith("fake-operation-");
              assertThat(
                      context
                          .getBean(FakeManagedCodeDeploymentProvider.class)
                          .deployments()
                          .keySet())
                  .containsExactly("deployment-a");
            });
  }

  @Test
  void enabledProvidesRestControlPlaneByDefault() {
    var client = mock(CamundaClient.class, RETURNS_DEEP_STUBS);
    when(client.getConfiguration().getPhysicalTenantId()).thenReturn("tenant-a");

    contextRunner
        .withPropertyValues(
            "camunda.connector.managed-code.enabled=true",
            "camunda.connector.managed-code.provider=fake")
        .withBean(CamundaClient.class, () -> client)
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              assertThat(context).hasSingleBean(RestManagedScriptControlPlane.class);
              assertThat(context).hasSingleBean(ManagedCodeReconciler.class);
            });
  }

  @Test
  void localExecutionMustBeExplicitlyEnabled() {
    var client = mock(CamundaClient.class, RETURNS_DEEP_STUBS);
    when(client.getConfiguration().getPhysicalTenantId()).thenReturn("tenant-a");

    contextRunner
        .withPropertyValues(
            "camunda.connector.managed-code.enabled=true",
            "camunda.connector.managed-code.provider=fake",
            "camunda.connector.managed-code.local-execution-enabled=true")
        .withBean(CamundaClient.class, () -> client)
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              assertThat(context).hasSingleBean(LocalProcessScriptExecutor.class);
              assertThat(context).hasSingleBean(ManagedScriptJobWorker.class);
            });
  }

  private static ManagedScriptDeployment deployment(String id) {
    return new ManagedScriptDeployment(
        id,
        1L,
        "lease-a",
        "fake",
        "tenant-a",
        123L,
        "resource-a",
        new byte[] {1, 2, 3},
        "javascript",
        "nodejs22",
        "return 1".getBytes(StandardCharsets.UTF_8),
        Optional.empty());
  }

  private static final class SingleDeploymentControlPlane implements ManagedScriptControlPlane {

    private final ManagedScriptDeployment deployment;
    private final CountDownLatch completed = new CountDownLatch(1);
    private volatile String completedPhysicalTenantId;
    private volatile String providerOperationId;

    private SingleDeploymentControlPlane(ManagedScriptDeployment deployment) {
      this.deployment = deployment;
    }

    @Override
    public List<ManagedScriptDeployment> acquireDeployments(
        CamundaClient client,
        String physicalTenantId,
        String worker,
        int limit,
        Duration leaseDuration) {
      return List.of(deployment);
    }

    @Override
    public void renewLease(
        CamundaClient client,
        String physicalTenantId,
        ManagedScriptDeployment deployment,
        Duration leaseDuration) {}

    @Override
    public ManagedScriptDeployment recordProviderOperation(
        CamundaClient client,
        String physicalTenantId,
        ManagedScriptDeployment deployment,
        String providerOperationId) {
      this.providerOperationId = providerOperationId;
      return deployment.withProviderOperation(
          deployment.definitionRevision() + 1, providerOperationId);
    }

    @Override
    public void completeDeployment(
        CamundaClient client,
        String physicalTenantId,
        ManagedScriptDeployment deployment,
        ManagedCodeDeploymentResult result) {
      completedPhysicalTenantId = physicalTenantId;
      completed.countDown();
    }

    @Override
    public void failDeployment(
        CamundaClient client,
        String physicalTenantId,
        ManagedScriptDeployment deployment,
        ManagedCodeDeploymentFailure failure) {
      throw new AssertionError("deployment should not fail");
    }
  }
}
