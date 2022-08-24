package io.camunda.connector.inbound.importer;

public record ProcessMetadata(String bpmnProcessId, Long processDefinitionKey, String resourceName,
                              int version) {

}