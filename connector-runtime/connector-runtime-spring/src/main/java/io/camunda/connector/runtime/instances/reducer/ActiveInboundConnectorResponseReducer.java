package io.camunda.connector.runtime.instances.reducer;

import io.camunda.connector.api.inbound.Health;
import io.camunda.connector.runtime.inbound.controller.ActiveInboundConnectorResponse;
import java.util.Optional;

public class ActiveInboundConnectorResponseReducer
    implements Reducer<ActiveInboundConnectorResponse> {

  @Override
  public ActiveInboundConnectorResponse reduce(
      ActiveInboundConnectorResponse first, ActiveInboundConnectorResponse second) {
    if (first == null) {
      return second;
    }
    if (second == null) {
      return first;
    }
    if (!first.executableId().equals(second.executableId())) {
      throw new IllegalArgumentException(
          "Cannot reduce two ActiveInboundConnectorResponse with different executable IDs: "
              + first.executableId()
              + " and "
              + second.executableId());
    }

    return reduceHealth(first, second);
  }

  private ActiveInboundConnectorResponse reduceHealth(
      ActiveInboundConnectorResponse first, ActiveInboundConnectorResponse second) {
    return findForStatus(first, second, Health.Status.DOWN)
        .or(() -> findForStatus(first, second, Health.Status.UNKNOWN))
        .orElse(first);
  }

  private Optional<ActiveInboundConnectorResponse> findForStatus(
      ActiveInboundConnectorResponse first,
      ActiveInboundConnectorResponse second,
      Health.Status status) {
    if (first.health().getStatus() == status) {
      return Optional.of(first);
    } else if (second.health().getStatus() == status) {
      return Optional.of(second);
    } else {
      return Optional.empty();
    }
  }
}
