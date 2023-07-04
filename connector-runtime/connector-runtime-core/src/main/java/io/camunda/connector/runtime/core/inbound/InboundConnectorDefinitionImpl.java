package io.camunda.connector.runtime.core.inbound;

import io.camunda.connector.api.inbound.InboundConnectorDefinition;
import io.camunda.connector.impl.Constants;
import io.camunda.connector.impl.inbound.ProcessCorrelationPoint;
import java.util.Map;
import java.util.Optional;

/**
 * Inbound connector definition implementation that also contains connector properties
 */
public record InboundConnectorDefinitionImpl(
    Map<String, Object> properties,
    ProcessCorrelationPoint correlationPoint,
    String bpmnProcessId,
    Integer version,
    Long processDefinitionKey,
    String elementId
) implements InboundConnectorDefinition {

  @Override
  public String type() {
    return Optional.ofNullable(
        properties.get(Constants.INBOUND_TYPE_KEYWORD).toString()
    ).orElseThrow(() -> new IllegalArgumentException(
        "Missing connector type property. The connector element template is not valid"));
  }
}
