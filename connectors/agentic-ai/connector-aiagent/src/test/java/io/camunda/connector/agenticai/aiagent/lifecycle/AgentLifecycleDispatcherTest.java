/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.lifecycle;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.connector.agenticai.aiagent.api.lifecycle.AgentFlavor;
import io.camunda.connector.agenticai.aiagent.api.lifecycle.AgentJobKind;
import io.camunda.connector.agenticai.aiagent.api.lifecycle.AgentJobObserver;
import io.camunda.connector.agenticai.aiagent.api.lifecycle.AgentLifecycleListener;
import io.camunda.connector.agenticai.aiagent.api.lifecycle.JobStarted;
import io.camunda.connector.agenticai.aiagent.api.lifecycle.ToolDiscoveryDispatched;
import io.camunda.connector.agenticai.aiagent.model.AgentContext;
import io.camunda.connector.api.outbound.JobContext;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class AgentLifecycleDispatcherTest {

  private final JobContext jobContext = Mockito.mock(JobContext.class);

  @Test
  void withoutListenersTheLifecycleDoesNothing() {
    final var dispatcher = new AgentLifecycleDispatcher(List.of(), 1000);

    final var lifecycle = start(dispatcher);

    assertThat(lifecycle).isSameAs(AgentJobLifecycle.NOOP);
  }

  @Test
  void throwingListenerDoesNotStopOthersAndStateLandsInItsOwnNamespace() {
    final var seen = new ArrayList<JobStarted>();
    final var throwing =
        new AgentLifecycleListener() {
          @Override
          public String id() {
            return "throwing";
          }

          @Override
          public AgentJobObserver onJobStarted(JobStarted event) {
            return new AgentJobObserver() {
              @Override
              public Map<String, Object> onToolDiscoveryDispatched(ToolDiscoveryDispatched e) {
                throw new IllegalStateException("boom");
              }
            };
          }
        };
    final var stateful =
        new AgentLifecycleListener() {
          @Override
          public String id() {
            return "stateful";
          }

          @Override
          public AgentJobObserver onJobStarted(JobStarted event) {
            seen.add(event);
            return new AgentJobObserver() {
              @Override
              public Map<String, Object> onToolDiscoveryDispatched(ToolDiscoveryDispatched e) {
                return Map.of("status", "OPEN");
              }
            };
          }
        };
    final var dispatcher = new AgentLifecycleDispatcher(List.of(throwing, stateful), 1000);
    final var previous = AgentContext.empty().withProperty("lifecycle:stateful", Map.of("a", 1));

    final var lifecycle =
        dispatcher.startJob(
            AgentFlavor.SUB_PROCESS,
            AgentJobKind.TOOL_DISCOVERY,
            jobContext,
            previous,
            Instant.now());
    final var result =
        lifecycle.toolDiscoveryDispatched(previous, new ToolDiscoveryDispatched(List.of()));

    assertThat(seen.getFirst().previousState()).containsEntry("a", 1);
    assertThat(result.properties())
        .containsOnlyKeys("lifecycle:stateful")
        .containsEntry("lifecycle:stateful", Map.of("status", "OPEN"));
  }

  @Test
  void oversizedStateIsDropped() {
    final var listener =
        new AgentLifecycleListener() {
          @Override
          public String id() {
            return "big";
          }

          @Override
          public AgentJobObserver onJobStarted(JobStarted event) {
            return new AgentJobObserver() {
              @Override
              public Map<String, Object> onToolDiscoveryDispatched(ToolDiscoveryDispatched e) {
                return Map.of("payload", "x".repeat(100));
              }
            };
          }
        };
    final var dispatcher = new AgentLifecycleDispatcher(List.of(listener), 50);

    final var result =
        start(dispatcher)
            .toolDiscoveryDispatched(AgentContext.empty(), new ToolDiscoveryDispatched(List.of()));

    assertThat(result.properties()).isEmpty();
  }

  private AgentJobLifecycle start(AgentLifecycleDispatcher dispatcher) {
    return dispatcher.startJob(
        AgentFlavor.SUB_PROCESS,
        AgentJobKind.TOOL_DISCOVERY,
        jobContext,
        AgentContext.empty(),
        Instant.now());
  }
}
