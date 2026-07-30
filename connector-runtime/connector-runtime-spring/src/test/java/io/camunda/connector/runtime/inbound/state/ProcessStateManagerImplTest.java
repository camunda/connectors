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
package io.camunda.connector.runtime.inbound.state;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.connector.runtime.inbound.executable.InboundExecutableEvent;
import io.camunda.connector.runtime.inbound.executable.InboundExecutableRegistry;
import io.camunda.connector.runtime.inbound.state.model.ImportResult;
import io.camunda.connector.runtime.inbound.state.model.ImportResult.ImportType;
import io.camunda.connector.runtime.inbound.state.model.ProcessDefinitionRef;
import io.camunda.connector.runtime.metrics.ConnectorMetrics;
import io.camunda.connector.runtime.metrics.ConnectorsInboundMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ProcessStateManagerImplTest {

  private ProcessStateContainerImpl container;
  private ProcessDefinitionInspector inspector;
  private InboundExecutableRegistry registry;
  private SimpleMeterRegistry meterRegistry;
  private ProcessStateManagerImpl manager;

  @BeforeEach
  void setUp() {
    container = new ProcessStateContainerImpl();
    inspector = mock(ProcessDefinitionInspector.class);
    registry = mock(InboundExecutableRegistry.class);
    meterRegistry = new SimpleMeterRegistry();
    manager =
        new ProcessStateManagerImpl(
            container, inspector, registry, new ConnectorsInboundMetrics(meterRegistry));
  }

  private ProcessDefinitionRef processRef(String bpmnProcessId) {
    return new ProcessDefinitionRef(bpmnProcessId, "tenant1");
  }

  private ImportResult latestVersions(Map<ProcessDefinitionRef, Set<Long>> versions) {
    return new ImportResult(versions, ImportType.LATEST_VERSIONS);
  }

  private double publishFailureCount() {
    var counter =
        meterRegistry
            .find(ConnectorMetrics.Inbound.METRIC_NAME_PROCESS_STATE_CHANGE_PUBLISH_FAILURES)
            .counter();
    return counter == null ? 0d : counter.count();
  }

  @Test
  void shouldPublishStateChangeOnFirstImport() {
    // given
    var processRef = processRef("process1");
    when(inspector.findInboundConnectors(eq(processRef), anyLong())).thenReturn(List.of());

    // when
    manager.update(latestVersions(Map.of(processRef, Set.of(1L))));

    // then
    verify(registry, times(1)).publishEvent(any(InboundExecutableEvent.ProcessStateChanged.class));
  }

  @Test
  void shouldNotRepublishWhenNothingChanged() {
    // given
    var processRef = processRef("process1");
    when(inspector.findInboundConnectors(eq(processRef), anyLong())).thenReturn(List.of());
    var importResult = latestVersions(Map.of(processRef, Set.of(1L)));
    manager.update(importResult);

    // when - the same unchanged data is imported again
    manager.update(importResult);

    // then - the change is only ever reported once
    verify(registry, times(1)).publishEvent(any(InboundExecutableEvent.ProcessStateChanged.class));
  }

  /**
   * Regression test for a lost state change: the state transition is committed by {@code
   * compareAndUpdate} before the event is published, and an unchanged import produces no further
   * diff. If a transient failure while fetching the BPMN model is swallowed, the process is
   * recorded as active but its connectors are never activated, and no later poll ever retries —
   * stranding them until the runtime is restarted. See camunda/connectors#8148.
   */
  @Test
  void shouldRetryPublishingOnNextUpdateWhenFetchingConnectorsFailed() {
    // given - fetching the BPMN model fails once (Orchestration Cluster briefly unreachable),
    // then succeeds
    var processRef = processRef("process1");
    when(inspector.findInboundConnectors(eq(processRef), anyLong()))
        .thenThrow(new RuntimeException("Connection refused"))
        .thenReturn(List.of());

    var importResult = latestVersions(Map.of(processRef, Set.of(1L)));

    // when - the first poll fails to publish
    manager.update(importResult);

    // then - nothing was published, and the failure is observable
    verify(registry, never()).publishEvent(any());
    assertThat(publishFailureCount()).isEqualTo(1d);

    // when - the next poll imports the very same, unchanged data
    manager.update(importResult);

    // then - the change is reported again and the connectors are activated
    verify(registry, times(1)).publishEvent(any(InboundExecutableEvent.ProcessStateChanged.class));
  }

  @Test
  void shouldKeepRetryingWhilePublishingKeepsFailing() {
    // given - fetching the BPMN model fails on the first two polls
    var processRef = processRef("process1");
    when(inspector.findInboundConnectors(eq(processRef), anyLong()))
        .thenThrow(new RuntimeException("Connection refused"))
        .thenThrow(new RuntimeException("Connection refused"))
        .thenReturn(List.of());

    var importResult = latestVersions(Map.of(processRef, Set.of(1L)));

    // when
    manager.update(importResult);
    manager.update(importResult);

    // then - still nothing published, but the retry flag was not consumed for good
    verify(registry, never()).publishEvent(any());
    assertThat(publishFailureCount()).isEqualTo(2d);

    // when - the cluster becomes reachable again
    manager.update(importResult);

    // then
    verify(registry, times(1)).publishEvent(any(InboundExecutableEvent.ProcessStateChanged.class));
  }

  /**
   * A retry must act on the versions that are active when it runs, not on the ones it originally
   * failed with — those may have been superseded by a deployment in the meantime, and republishing
   * them would activate a stale version.
   */
  @Test
  void shouldRetryWithTheVersionsActiveNowNotTheOnesItFailedWith() {
    // given - publishing version 1 fails
    var processRef = processRef("process1");
    when(inspector.findInboundConnectors(eq(processRef), anyLong()))
        .thenThrow(new RuntimeException("Connection refused"))
        .thenReturn(List.of());
    manager.update(latestVersions(Map.of(processRef, Set.of(1L))));
    verify(registry, never()).publishEvent(any());

    // when - version 2 is deployed before the retry gets a chance to run
    manager.update(latestVersions(Map.of(processRef, Set.of(2L))));

    // then - published once, for version 2 only
    var event = ArgumentCaptor.forClass(InboundExecutableEvent.ProcessStateChanged.class);
    verify(registry, times(1)).publishEvent(event.capture());
    assertThat(event.getValue().elementsByProcessDefinitionKey()).containsOnlyKeys(2L);
  }

  /**
   * A process can be undeployed while its retry is still queued. The retry must then publish a
   * deactivation (an empty version map) rather than resurrecting the version it failed with.
   */
  @Test
  void shouldPublishDeactivationWhenProcessWasUndeployedWhileRetryPending() {
    // given - publishing version 1 fails
    var processRef = processRef("process1");
    when(inspector.findInboundConnectors(eq(processRef), anyLong()))
        .thenThrow(new RuntimeException("Connection refused"))
        .thenReturn(List.of());
    manager.update(latestVersions(Map.of(processRef, Set.of(1L))));
    verify(registry, never()).publishEvent(any());

    // when - the process is undeployed, so the next import no longer reports it
    manager.update(new ImportResult(Map.of(), ImportType.LATEST_VERSIONS));

    // then - a deactivation is published, not version 1 again
    var event = ArgumentCaptor.forClass(InboundExecutableEvent.ProcessStateChanged.class);
    verify(registry, times(1)).publishEvent(event.capture());
    assertThat(event.getValue().bpmnProcessId()).isEqualTo("process1");
    assertThat(event.getValue().elementsByProcessDefinitionKey()).isEmpty();
  }

  @Test
  void shouldNotStrandOtherProcessesWhenOneFailsToPublish() {
    // given - only process1 fails to be inspected
    var failing = processRef("process1");
    var healthy = processRef("process2");
    when(inspector.findInboundConnectors(eq(failing), anyLong()))
        .thenThrow(new RuntimeException("Connection refused"))
        .thenReturn(List.of());
    when(inspector.findInboundConnectors(eq(healthy), anyLong())).thenReturn(List.of());

    var importResult = latestVersions(Map.of(failing, Set.of(1L), healthy, Set.of(2L)));

    // when
    manager.update(importResult);

    // then - process2 was published despite process1 failing
    verify(registry, times(1)).publishEvent(any(InboundExecutableEvent.ProcessStateChanged.class));

    // when - the next poll retries only the failed one
    manager.update(importResult);

    // then
    verify(registry, times(2)).publishEvent(any(InboundExecutableEvent.ProcessStateChanged.class));
  }

  @Test
  void shouldRetryPublishingWhenRegistryRejectedTheEvent() {
    // given - inspection succeeds but the registry itself fails once
    var processRef = processRef("process1");
    when(inspector.findInboundConnectors(eq(processRef), anyLong())).thenReturn(List.of());
    doThrow(new RuntimeException("registry unavailable"))
        .doNothing()
        .when(registry)
        .publishEvent(any());

    var importResult = latestVersions(Map.of(processRef, Set.of(1L)));

    // when
    manager.update(importResult);
    manager.update(importResult);

    // then - published twice: the rejected attempt and the successful retry
    verify(registry, times(2)).publishEvent(any(InboundExecutableEvent.ProcessStateChanged.class));
    assertThat(publishFailureCount()).isEqualTo(1d);
  }
}
