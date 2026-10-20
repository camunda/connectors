/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.api.lifecycle;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AgentLifecycleListenerTest {

  @Test
  void listenerWithoutOverridesYieldsNoopObserver() {
    final AgentLifecycleListener listener = () -> "test";
    final var job = new AgentJobInfo(1, 2, 3, 4, "agent", "process", "<default>");
    final var event =
        new JobStarted(
            job,
            AgentFlavor.SUB_PROCESS,
            AgentJobKind.CONVERSATION,
            Instant.now(),
            null,
            null,
            Map.of());

    final var observer = listener.onJobStarted(event);

    assertThat(observer).isSameAs(AgentJobObserver.NOOP);
    assertThat(observer.onToolDiscoveryDispatched(new ToolDiscoveryDispatched(List.of()))).isNull();
    assertThat(observer.onTurnCompleted(null)).isNull();
  }
}
