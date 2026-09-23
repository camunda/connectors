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

import io.camunda.client.CamundaClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class ManagedCodeReconcilerTest {

  @Test
  void isolatesItemFailuresWithinOneTenant() throws Exception {
    var controlPlane =
        new RecordingControlPlane(List.of(deployment("failing"), deployment("healthy")), 2);
    ManagedCodeDeploymentProvider provider =
        new ManagedCodeDeploymentProvider() {
          @Override
          public String provider() {
            return "fake";
          }

          @Override
          public String startDeployment(ManagedScriptDeployment deployment) {
            return "operation-" + deployment.deploymentId();
          }

          @Override
          public ManagedCodeDeploymentResult resumeDeployment(
              ManagedScriptDeployment deployment, String providerOperationId) {
            if (deployment.deploymentId().equals("failing")) {
              throw new IllegalStateException("provider unavailable");
            }
            return new ManagedCodeDeploymentResult("provider-healthy", Map.of());
          }
        };
    var reconciler = reconciler(controlPlane, provider, mock(CamundaClient.class));
    try {
      reconciler.reconcile();

      assertThat(controlPlane.reported.await(5, TimeUnit.SECONDS)).isTrue();
      assertThat(controlPlane.completed)
          .extracting(ManagedScriptDeployment::deploymentId)
          .containsExactly("healthy");
      assertThat(controlPlane.completed)
          .extracting(ManagedScriptDeployment::definitionRevision)
          .containsExactly(2L);
      assertThat(controlPlane.failed)
          .extracting(ManagedScriptDeployment::deploymentId)
          .containsExactly("failing");
      assertThat(controlPlane.failed)
          .extracting(ManagedScriptDeployment::definitionRevision)
          .containsExactly(2L);
      assertThat(controlPlane.providerOperationIds)
          .containsExactlyInAnyOrder("operation-failing", "operation-healthy");
    } finally {
      reconciler.shutdown();
    }
  }

  @Test
  void doesNotOverlapReconciliationForTheSamePhysicalTenant() throws Exception {
    var entered = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    var calls = new AtomicInteger();
    var concurrent = new AtomicInteger();
    var maxConcurrent = new AtomicInteger();
    ManagedScriptControlPlane controlPlane =
        new EmptyControlPlane() {
          @Override
          public List<ManagedScriptDeployment> acquireDeployments(
              CamundaClient client,
              String physicalTenantId,
              String worker,
              int limit,
              Duration leaseDuration)
              throws InterruptedException {
            calls.incrementAndGet();
            int active = concurrent.incrementAndGet();
            maxConcurrent.accumulateAndGet(active, Math::max);
            entered.countDown();
            try {
              release.await(5, TimeUnit.SECONDS);
              return List.of();
            } finally {
              concurrent.decrementAndGet();
            }
          }
        };
    var reconciler =
        reconciler(
            controlPlane, new FakeManagedCodeDeploymentProvider(), mock(CamundaClient.class));
    try {
      reconciler.reconcile();
      assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();

      reconciler.reconcile();

      assertThat(calls.get()).isEqualTo(1);
      assertThat(maxConcurrent.get()).isEqualTo(1);
    } finally {
      release.countDown();
      reconciler.shutdown();
    }
  }

  @Test
  void stopsAndRestartsOnePhysicalTenantWithItsCamundaClient() throws Exception {
    var originalClient = mock(CamundaClient.class);
    var restartedClient = mock(CamundaClient.class);
    var acquiredClient = new AtomicReference<CamundaClient>();
    var acquired = new CountDownLatch(1);
    ManagedScriptControlPlane controlPlane =
        new EmptyControlPlane() {
          @Override
          public List<ManagedScriptDeployment> acquireDeployments(
              CamundaClient client,
              String physicalTenantId,
              String worker,
              int limit,
              Duration leaseDuration) {
            acquiredClient.set(client);
            acquired.countDown();
            return List.of();
          }
        };
    var reconciler =
        reconciler(controlPlane, new FakeManagedCodeDeploymentProvider(), originalClient);
    try {
      reconciler.onStop(originalClient, "engine-a");
      assertThat(reconciler.activePhysicalTenantIds()).isEmpty();

      reconciler.onStart(restartedClient, "engine-a");
      assertThat(reconciler.activePhysicalTenantIds()).containsExactly("tenant-a");

      reconciler.reconcile();
      assertThat(acquired.await(5, TimeUnit.SECONDS)).isTrue();
      assertThat(acquiredClient.get()).isSameAs(restartedClient);
    } finally {
      reconciler.shutdown();
    }
  }

  @Test
  void shutdownStopsAcceptingReconciliationWork() {
    var calls = new AtomicInteger();
    ManagedScriptControlPlane controlPlane =
        new EmptyControlPlane() {
          @Override
          public List<ManagedScriptDeployment> acquireDeployments(
              CamundaClient client,
              String physicalTenantId,
              String worker,
              int limit,
              Duration leaseDuration) {
            calls.incrementAndGet();
            return List.of();
          }
        };
    var reconciler =
        reconciler(
            controlPlane, new FakeManagedCodeDeploymentProvider(), mock(CamundaClient.class));

    reconciler.shutdown();
    reconciler.reconcile();

    assertThat(reconciler.isShutdown()).isTrue();
    assertThat(calls.get()).isZero();
  }

  @Test
  void resumesCheckpointedProviderOperationWithoutStartingAnother() throws Exception {
    var deployment =
        new ManagedScriptDeployment(
            "deployment-a",
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
            Optional.of("operation-a"));
    var controlPlane = new RecordingControlPlane(List.of(deployment), 1);
    ManagedCodeDeploymentProvider provider =
        new ManagedCodeDeploymentProvider() {
          @Override
          public String provider() {
            return "fake";
          }

          @Override
          public String startDeployment(ManagedScriptDeployment deployment) {
            throw new AssertionError("checkpointed operation must not be started again");
          }

          @Override
          public ManagedCodeDeploymentResult resumeDeployment(
              ManagedScriptDeployment deployment, String providerOperationId) {
            assertThat(providerOperationId).isEqualTo("operation-a");
            return new ManagedCodeDeploymentResult("provider-a", Map.of());
          }
        };
    var reconciler = reconciler(controlPlane, provider, mock(CamundaClient.class));
    try {
      reconciler.reconcile();

      assertThat(controlPlane.reported.await(5, TimeUnit.SECONDS)).isTrue();
      assertThat(controlPlane.completed).containsExactly(deployment);
      assertThat(controlPlane.providerOperationIds).isEmpty();
    } finally {
      reconciler.shutdown();
    }
  }

  @Test
  void renewsLeaseWhileProviderWorkIsRunning() throws Exception {
    var renewed = new CountDownLatch(1);
    var releaseProvider = new CountDownLatch(1);
    var controlPlane =
        new RecordingControlPlane(List.of(deployment("slow")), 1) {
          @Override
          public void renewLease(
              CamundaClient client,
              String physicalTenantId,
              ManagedScriptDeployment deployment,
              Duration leaseDuration) {
            renewed.countDown();
          }
        };
    ManagedCodeDeploymentProvider provider =
        new ManagedCodeDeploymentProvider() {
          @Override
          public String provider() {
            return "fake";
          }

          @Override
          public String startDeployment(ManagedScriptDeployment deployment) {
            return "operation-slow";
          }

          @Override
          public ManagedCodeDeploymentResult resumeDeployment(
              ManagedScriptDeployment deployment, String providerOperationId)
              throws InterruptedException {
            releaseProvider.await(5, TimeUnit.SECONDS);
            return new ManagedCodeDeploymentResult("provider-slow", Map.of());
          }
        };
    var reconciler =
        reconciler(controlPlane, provider, mock(CamundaClient.class), Duration.ofMillis(20));
    try {
      reconciler.reconcile();

      assertThat(renewed.await(5, TimeUnit.SECONDS)).isTrue();
      releaseProvider.countDown();
      assertThat(controlPlane.reported.await(5, TimeUnit.SECONDS)).isTrue();
    } finally {
      releaseProvider.countDown();
      reconciler.shutdown();
    }
  }

  private static ManagedCodeReconciler reconciler(
      ManagedScriptControlPlane controlPlane,
      ManagedCodeDeploymentProvider provider,
      CamundaClient client) {
    return reconciler(controlPlane, provider, client, Duration.ofMinutes(2));
  }

  private static ManagedCodeReconciler reconciler(
      ManagedScriptControlPlane controlPlane,
      ManagedCodeDeploymentProvider provider,
      CamundaClient client,
      Duration leaseDuration) {
    return new ManagedCodeReconciler(
        controlPlane,
        provider,
        new ManagedCodeProperties(
            true,
            provider.provider(),
            "managed-code-runtime",
            Duration.ofSeconds(5),
            Duration.ZERO,
            leaseDuration,
            20,
            2,
            4,
            Duration.ofSeconds(5)),
        Map.of("engine-a", "tenant-a"),
        Map.of("tenant-a", client));
  }

  private static ManagedScriptDeployment deployment(String id) {
    return new ManagedScriptDeployment(
        id,
        1L,
        "lease-" + id,
        "fake",
        "tenant-a",
        123L,
        "resource-" + id,
        id.getBytes(StandardCharsets.UTF_8),
        "javascript",
        "nodejs22",
        id.getBytes(StandardCharsets.UTF_8),
        Optional.empty());
  }

  private static class EmptyControlPlane implements ManagedScriptControlPlane {

    @Override
    public List<ManagedScriptDeployment> acquireDeployments(
        CamundaClient client,
        String physicalTenantId,
        String worker,
        int limit,
        Duration leaseDuration)
        throws Exception {
      return List.of();
    }

    @Override
    public void renewLease(
        CamundaClient client,
        String physicalTenantId,
        ManagedScriptDeployment deployment,
        Duration leaseDuration)
        throws Exception {}

    @Override
    public ManagedScriptDeployment recordProviderOperation(
        CamundaClient client,
        String physicalTenantId,
        ManagedScriptDeployment deployment,
        String providerOperationId)
        throws Exception {
      return deployment.withProviderOperation(
          deployment.definitionRevision() + 1, providerOperationId);
    }

    @Override
    public void completeDeployment(
        CamundaClient client,
        String physicalTenantId,
        ManagedScriptDeployment deployment,
        ManagedCodeDeploymentResult result)
        throws Exception {}

    @Override
    public void failDeployment(
        CamundaClient client,
        String physicalTenantId,
        ManagedScriptDeployment deployment,
        ManagedCodeDeploymentFailure failure)
        throws Exception {}
  }

  private static class RecordingControlPlane extends EmptyControlPlane {

    private final List<ManagedScriptDeployment> acquired;
    final CountDownLatch reported;
    private final List<ManagedScriptDeployment> completed =
        Collections.synchronizedList(new ArrayList<>());
    private final List<ManagedScriptDeployment> failed =
        Collections.synchronizedList(new ArrayList<>());
    private final List<String> providerOperationIds =
        Collections.synchronizedList(new ArrayList<>());

    private RecordingControlPlane(List<ManagedScriptDeployment> acquired, int expectedReports) {
      this.acquired = acquired;
      reported = new CountDownLatch(expectedReports);
    }

    @Override
    public List<ManagedScriptDeployment> acquireDeployments(
        CamundaClient client,
        String physicalTenantId,
        String worker,
        int limit,
        Duration leaseDuration) {
      return acquired;
    }

    @Override
    public ManagedScriptDeployment recordProviderOperation(
        CamundaClient client,
        String physicalTenantId,
        ManagedScriptDeployment deployment,
        String providerOperationId) {
      providerOperationIds.add(providerOperationId);
      return deployment.withProviderOperation(
          deployment.definitionRevision() + 1, providerOperationId);
    }

    @Override
    public void completeDeployment(
        CamundaClient client,
        String physicalTenantId,
        ManagedScriptDeployment deployment,
        ManagedCodeDeploymentResult result) {
      completed.add(deployment);
      reported.countDown();
    }

    @Override
    public void failDeployment(
        CamundaClient client,
        String physicalTenantId,
        ManagedScriptDeployment deployment,
        ManagedCodeDeploymentFailure failure) {
      failed.add(deployment);
      reported.countDown();
    }
  }
}
