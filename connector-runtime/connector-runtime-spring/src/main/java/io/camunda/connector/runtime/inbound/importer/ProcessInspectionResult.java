package io.camunda.connector.runtime.inbound.importer;

import io.camunda.connector.runtime.core.inbound.correlation.ProcessCorrelationPoint;
import java.util.List;
import java.util.Map;

public record ProcessInspectionResult(
    String bpmnProcessId,
    int version,
    long processDefinitionKey,
    String tenantId,
    List<ProcessInboundConnectorData> inboundConnectors
) {
  record ProcessInboundConnectorData(
      String elementId,
      Map<String, String> rawProperties,
      ProcessCorrelationPoint correlationPoint
  ) {}
}
