/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.sandbox.provider;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.connector.agenticai.aiagent.model.request.SandboxConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.SandboxConfiguration.DaytonaSandboxConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.SandboxConfiguration.DaytonaSandboxConfiguration.DaytonaConnection;
import io.camunda.connector.agenticai.sandbox.spi.SandboxCapability;
import io.camunda.connector.agenticai.sandbox.spi.SandboxHandle;
import io.camunda.connector.agenticai.sandbox.spi.SandboxProvider;
import io.camunda.connector.agenticai.sandbox.spi.SandboxSession;
import io.camunda.connector.agenticai.sandbox.spi.SandboxSpec;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link SandboxProviderRegistry} — provider-agnostic, stub-based. */
class SandboxProviderRegistryTest {

  private static final DaytonaSandboxConfiguration DAYTONA_CONFIG =
      new DaytonaSandboxConfiguration(
          new DaytonaConnection("api-key", null, null, null, null, null, null));

  /** Stub provider — does not perform real I/O. */
  private static class StubSandboxProvider implements SandboxProvider {
    static final StubSandboxProvider INSTANCE = new StubSandboxProvider();

    @Override
    public String id() {
      return "stub";
    }

    @Override
    public Set<SandboxCapability> capabilities() {
      return Set.of();
    }

    @Override
    public SandboxSession create(SandboxSpec spec) {
      throw new UnsupportedOperationException("stub");
    }

    @Override
    public SandboxSession connect(SandboxHandle handle) {
      throw new UnsupportedOperationException("stub");
    }
  }

  /** Factory that handles {@link DaytonaSandboxConfiguration}. */
  private static class DaytonaStubFactory implements SandboxProviderFactory {
    static final SandboxSpec STUB_SPEC = SandboxSpec.defaults();

    @Override
    public Class<? extends SandboxConfiguration> configType() {
      return DaytonaSandboxConfiguration.class;
    }

    @Override
    public SandboxProvider create(SandboxConfiguration config) {
      return StubSandboxProvider.INSTANCE;
    }

    @Override
    public SandboxSpec specFor(SandboxConfiguration config) {
      return STUB_SPEC;
    }
  }

  @Test
  void providerFor_returnsEmptyWhenNoFactoriesRegistered() {
    SandboxProviderRegistry registry = new SandboxProviderRegistry(List.of());

    assertThat(registry.providerFor(DAYTONA_CONFIG)).isEmpty();
  }

  @Test
  void providerFor_returnsProviderWhenFactoryMatchesConfigType() {
    SandboxProviderRegistry registry =
        new SandboxProviderRegistry(List.of(new DaytonaStubFactory()));

    assertThat(registry.providerFor(DAYTONA_CONFIG))
        .isPresent()
        .contains(StubSandboxProvider.INSTANCE);
  }

  @Test
  void specFor_returnsEmptyWhenNoFactoryRegistered() {
    SandboxProviderRegistry registry = new SandboxProviderRegistry(List.of());

    assertThat(registry.specFor(DAYTONA_CONFIG)).isEmpty();
  }

  @Test
  void specFor_returnsSpecWhenFactoryMatches() {
    SandboxProviderRegistry registry =
        new SandboxProviderRegistry(List.of(new DaytonaStubFactory()));

    assertThat(registry.specFor(DAYTONA_CONFIG)).isPresent().contains(DaytonaStubFactory.STUB_SPEC);
  }

  @Test
  void providerFor_usesFirstMatchingFactory() {
    // Two factories both claim DaytonaSandboxConfiguration — first wins.
    SandboxProviderFactory first = new DaytonaStubFactory();
    SandboxProviderFactory second =
        new SandboxProviderFactory() {
          @Override
          public Class<? extends SandboxConfiguration> configType() {
            return DaytonaSandboxConfiguration.class;
          }

          @Override
          public SandboxProvider create(SandboxConfiguration config) {
            throw new AssertionError("Should not be reached — first factory takes priority");
          }

          @Override
          public SandboxSpec specFor(SandboxConfiguration config) {
            throw new AssertionError("Should not be reached — first factory takes priority");
          }
        };

    SandboxProviderRegistry registry = new SandboxProviderRegistry(List.of(first, second));

    assertThat(registry.providerFor(DAYTONA_CONFIG))
        .isPresent()
        .contains(StubSandboxProvider.INSTANCE);
  }
}
