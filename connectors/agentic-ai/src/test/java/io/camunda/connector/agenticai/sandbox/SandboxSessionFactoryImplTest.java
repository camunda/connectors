/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.sandbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.connector.agenticai.aiagent.model.AgentConfiguration;
import io.camunda.connector.agenticai.aiagent.model.AgentContext;
import io.camunda.connector.agenticai.aiagent.model.AgentExecutionContext;
import io.camunda.connector.agenticai.aiagent.model.request.SandboxConfiguration.DaytonaSandboxConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.SandboxConfiguration.DaytonaSandboxConfiguration.DaytonaConnection;
import io.camunda.connector.agenticai.sandbox.provider.SandboxProviderRegistry;
import io.camunda.connector.agenticai.sandbox.spi.SandboxHandle;
import io.camunda.connector.agenticai.sandbox.spi.SandboxProvider;
import io.camunda.connector.agenticai.sandbox.spi.SandboxSession;
import io.camunda.connector.agenticai.sandbox.spi.SandboxSpec;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SandboxSessionFactoryImplTest {

  private static final DaytonaSandboxConfiguration SANDBOX_CONFIG =
      new DaytonaSandboxConfiguration(
          new DaytonaConnection("api-key", null, null, null, null, null));

  @Mock private SandboxProviderRegistry providerRegistry;
  @Mock private SandboxProvider provider;
  @Mock private AgentExecutionContext executionContext;
  @Mock private SandboxSession reconnectedSession;
  @Mock private SandboxSession createdSession;

  private SandboxSessionFactoryImpl factory;

  @BeforeEach
  void setUp() {
    factory = new SandboxSessionFactoryImpl(providerRegistry);
    when(executionContext.configuration())
        .thenReturn(
            new AgentConfiguration(null, null, null, null, null, null, null, SANDBOX_CONFIG));
    when(providerRegistry.providerFor(any())).thenReturn(Optional.of(provider));
  }

  private AgentContext agentContextWith(Object handleValue) {
    final Map<String, Object> properties =
        handleValue == null
            ? Map.of()
            : Map.of(SandboxSessionFactoryImpl.SANDBOX_HANDLE_KEY, handleValue);
    return AgentContext.builder().toolDefinitions(List.of()).properties(properties).build();
  }

  @Test
  void reconnects_whenLiveSandboxHandlePresent() {
    final var handle = new SandboxHandle("daytona", "sb-live", null);
    when(provider.connect(handle)).thenReturn(reconnectedSession);

    final var session = factory.openSession(executionContext, agentContextWith(handle));

    assertThat(session).contains(reconnectedSession);
    verify(provider).connect(handle);
    verify(provider, never()).create(any());
  }

  @Test
  void reconnects_whenHandleRehydratedAsMap() {
    // After a store round-trip the handle comes back as a plain Map, not a SandboxHandle.
    final var rehydrated =
        Map.of("providerId", "daytona", "sessionId", "sb-123", "snapshotRef", "snap-1");
    when(provider.connect(any())).thenReturn(reconnectedSession);

    final var session = factory.openSession(executionContext, agentContextWith(rehydrated));

    assertThat(session).contains(reconnectedSession);
    final var captor = ArgumentCaptor.forClass(SandboxHandle.class);
    verify(provider).connect(captor.capture());
    assertThat(captor.getValue().sessionId()).isEqualTo("sb-123");
    assertThat(captor.getValue().providerId()).isEqualTo("daytona");
    assertThat(captor.getValue().snapshotRef()).isEqualTo("snap-1");
    verify(provider, never()).create(any());
  }

  @Test
  void createsNewSession_whenNoHandlePresent() {
    final var spec = SandboxSpec.defaults();
    when(providerRegistry.specFor(any())).thenReturn(Optional.of(spec));
    when(provider.create(spec)).thenReturn(createdSession);

    final var session = factory.openSession(executionContext, agentContextWith(null));

    assertThat(session).contains(createdSession);
    verify(provider).create(spec);
    verify(provider, never()).connect(any());
  }

  @Test
  void createsNewSession_whenHandleValueIsUnusable() {
    // A map without a session id cannot be reconnected → fall through to create.
    final var spec = SandboxSpec.defaults();
    when(providerRegistry.specFor(any())).thenReturn(Optional.of(spec));
    when(provider.create(spec)).thenReturn(createdSession);

    final var session =
        factory.openSession(executionContext, agentContextWith(Map.of("providerId", "daytona")));

    assertThat(session).contains(createdSession);
    verify(provider).create(spec);
    verify(provider, never()).connect(any());
  }

  @Test
  void fallsBackToCreate_whenReconnectFails() {
    final var handle = new SandboxHandle("daytona", "sb-stale", null);
    final var spec = SandboxSpec.defaults();
    when(provider.connect(handle)).thenThrow(new RuntimeException("gone"));
    when(providerRegistry.specFor(any())).thenReturn(Optional.of(spec));
    when(provider.create(spec)).thenReturn(createdSession);

    final var session = factory.openSession(executionContext, agentContextWith(handle));

    assertThat(session).contains(createdSession);
    verify(provider).connect(handle);
    verify(provider).create(spec);
  }
}
