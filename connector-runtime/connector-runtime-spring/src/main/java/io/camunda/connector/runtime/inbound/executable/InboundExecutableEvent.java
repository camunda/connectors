package io.camunda.connector.runtime.inbound.executable;

import io.camunda.connector.runtime.core.inbound.InboundConnectorDefinitionImpl;
import java.util.UUID;

public sealed interface InboundExecutableEvent {

  record Activated(
      UUID executableId,
      InboundConnectorDefinitionImpl definition
  ) implements InboundExecutableEvent {}

  record Deactivated(UUID executableId) implements InboundExecutableEvent {}
}
