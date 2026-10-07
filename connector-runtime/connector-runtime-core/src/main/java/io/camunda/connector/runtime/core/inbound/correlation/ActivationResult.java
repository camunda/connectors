package io.camunda.connector.runtime.core.inbound.correlation;

import io.camunda.connector.api.inbound.ActivationCheckResult;
import io.camunda.connector.runtime.core.inbound.InboundConnectorElement;
import java.util.List;

public record ActivationResult(
    ActivationCheckResult result, List<InboundConnectorElement> elementsToCorrelate) {

  static ActivationResult success(List<InboundConnectorElement> elementsToCorrelate) {
    return new ActivationResult(
        new ActivationCheckResult.Success.CanActivate(elementsToCorrelate.getFirst().element()),
        elementsToCorrelate);
  }

  static ActivationResult emptyFailure(ActivationCheckResult result) {
    return new ActivationResult(result, List.of());
  }
}
