package io.camunda.connector.api.inbound;

/** Represents a BPMN process element that contains an inbound connector definition. */
public interface InboundConnectorElement {

  String bpmnProcessId();

  int version();

  long processDefinitionKey();

  String elementId();
}
